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
import java.time.Duration
import java.time.Instant

object TenantSendDepositBackToTLandlordDeductionsFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val depositId: UniqueIdentifier,
                    val deductionsFromTenant: List<Deduction>) : FlowLogic<SignedTransaction>() {

        override val progressTracker = tracker()


        companion object {
            object FINDING_STATE : ProgressTracker.Step("Locating State")
            object UPDATING_STATE : ProgressTracker.Step("Updating deposit handover time")
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

            require(tenant == ourIdentity) { "sending back of a deposit must be initiated by the tenant." }

            val requestRefundCommand = Command(
                    DepositContract.Commands.SendBackToLandlord(refAndState.state.data.propertyId),
                    listOf(landlord, tenant).map { it.owningKey }
            )

            progressTracker.currentStep = UPDATING_STATE;

            val splitDeductions = splitDeductions(refAndState.state.data.landlordDeductions as List<Deduction>, deductionsFromTenant)
            val copy = refAndState.state.data.copy(sentBackToLandlordAt = Instant.now(), contestedDeductions = splitDeductions.contested, tenantDeductions = deductionsFromTenant)

            val txBuilder = TransactionBuilder(notary)
                    .addInputState(refAndState)
                    .addOutputState(copy, DepositContract.DEPOSIT_CONTRACT_ID)
                    .addCommand(requestRefundCommand)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
            val landlordFlow = initiateFlow(landlord)
            progressTracker.currentStep = GATHERING_SIGS
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(landlordFlow),
                    GATHERING_SIGS.childProgressTracker()))
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
                    //all checks delegated to contract
                }
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id);
        }

    }


    private fun splitDeductions(landLordDeductions: List<Deduction>,
                                tenantDeductions: List<Deduction>): SplitDeductions {
        val contested: MutableList<Deduction> = ArrayList();
        val accepted: MutableList<Deduction> = ArrayList();
        val indexedLandlordDeductions = landLordDeductions.map { it.deductionId to it }.toMap();
        tenantDeductions.forEach { it ->
            if (it.equals(indexedLandlordDeductions.get(it.deductionId))) {
                accepted += it
            } else {
                contested += it
            }
        }
        return SplitDeductions(accepted, contested);
    }

    data class SplitDeductions(val accepted: List<Deduction>, val contested: List<Deduction>)


}