package com.stefano.corda.landlord.api

import com.stefano.corda.deposits.Deduction
import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.flow.FundDepositFlow
import com.stefano.corda.deposits.flow.ProcessDepositRefundFlow
import com.stefano.corda.deposits.flow.RequestDepositRefundFlow
import com.stefano.corda.deposits.flow.TenantSuggestAcceptDeductionFlow
import com.stefano.corda.deposits.utils.getImage
import com.stefano.corda.deposits.utils.getInventory
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.schemas.CashSchemaV1
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("tenantOps")
class TenantApi(val rpcOps: CordaRPCOps) {

    val myIdentities = rpcOps.nodeInfo().legalIdentities.first().name;


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
    @Path("fundDeposit")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun fundDeposit(linearId: UniqueIdentifier): Response {
        val flowHandle = rpcOps.startFlow(FundDepositFlow::Initiator, linearId);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.OK).entity(result).build();
    }


    @GET
    @Path("inventory")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getInventory(@QueryParam("attachmentId") attachmentId: String):Response {
        return Response.ok(rpcOps.getInventory(attachmentId)).build();
    }

    @GET
    @Path("balance")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash() : List<Any>{
        val sum = builder {
            CashSchemaV1.PersistentCashState::pennies.sum(
                    groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency, CashSchemaV1.PersistentCashState::issuerRef),
                    orderBy = Sort.Direction.DESC)
        }
        val criteria = QueryCriteria.VaultCustomQueryCriteria(sum)
        val sums = rpcOps.vaultQueryBy<FungibleAsset<*>>(criteria).otherResults
        return sums;
    }


    @GET
    @Path("mydeposits")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits(): List<StateAndRef<DepositState>> {
        return rpcOps.vaultQueryBy<DepositState>(QueryCriteria.LinearStateQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.filter { it.state.data.tenant.name == myIdentities };
    }


    @POST
    @Path("refund")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun requestRefund(linearId: UniqueIdentifier): Response {
        val flowHandle = rpcOps.startFlow(RequestDepositRefundFlow::Initiator, linearId);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.OK).entity(result).build();
    }


    @POST
    @Path("deductions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    fun deductions(deductions: Deductions): Response {
        val flowHandle = rpcOps.startFlow(TenantSuggestAcceptDeductionFlow::Initiator, deductions.forDeposit, deductions.accepted, deductions.contested);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @GET
    @Path("deductionImage")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getImage(@QueryParam("imageId") attachmentId: String):Response {
        return Response.ok(rpcOps.getImage(attachmentId)).build();
    }

    data class Deductions(val forDeposit: UniqueIdentifier, val accepted: List<Deduction>, val contested: List<Deduction>)

}