package com.kasakaid.pictureuploader.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmoideMemoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val omoideMemoryDao: OmoideMemoryDao
) {

    suspend fun getPendingFiles(): List<LocalFile> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<LocalFile>()
        val contentResolver = context.contentResolver

        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATA
        )

        // Select images and videos
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeColumn)
                val path = cursor.getString(dataColumn)

                // Skip if null or unknown
                if (path == null) continue

                // Calculate Hash
                // Note: Calculating hash for every file on every scan is expensive. 
                // We optimize by checking if already in DB first? 
                // BUT, requirements say "File Name Collision" is possible, so we rely on content hash.
                // However, we can trust that if we uploaded it, we stored the hash. 
                // So we can compute hash and THEN check.
                // To avoid reading huge files unnecessarily, maybe we should first check if *path* was uploaded associated with a modification time? 
                // But the requirement emphasizes "Unique ID per file (e.g. hash)".
                
                // Optimized approach:
                // 1. Calculate Hash
                // 2. Check if Hash exists in DB
                
                // Calculating hash for gigabytes of video is slow. 
                // Strategy: 
                // We will calculate hash. If it's too slow, we might need a partial hash or rely on size+name+date, but requirement says "File content base recommended".
                // We'll stick to full hash for reliability as requested, but be aware of performance on large videos.
                
                val contentUri = Uri.withAppendedPath(collection, id.toString())
                
                // Streaming hash calculation
                val hash = calculateFileHash(contentResolver, contentUri)
                
                if (hash != null) {
                    val isUploaded = omoideMemoryDao.isFileUploaded(hash)
                    if (!isUploaded) {
                        mediaList.add(LocalFile(id, name, path, contentUri, size, mimeType, hash))
                    }
                }
            }
        }
        return@withContext mediaList
    }

    private fun calculateFileHash(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getUploadedCount(): Flow<Int> = omoideMemoryDao.getUploadedCount()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    suspend fun markAsUploaded(localFile: LocalFile, driveFileId: String?) {
        val entity = OmoideMemory(
            id = localFile.hash,
            filePath = localFile.path,
            fileSize = localFile.size,
            uploadedAt = System.currentTimeMillis(),
            mimeType = localFile.mimeType,
            driveFileId = driveFileId
        )
        omoideMemoryDao.insertUploadedFile(entity)
    }
}

data class LocalFile(
    val id: Long,
    val name: String,
    val path: String,
    val uri: Uri,
    val size: Long,
    val mimeType: String,
    val hash: String
)
