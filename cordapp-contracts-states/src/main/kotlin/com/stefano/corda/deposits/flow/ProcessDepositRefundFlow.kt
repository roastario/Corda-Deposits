package com.stefano.corda.deposits.flow

import co.paralleluniverse.fibers.Suspendable
import com.stefano.corda.deposits.DepositContract
import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.flow.FundDepositFlow.getStateAndRefByLinearId
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

object ProcessDepositRefundFlow {
    //this will be responsible for asking the landlord for a refund


    @InitiatingFlow
    @StartableByRPC
    class Initiator(val depositId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        override val progressTracker = tracker()


        companion object {
            object FINDING_STATE : ProgressTracker.Step("Locating unfunded deposit to fund")
            object UPDATING_STATE : ProgressTracker.Step("Verifying contract constraints.")
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
                    UPDATING_STATE,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            progressTracker.currentStep = FINDING_STATE
            val refAndState = serviceHub.getStateAndRefByLinearId<DepositState>(linearId = depositId);

            val landlord = refAndState.state.data.landlord;
            val tenant = refAndState.state.data.tenant;
            val scheme = refAndState.state.data.issuer


            val requestRefundCommand = Command(
                    DepositContract.Commands.RequestRefund(refAndState.state.data.propertyId),
                    listOf(landlord, tenant, scheme).map { it.owningKey }
            )

            progressTracker.currentStep = UPDATING_STATE;

            val copy = refAndState.state.data.copy(refundedAt = Instant.now())
            val txBuilder1 = TransactionBuilder(notary)
                    .addInputState(refAndState)
                    .addOutputState(copy, DepositContract.DEPOSIT_CONTRACT_ID)
                    .addCommand(requestRefundCommand)

            val schemeFlow = initiateFlow(scheme)
            val updatedTxBuilder = subFlow(RefundInstructionFlow.Initiator(copy, txBuilder1))

            progressTracker.currentStep = VERIFYING_TRANSACTION
            updatedTxBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(updatedTxBuilder)
            val tenantFlow = initiateFlow(tenant)

            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(tenantFlow, schemeFlow),
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