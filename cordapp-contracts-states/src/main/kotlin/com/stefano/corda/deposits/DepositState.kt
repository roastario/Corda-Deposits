package com.stefano.corda.deposits

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.reflect.KProperty

data class DepositState(val depositAmount: Amount<Currency>,

                        val landlord: Party,
                        val tenant: Party,
                        val issuer: Party,
                        val propertyId: String,
                        val inventory: SecureHash,

                        val amountDeposited: Amount<Currency>? = null,
                        val amountPaidToLandlord: Amount<Currency>? = null,
                        val amountPaidToTenant: Amount<Currency>? = null,
                        val landlordDeductions: List<Deduction>? = null,
                        val tenantDeductions: List<Deduction>? = null,
                        val acceptedDeductions: List<Deduction>? = null,
                        val bounces: Int = 0,
                        val refundRequestedAt: Instant? = null,
                        val refundedAt: Instant? = null,

                        override val linearId: UniqueIdentifier = UniqueIdentifier(propertyId)) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(tenant, landlord, issuer);
}


data class Deduction(val deductionReason: String, val deductionAmount: Amount<Currency>, val picture: SecureHash);