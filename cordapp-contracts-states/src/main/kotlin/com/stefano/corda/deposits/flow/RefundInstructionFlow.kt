package com.stefano.corda.deposits.flow

import co.paralleluniverse.fibers.Suspendable
import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.utils.generateSpendAvoidingDuplicateMoves
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash


object RefundInstructionFlow {


    @InitiatingFlow
    @StartableByRPC
    class Initiator(val proposedDeposit: DepositState,
                    val partialTx: TransactionBuilder) : FlowLogic<TransactionBuilder>() {

        override val progressTracker = tracker()

        companion object {
            object FINDING_STATE : ProgressTracker.Step("Locating deposit")
            object UPDATING_STATE : ProgressTracker.Step("Verifying contract constraints.")
            object REQUESTING_CASH_MOVEMENT : ProgressTracker.Step("Asking for cash");
            object VERIFYING_CASH_MOVEMENT : ProgressTracker.Step("Checking Cash");


            fun tracker() = ProgressTracker(
                    FINDING_STATE,
                    UPDATING_STATE,
                    REQUESTING_CASH_MOVEMENT,
                    VERIFYING_CASH_MOVEMENT
            )
        }

        @Suspendable
        override fun call(): TransactionBuilder {
            progressTracker.currentStep = FINDING_STATE
            progressTracker.currentStep = UPDATING_STATE;
            progressTracker.currentStep = REQUESTING_CASH_MOVEMENT;
            val issuerChannel = initiateFlow(proposedDeposit.issuer)
            issuerChannel.send(partialTx);
            issuerChannel.send(proposedDeposit);
            progressTracker.currentStep = VERIFYING_CASH_MOVEMENT

            subFlow(IdentitySyncFlow.Receive(issuerChannel))
            subFlow(ReceiveStateAndRefFlow<FungibleAsset<*>>(issuerChannel))

            return issuerChannel.receive(receiveType = TransactionBuilder::class.java).unwrap({ it });
        }
    }

    @InitiatingFlow
    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {

        override val progressTracker: ProgressTracker = tracker()


        companion object {
            object RECEIVING_TX : ProgressTracker.Step("Receiving transaction proposal")
            object RECEIVING_REFUND_INFO : ProgressTracker.Step("Receiving refund data")
            object ADDING_TENANT_CASH_PAYMENT : ProgressTracker.Step("Moving cash to tenant")
            object ADDING_LANDLORD_CASH_PAYMENT : ProgressTracker.Step("Moving cash to landlord")
            object SYNCING_IDENTITIES : ProgressTracker.Step("Sending anonymous party information bck to initiator")
            object SYNCING_TRANSACTIONS : ProgressTracker.Step("Sending cash transaction information back to initiator")
            object SENDING_TX : ProgressTracker.Step("Sending completed transaction proposal")
            object DONE : ProgressTracker.Step("Finished")

            fun tracker() = ProgressTracker(
                    RECEIVING_TX,
                    RECEIVING_REFUND_INFO,

                    ADDING_TENANT_CASH_PAYMENT,
                    ADDING_LANDLORD_CASH_PAYMENT,
                    SYNCING_IDENTITIES,
                    SYNCING_TRANSACTIONS,
                    SENDING_TX,
                    DONE
            )
        }

        @Suspendable
        override fun call() {
            progressTracker.currentStep = RECEIVING_TX;
            var proposedTransaction = counterpartySession.receive<TransactionBuilder>().unwrap { it };
            progressTracker.currentStep = RECEIVING_REFUND_INFO
            val propopsedOutputState = counterpartySession.receive<DepositState>().unwrap { it };
            val deductionsTotal = (propopsedOutputState.landlordDeductions?.sumByLong { it.deductionAmount.quantity })
            var tenantAmount = propopsedOutputState.depositAmount;
            deductionsTotal?.let { toSubstract ->
                tenantAmount = tenantAmount.minus(Amount(toSubstract, propopsedOutputState.depositAmount.token))
            }
            progressTracker.currentStep = ADDING_TENANT_CASH_PAYMENT
            if (tenantAmount.compareTo(Amount(0, propopsedOutputState.depositAmount.token)) > 0) {
                println("I AM GOING TO REFUND: $tenantAmount TO TENANT ")
                proposedTransaction = Cash.Companion.generateSpendAvoidingDuplicateMoves(serviceHub, proposedTransaction,
                        tenantAmount, propopsedOutputState.tenant)
            }
            deductionsTotal?.let {
                progressTracker.currentStep = ADDING_LANDLORD_CASH_PAYMENT
                val landLordAmount = propopsedOutputState.depositAmount.minus(tenantAmount)
                println("I AM GOING TO REFUND: $landLordAmount TO LANDLORD ")
                proposedTransaction = Cash.Companion.generateSpendAvoidingDuplicateMoves(serviceHub, proposedTransaction, landLordAmount, propopsedOutputState.landlord)
            }

            progressTracker.currentStep = SYNCING_IDENTITIES
            // Sync up confidential identities in the transaction
            subFlow(IdentitySyncFlow.Send(counterpartySession, proposedTransaction.toWireTransaction(serviceHub)))

            progressTracker.currentStep = SYNCING_TRANSACTIONS
            subFlow(SendStateAndRefFlow(counterpartySession, proposedTransaction.inputStates()
                    .map { serviceHub.toStateAndRef<ContractState>(it) }.filter { it.state.data is FungibleAsset<*> }))

            progressTracker.currentStep = SENDING_TX
            counterpartySession.send(proposedTransaction);

            progressTracker.currentStep = DONE;
        }
    }

}

inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long): Long {
    var sum: Long = 0
    for (element in this) {
        sum += selector(element)
    }
    return sum
}



