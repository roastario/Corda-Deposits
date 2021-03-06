package com.stefano.corda.bank

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import java.util.*


@StartableByRPC
@InitiatingFlow
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {

    override val progressTracker = tracker()

    companion object {
        object PREPARING : ProgressTracker.Step("Gathering the required inputs.")
        object ISSUING : ProgressTracker.Step("Issuing cash.")
        object RETURNING : ProgressTracker.Step("Returning the newly-issued cash state.")

        fun tracker() = ProgressTracker(PREPARING, ISSUING, RETURNING)
    }

    @Suspendable
    override fun call(): Cash.State {
        progressTracker.currentStep = PREPARING
        val issueRef = OpaqueBytes.of(Random().nextInt().toByte())
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        progressTracker.currentStep = ISSUING
        val cashIssueSubflowResult = subFlow(CashIssueFlow(amount, issueRef, notary))

        progressTracker.currentStep = RETURNING
        val cashIssueTx = cashIssueSubflowResult.stx.toLedgerTransaction(serviceHub)
        return cashIssueTx.outputsOfType<Cash.State>().single()
    }
}