package com.stefano.corda.deposits.flow

import co.paralleluniverse.fibers.Suspendable
import com.stefano.corda.deposits.DepositState
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
            object FINDING_STATE : ProgressTracker.Step("Locating unfunded deposit to fund")
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
            object SENDING_TX : ProgressTracker.Step("Sending completed transaction proposal")
            object DONE : ProgressTracker.Step("Finished")

            fun tracker() = ProgressTracker(
                    RECEIVING_TX,
                    RECEIVING_REFUND_INFO,

                    ADDING_TENANT_CASH_PAYMENT,
                    ADDING_LANDLORD_CASH_PAYMENT,

                    SENDING_TX,
                    DONE
            )
        }

        @Suspendable
        override fun call() {
            progressTracker.currentStep = RECEIVING_TX;
            val proposedTransaction = counterpartySession.receive<TransactionBuilder>().unwrap { it };
            progressTracker.currentStep = RECEIVING_REFUND_INFO
            val propopsedOutputState = counterpartySession.receive<DepositState>().unwrap { it };
            progressTracker.currentStep = ADDING_TENANT_CASH_PAYMENT
            Cash.Companion.generateSpend(serviceHub, proposedTransaction, propopsedOutputState.depositAmount, propopsedOutputState.tenant)
            progressTracker.currentStep = SENDING_TX
            counterpartySession.send(proposedTransaction);
            progressTracker.currentStep = DONE;
        }
    }

}

