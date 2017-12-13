package com.stefano.corda.bank

import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleAsset
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("bankOps")
class BankApi(val rpcOps: CordaRPCOps) {
    // Accessible at /api/bankOps/*.

    @GET
    @Path("issue-cash")
    fun selfIssueCash(@QueryParam(value = "amount") amount: Int,
                      @QueryParam(value = "currency") currency: String): Response {
        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
        return try {
            val flowHandle = rpcOps.startFlow(::SelfIssueCashFlow, issueAmount)
            val cashState = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.CREATED).entity(cashState.toString()).build()
        } catch (e: Exception) {
            Response.status(Response.Status.BAD_REQUEST).entity(e.message).build()
        }
    }

    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash() = rpcOps.getCashBalances2()

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami(): Map<String, CordaX500Name> {
        println(System.getProperty("user.dir"))
        return rpcOps.nodeInfo().legalIdentities.map { it.name }.associateBy { it.organisation + "::" + it.organisationUnit + "::" + it.commonName }
    }

    fun CordaRPCOps.getCashBalances2(): List<Any> {
        val sum = builder {
            CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency, CashSchemaV1.PersistentCashState::issuerRef),
                    orderBy = Sort.Direction.DESC)
        }
        val criteria = QueryCriteria.VaultCustomQueryCriteria(sum)
        val sums = this.vaultQueryBy<FungibleAsset<*>>(criteria).otherResults
        return sums;
    }

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        return mapOf(("peers" to getPeerParties().map({ it.name })))
    }

    private fun getPeerParties() = rpcOps.networkMapSnapshot().map({ it.legalIdentities.first() })

    @POST
    @Path("xfer")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun transferCashToPeer(request: TransferRequest): Map<String, String> {
        println(System.getProperty("user.dir"))
        val recipient = getPeerParties().find { party -> party.name == request.nameOfParty }
        val amountToMove = Amount(request.amount.toLong() * 100, Currency.getInstance(request.currency))
        val flowHandle = rpcOps.startFlow(::CashPaymentFlow, amountToMove, recipient!!)
        val cashState = flowHandle.returnValue.getOrThrow()
        return mapOf("xferred" to cashState.toString())
    }

    data class TransferRequest(val nameOfParty: CordaX500Name, val currency: String, val amount: Double)


}