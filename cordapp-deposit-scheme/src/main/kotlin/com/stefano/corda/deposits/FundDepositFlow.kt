package com.stefano.corda.deposits

import co.paralleluniverse.fibers.Suspendable
import com.stefano.corda.deposits.DepositContract.Companion.IOU_CONTRACT_ID
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash

inline fun <reified T : ContractState> ServiceHub.getStateAndRefByLinearId(linearId: UniqueIdentifier): StateAndRef<T> {
    val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
    return vaultService.queryBy<T>(queryCriteria).states.single()
}

object FundDepositFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val depositId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object FINDING_STATE : ProgressTracker.Step("Locating unfunded deposit to fund")
            object GENERATING_CASH_MOVEMENT : ProgressTracker.Step("Verifying contract constraints.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    FINDING_STATE,
                    GENERATING_CASH_MOVEMENT,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = FINDING_STATE
            val refAndState = serviceHub.getStateAndRefByLinearId<DepositState>(linearId = depositId);
            // Generate an unsigned transaction.

            val landlord = refAndState.state.data.landlord;
            val tenant = refAndState.state.data.tenant;

            require(tenant == ourIdentity) { "Funding of a deposit must be initiated by the tenant." }

            val issueCommand = Command(
                    DepositContract.Commands.Fund(refAndState.state.data.propertyId),
                    listOf(landlord, tenant).map { it.owningKey }
            )

            val copy = refAndState.state.data.copy(amountDeposited = refAndState.state.data.depositAmount)
            val txBuilder = TransactionBuilder(notary)
                    .withItems(StateAndContract(copy, IOU_CONTRACT_ID), issueCommand)


            progressTracker.currentStep = GENERATING_CASH_MOVEMENT
            Cash.generateSpend(serviceHub, txBuilder, refAndState.state.data.depositAmount, landlord)

            // Stage 2.
            progressTracker.currentStep = FINDING_STATE
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            val landLordFlow = initiateFlow(landlord)
            val tenantFlow = initiateFlow(tenant)
            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(landLordFlow, tenantFlow),
                    GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatingFlow
    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(counterpartySession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                }
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }

}