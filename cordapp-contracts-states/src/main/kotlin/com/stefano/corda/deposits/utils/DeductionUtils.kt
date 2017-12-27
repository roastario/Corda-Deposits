package com.stefano.corda.deposits.utils

import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


private val imageName = "image"


fun CordaRPCOps.getImage(hash: String): InputStream {
    return getInventory(hash, this);
}

fun CordaRPCOps.saveImage(data: ByteArray): SecureHash {
    return uploadImage(data, this);
}

private fun getInventory(hash: String, rpcOps: CordaRPCOps): InputStream {
    val attachment = rpcOps.openAttachment(SecureHash.parse(hash));
    val attachmentStream = ZipInputStream(attachment);
    var entry: ZipEntry? = attachmentStream.nextEntry;
    while (entry != null) {
        if (entry.name == imageName) {
            break;
        }
        entry = attachmentStream.nextEntry;
    }
    return attachmentStream
}

fun uploadImage(inventory: ByteArray, rcpOps: CordaRPCOps): SecureHash {
    val inputStream = ByteArrayInputStream(inventory);
    val outputStream = ByteArrayOutputStream()
    try {
        val zippedOutputStream = ZipOutputStream(outputStream);
        zippedOutputStream.putNextEntry(ZipEntry(imageName));
        inputStream.copyTo(zippedOutputStream);
        zippedOutputStream.closeEntry();
        zippedOutputStream.flush();
        zippedOutputStream.close();
        return rcpOps.uploadAttachment(ByteArrayInputStream(outputStream.toByteArray()));
    } finally {
        inputStream.close();
        outputStream.close();
    }
}