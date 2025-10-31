package io.github.darkstarworks.trialChamberPro.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Utility class for compressing and decompressing data using Gzip.
 * Used for snapshot file compression to save disk space.
 */
object CompressionUtil {

    /**
     * Compresses data using Gzip compression.
     *
     * @param data The data to compress
     * @return Compressed byte array
     * @throws Exception if compression fails
     */
    fun compress(data: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(data)
        }
        return byteArrayOutputStream.toByteArray()
    }

    /**
     * Decompresses Gzip-compressed data.
     *
     * @param compressedData The compressed data
     * @return Decompressed byte array
     * @throws Exception if decompression fails
     */
    fun decompress(compressedData: ByteArray): ByteArray {
        val byteArrayInputStream = ByteArrayInputStream(compressedData)
        val byteArrayOutputStream = ByteArrayOutputStream()

        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            val buffer = ByteArray(1024)
            var len: Int
            while (gzipInputStream.read(buffer).also { len = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, len)
            }
        }

        return byteArrayOutputStream.toByteArray()
    }

    /**
     * Serializes an object and compresses it.
     *
     * @param obj The object to serialize and compress
     * @return Compressed serialized object
     * @throws Exception if serialization or compression fails
     */
    fun <T> compressObject(obj: T): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(byteArrayOutputStream).use { objectOutputStream ->
            objectOutputStream.writeObject(obj)
        }
        return compress(byteArrayOutputStream.toByteArray())
    }

    /**
     * Decompresses and deserializes an object.
     *
     * @param compressedData The compressed serialized data
     * @return Deserialized object
     * @throws Exception if decompression or deserialization fails
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> decompressObject(compressedData: ByteArray): T {
        val decompressed = decompress(compressedData)
        val byteArrayInputStream = ByteArrayInputStream(decompressed)
        ObjectInputStream(byteArrayInputStream).use { objectInputStream ->
            return objectInputStream.readObject() as T
        }
    }

    /**
     * Gets a human-readable size string from bytes.
     *
     * @param bytes Size in bytes
     * @return Formatted size string (e.g., "1.5 MB")
     */
    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }
}
