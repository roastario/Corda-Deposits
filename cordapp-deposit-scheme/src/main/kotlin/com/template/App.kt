package com.template

import com.stefano.corda.deposits.api.LandlordApi
import com.stefano.corda.deposits.api.TenantApi
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

// ***********
// * Plugins *
// ***********
class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::LandlordApi), Function(::TenantApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            "landlord-scheme-hotLoad" to "../../../cordapp-deposit-scheme/src/main/resources/landlord",
            "tenant-scheme-hotLoad" to "../../../cordapp-deposit-scheme/src/main/resources/tenant",
            "js" to "../../../js"
    )
}

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)
