package com.stefano.corda.deposits.flow

import co.paralleluniverse.fibers.Suspendable
import com.stefano.corda.deposits.Deduction
import com.stefano.corda.deposits.DepositContract
import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.flow.FundDepositFlow.getStateAndRefByLinearId
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object TenantSuggestAcceptDeductionFlow {

    fun addDeduction(list1: List<Deduction>, item: Deduction): List<Deduction>{

        val copy = list1.toMutableList();
        copy += item;
        return copy;

    }


    @InitiatingFlow
    @StartableByRPC
    class Initiator(val depositId: UniqueIdentifier,
                    val acceptedDeductions: List<Deduction>,
                    val contestedDeductions: List<Deduction>) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object FINDING_STATE : ProgressTracker.Step("Locating unfunded deposit to fund")
            object APPLYING_DEDUCTION : ProgressTracker.Step("")
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
                    APPLYING_DEDUCTION,
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

            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            progressTracker.currentStep = FINDING_STATE
            val refAndState = serviceHub.getStateAndRefByLinearId<DepositState>(linearId = depositId);

            val landlord = refAndState.state.data.landlord;
            val tenant = refAndState.state.data.tenant;
            val depositIssuer = refAndState.state.data.issuer;

            require(tenant == ourIdentity) { "deduction acceptance or modification must be initiated by tenant" }

            val deductCommand = Command(
                    DepositContract.Commands.TenantDeduct(refAndState.state.data.propertyId),
                    listOf(landlord, tenant, depositIssuer).map { it.owningKey }
            )

            progressTracker.currentStep = APPLYING_DEDUCTION

            val copy = refAndState.state.data.copy(acceptedDeductions = acceptedDeductions, tenantDeductions = contestedDeductions)

            val txBuilder = TransactionBuilder(notary)
                    .addInputState(refAndState)
                    .addOutputState(copy, DepositContract.DEPOSIT_CONTRACT_ID)
                    .addCommand(deductCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            val tenantFlow = initiateFlow(tenant)
            val issuerFlow = initiateFlow(depositIssuer)

            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(issuerFlow, tenantFlow),
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