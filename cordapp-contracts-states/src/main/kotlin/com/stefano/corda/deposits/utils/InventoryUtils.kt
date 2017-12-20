package com.stefano.corda.deposits.utils

import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun CordaRPCOps.getInventory(hash: String) : InputStream {
    return getInventory(hash, this);
}

private fun getInventory(hash: String, rpcOps: CordaRPCOps): InputStream{
    val attachment = rpcOps.openAttachment(SecureHash.parse(hash));
    val attachmentStream = ZipInputStream(attachment);
    var entry: ZipEntry? = attachmentStream.nextEntry;
    while (entry != null){
        if (entry.name == "inventory.pdf"){
            break;
        }
        entry = attachmentStream.nextEntry;
    }
    return attachmentStream
}