package com.stefano.corda.deposits.api

import com.stefano.corda.deposits.DepositIssueFlow
import com.stefano.corda.deposits.DepositState
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("depositOps")
class DepositApi(val rpcOps: CordaRPCOps) {

    val myIdentities = rpcOps.nodeInfo().legalIdentities.map { it.name };


    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami(): Map<String, CordaX500Name> {
        return rpcOps.nodeInfo().legalIdentities.map { it.name }.associateBy { it.organisation + "::" + it.organisationUnit + "::" + it.commonName }
    }

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        return mapOf(("peers" to getPeerParties().map({ it.name })))
    }

    private fun getPeerParties() = rpcOps.networkMapSnapshot().map({ it.legalIdentities.first() })

    @POST
    @Path("createDeposit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun issueUnfunded(request: DepositRequest): Response {
        val landlord = rpcOps.wellKnownPartyFromX500Name(request.landlordX500Name) ?: throw IllegalArgumentException("Unknown landlord.")
        val tenant = rpcOps.wellKnownPartyFromX500Name(request.tenantX500Name) ?: throw IllegalArgumentException("Unknown tenant.")
        val depositScheme = rpcOps.nodeInfo().legalIdentities.first();
        val depositState = DepositState(Amount(request.amount.toLong() * 100, Currency.getInstance("GBP")),
                Amount(0, Currency.getInstance("GBP")),
                Amount(0, Currency.getInstance("GBP")),
                Amount(0, Currency.getInstance("GBP")),
                listOf(),
                landlord,
                tenant,
                depositScheme,
                request.propertyId
        )
        val flowHandle = rpcOps.startFlow(DepositIssueFlow::Initiator, depositState);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.CREATED).entity(result).build();
    }


    @GET
    @Path("deposits")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits() = rpcOps.vaultQueryBy<DepositState>().states;

    data class DepositRequest(val landlordX500Name: CordaX500Name,
                              val tenantX500Name: CordaX500Name,
                              val amount: Double,
                              val propertyId: String);

}