package com.stefano.corda.landlord.config

import com.stefano.corda.landlord.api.LandlordApi
import net.corda.core.messaging.CordaRPCOps
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

// ***********
// * Plugins *
// ***********
class LandlordWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::LandlordApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            "landlord-scheme-hotLoad" to "../../../cordapp-landlord/src/main/resources/landlord",
            "js" to "../../../js"
    )
}