package com.r3.stefano.deposits.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant
import java.util.*

data class DepositState(val depositId: String,
                        val depositAmount: Amount<Currency>,
                        val tenant: Party,
                        val landlord: Party,
                        val refunded: Boolean = false,
                        val refundedTimeStamp: Instant? = null,
                        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants: List<AbstractParty> get() = listOf()
}