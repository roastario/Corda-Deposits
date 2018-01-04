package com.stefano.corda.deposits.utils

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashException
import net.corda.finance.schemas.CashSchemaV1
import java.util.*


fun CordaRPCOps.getCash(): List<Any> {
    val sum = builder {
        CashSchemaV1.PersistentCashState::pennies.sum(
                groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency, CashSchemaV1.PersistentCashState::issuerRef),
                orderBy = Sort.Direction.DESC)
    }
    val criteria = QueryCriteria.VaultCustomQueryCriteria(sum)
    val sums = this.vaultQueryBy<FungibleAsset<*>>(criteria).otherResults
    return sums;
}

fun Cash.Companion.generateSpendAvoidingDuplicateMoves(serviceHub: ServiceHub,
                                                       builder: TransactionBuilder,
                                                       amount: Amount<Currency>,
                                                       to: AbstractParty,
                                                       onlyFromParties: Set<AbstractParty> = emptySet()): TransactionBuilder{

    val tmpBuilder = TransactionBuilder(builder.notary!!)
    val (_, anonymisedSpendOwnerKeys) = try {
        generateSpend(serviceHub, tmpBuilder, amount, to, onlyFromParties)
    } catch (e: InsufficientBalanceException) {
        throw CashException("Insufficient cash for spend: ${e.message}", e)
    }
    val resultBuilder = TransactionBuilder(builder.notary!!)
    builder.copyTo(resultBuilder, serviceHub, filterCommands = { it.value !is Cash.Commands.Move })
    tmpBuilder.copyTo(resultBuilder, serviceHub, filterCommands = { it.value !is Cash.Commands.Move })
    resultBuilder.addCommand(Cash.Commands.Move(),
            builder.commands().filter { it.value is Cash.Commands.Move }.flatMap { it.signers } + tmpBuilder.commands().filter { it.value is Cash.Commands.Move }.flatMap { it.signers })
    return resultBuilder
}

// TODO remove after solving tmpBuilder need - or perhaps port this into Corda's TransactionBuilder
fun TransactionBuilder.copyTo(
        other: TransactionBuilder,
        serviceHub: ServiceHub,
        filterInputStates: (input: StateAndRef<ContractState>) -> Boolean = { true },
        filterOutputStates: (output: TransactionState<ContractState>) -> Boolean = { true },
        filterCommands: (command: Command<*>) -> Boolean = { true },
        filterAttachments: (attachment: SecureHash) -> Boolean = { true }
) {
    inputStates().map { serviceHub.toStateAndRef<ContractState>(it) }.filter(filterInputStates).forEach { other.addInputState(it) }
    outputStates().filter(filterOutputStates).map { other.addOutputState(it) }
    commands().filter(filterCommands).forEach { other.addCommand(it) }
    attachments().filter(filterAttachments).forEach { other.addAttachment(it) }
}