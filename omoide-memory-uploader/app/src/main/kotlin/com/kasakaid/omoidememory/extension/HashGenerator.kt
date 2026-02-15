package com.kasakaid.omoidememory.extension

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/**
 *
    Calculate Hash
    Note: Calculating hash for every file on every scan is expensive.
    We optimize by checking if already in DB first?
    BUT, requirements say "File Name Collision" is possible, so we rely on content hash.
    However, we can trust that if we uploaded it, we stored the hash.
    So we can compute hash and THEN check.
    To avoid reading huge files unnecessarily, maybe we should first check if *path* was uploaded associated with a modification time?
    But the requirement emphasizes "Unique ID per file (e.g. hash)".
    Streaming hash calculation
 */
object HashGenerator {

    fun Context.calculateFileHash(uri: Uri): Result<String> {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            Result.success(bytesToHex(digest.digest()))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}