package com.stefano.corda.depositscheme.api;

import com.stefano.corda.deposits.utils.getCash
import net.corda.core.messaging.CordaRPCOps
import javax.ws.rs.GET
import javax.ws.rs.Path;
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("schemeOps")
class SchemeApi(val rpcOps: CordaRPCOps) {

    @GET
    @Path("balance")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash(): List<Any> {
        return rpcOps.getCash();
    }

}
