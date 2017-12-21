package com.stefano.corda.deposits.flow

import co.paralleluniverse.fibers.Suspendable
import com.stefano.corda.deposits.DepositState
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash



object RefundInstructionFlow{


    @InitiatingFlow
    @StartableByRPC
    class Initiator(val proposedDeposit: DepositState,
                    val partialTx: TransactionBuilder) : FlowLogic<TransactionBuilder>(){

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
            return issuerChannel.receive(receiveType = TransactionBuilder::class.java).unwrap({it});
        }
    }

    @InitiatingFlow
    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object SET_UP : ProgressTracker.Step("Initialising flow.")
            object RECEIVING_INPUT_OPTION : ProgressTracker.Step("We receive the input option from the counterparty.")
            object QUERYING_THE_ORACLE : ProgressTracker.Step("Querying oracle for the current spot price and volatility.")
            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object ADDING_CASH_PAYMENT : ProgressTracker.Step("Adding the cash to cover the premium.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object WE_SIGN : ProgressTracker.Step("signing transaction.")
            object ORACLE_SIGNS : ProgressTracker.Step("Requesting oracle signature.")
            object OTHERS_SIGN : ProgressTracker.Step("Requesting old owner's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(SET_UP, RECEIVING_INPUT_OPTION, QUERYING_THE_ORACLE, BUILDING_THE_TX,
                    ADDING_CASH_PAYMENT, VERIFYING_THE_TX, WE_SIGN, ORACLE_SIGNS, OTHERS_SIGN, FINALISING)
        }

        @Suspendable
        override fun call() {
            val proposedTransaction = counterpartySession.receive<TransactionBuilder>().unwrap { it };
            val propopsedOutputState = counterpartySession.receive<DepositState>().unwrap { it };
            Cash.Companion.generateSpend(serviceHub, proposedTransaction, propopsedOutputState.depositAmount, propopsedOutputState.tenant)
            counterpartySession.send(proposedTransaction);
        }
    }

}

