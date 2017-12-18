package com.stefano.corda.deposits.api

import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.FundDepositFlow
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        return Response.status(Response.Status.CREATED).entity(result).build();
    }


    @GET
    @Path("inventory")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getInventory(@QueryParam("attachmentId") attachmentId: String):Response {
        val attachment = rpcOps.openAttachment(SecureHash.parse(attachmentId));
        val attachmentStream = ZipInputStream(attachment);
        var entry: ZipEntry? = attachmentStream.nextEntry;

        while (entry != null){
            if (entry.name == "inventory.pdf"){
                break;
            }
            entry = attachmentStream.nextEntry;
        }

        return Response.ok(attachmentStream).build();
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

}