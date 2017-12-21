package com.stefano.corda.deposits

import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.TransactionBuilder

// Serialization whitelist.
class DepositsSchemeWhiteList : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TransactionBuilder::class.java, Deduction::class.java)
}