package com.stefano.corda.depositscheme.api;

import com.stefano.corda.deposits.ArbitratorDeduction
import com.stefano.corda.deposits.DepositState
import com.stefano.corda.deposits.flow.ArbitrateAndRefundFlow
import com.stefano.corda.deposits.utils.getCash
import com.stefano.corda.deposits.utils.getImage
import com.stefano.corda.deposits.utils.getInventory
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("schemeOps")
class SchemeApi(val rpcOps: CordaRPCOps) {

    @GET
    @Path("balance")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash(): List<Any> {
        return rpcOps.getCash();
    }

    @GET
    @Path("deposits")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits() = rpcOps.vaultQueryBy<DepositState>()
            .states.map { it.state.data }
            .filter { it.sentToArbiter !== null }
            .filter { it.refundedAt === null }

    @POST
    @Path("arbitrate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun arbitrateDeposit(request: ArbitratorRequest): Response {
        val arbitratorDeductions = request.deductions.map { it ->
            ArbitratorDeduction(
                    it.deductionId,
                    it.comment,
                    Amount(it.amount * 100, Currency.getInstance("GBP"))
            )
        }
        val flowHandle = rpcOps.startFlow(ArbitrateAndRefundFlow::Initiator, request.depositId, arbitratorDeductions);
        val result = flowHandle.returnValue.getOrThrow();
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @GET
    @Path("deductionImage")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getImage(@QueryParam("imageId") attachmentId: String): Response {
        return Response.ok(rpcOps.getImage(attachmentId)).build();
    }

    @GET
    @Path("inventory")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getInventory(@QueryParam("attachmentId") attachmentId: String): Response {
        return Response.ok(rpcOps.getInventory(attachmentId)).build();
    }

    data class ArbitratorRequest(val depositId: UniqueIdentifier,
                                 val deductions: List<ArbitratorDeductionView>)

    data class ArbitratorDeductionView(val comment: String,
                                       val amount: Long,
                                       val deductionId: UniqueIdentifier)


}
