package com.stefano.corda.landlord.api

import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.flow.DeductionFlow
import com.stefano.corda.deposits.flow.DepositIssueFlow
import com.stefano.corda.deposits.flow.ProcessDepositRefundFlow
import com.stefano.corda.deposits.utils.getInventory
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("depositOps")
class LandlordApi(val rpcOps: CordaRPCOps) {



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
        val scheme = rpcOps.wellKnownPartyFromX500Name(request.schemeX500Name) ?: throw IllegalArgumentException("Unknown scheme.")
        val tenant = rpcOps.wellKnownPartyFromX500Name(request.tenantX500Name) ?: throw IllegalArgumentException("Unknown tenant.")
        val landlord = rpcOps.nodeInfo().legalIdentities.first();
        val inventoryHash = uploadInventory(request.inventory);
        val depositState = DepositState(Amount(request.amount.toLong() * 100, Currency.getInstance("GBP")),
                null,
                null,
                null,
                null,
                landlord,
                tenant,
                scheme,
                request.propertyId,
                null,
                null,
                inventoryHash
        )
        val flowHandle = rpcOps.startFlow(DepositIssueFlow::Initiator, depositState);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    @POST
    @Path("refund")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun refund(linearId: UniqueIdentifier): Response{
        val flowHandle = rpcOps.startFlow(ProcessDepositRefundFlow::Initiator, linearId);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.OK).entity(result).build();

    }

    @POST
    @Path("deduct")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun deduct(deductionRequest: DeductionRequest): Response{
        val imageHash = uploadImage(deductionRequest.picture)
        val flowHandle = rpcOps.startFlow(DeductionFlow::Initiator,
                deductionRequest.depositId,
                deductionRequest.deductionReason,
                Amount(deductionRequest.deductionAmount, Currency.getInstance("GBP")),
                imageHash);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.OK).entity(result).build();
    }


    fun uploadInventory(inventory: ByteArray): SecureHash {
        val inputStream = ByteArrayInputStream(inventory);
        val outputStream = ByteArrayOutputStream()
        try {
            val zippedOutputStream = ZipOutputStream(outputStream);
            zippedOutputStream.putNextEntry(ZipEntry("inventory.pdf"));
            inputStream.copyTo(zippedOutputStream);
            zippedOutputStream.closeEntry();
            zippedOutputStream.flush();
            zippedOutputStream.close();
            return rpcOps.uploadAttachment(ByteArrayInputStream(outputStream.toByteArray()));
        } finally {
            inputStream.close();
            outputStream.close();
        }
    }


    fun uploadImage(inventory: ByteArray): SecureHash {
        val inputStream = ByteArrayInputStream(inventory);
        val outputStream = ByteArrayOutputStream()
        try {
            val zippedOutputStream = ZipOutputStream(outputStream);
            zippedOutputStream.putNextEntry(ZipEntry("image"));
            inputStream.copyTo(zippedOutputStream);
            zippedOutputStream.closeEntry();
            zippedOutputStream.flush();
            zippedOutputStream.close();
            return rpcOps.uploadAttachment(ByteArrayInputStream(outputStream.toByteArray()));
        } finally {
            inputStream.close();
            outputStream.close();
        }
    }


    @GET
    @Path("deposits")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits() = rpcOps.vaultQueryBy<DepositState>().states;


    @GET
    @Path("inventory")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getInventory(@QueryParam("attachmentId") attachmentId: String):Response {
        return Response.ok(rpcOps.getInventory(attachmentId)).build();
    }

    data class DepositRequest(val schemeX500Name: CordaX500Name,
                              val tenantX500Name: CordaX500Name,
                              val amount: Double,
                              val propertyId: String,
                              val inventory: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DepositRequest

            if (schemeX500Name != other.schemeX500Name) return false
            if (tenantX500Name != other.tenantX500Name) return false
            if (amount != other.amount) return false
            if (propertyId != other.propertyId) return false
            if (!Arrays.equals(inventory, other.inventory)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = schemeX500Name.hashCode()
            result = 31 * result + tenantX500Name.hashCode()
            result = 31 * result + amount.hashCode()
            result = 31 * result + propertyId.hashCode()
            result = 31 * result + Arrays.hashCode(inventory)
            return result
        }
    };


    data class DeductionRequest(val depositId: UniqueIdentifier, val deductionReason: String, val deductionAmount: Long, val picture: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DeductionRequest

            if (deductionReason != other.deductionReason) return false
            if (deductionAmount != other.deductionAmount) return false
            if (!Arrays.equals(picture, other.picture)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deductionReason.hashCode()
            result = 31 * result + deductionAmount.hashCode()
            result = 31 * result + Arrays.hashCode(picture)
            return result
        }
    }
}
