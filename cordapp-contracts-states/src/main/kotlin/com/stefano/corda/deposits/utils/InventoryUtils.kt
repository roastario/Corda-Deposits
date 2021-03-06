package com.stefano.corda.deposits.utils

import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val inventoryFileName = "inventory.pdf"


fun CordaRPCOps.getInventory(hash: String): InputStream {
    return getInventory(hash, this);
}

fun CordaRPCOps.saveInventory(data: ByteArray): SecureHash {
    return uploadInventory(data, this);
}

private fun getInventory(hash: String, rpcOps: CordaRPCOps): InputStream {
    val attachment = rpcOps.openAttachment(SecureHash.parse(hash));
    val attachmentStream = ZipInputStream(attachment);
    var entry: ZipEntry? = attachmentStream.nextEntry;
    while (entry != null) {
        if (entry.name == inventoryFileName) {
            break;
        }
        entry = attachmentStream.nextEntry;
    }
    return attachmentStream
}

fun uploadInventory(inventory: ByteArray, rpcOps: CordaRPCOps): SecureHash {
    val inputStream = ByteArrayInputStream(inventory);
    val outputStream = ByteArrayOutputStream()
    try {
        val zippedOutputStream = ZipOutputStream(outputStream);
        zippedOutputStream.putNextEntry(ZipEntry(inventoryFileName));
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