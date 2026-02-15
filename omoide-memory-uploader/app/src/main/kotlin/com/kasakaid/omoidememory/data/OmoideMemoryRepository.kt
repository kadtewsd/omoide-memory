package com.kasakaid.omoidememory.data

import android.content.Context
import arrow.core.None
import arrow.core.some
import com.kasakaid.omoidememory.extension.HashGenerator.calculateFileHash
import com.kasakaid.omoidememory.os.FolderUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmoideMemoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val omoideMemoryDao: OmoideMemoryDao,
    private val localFileRepository: LocalFileRepository,
) {

    /**
     * すでにアップロードされたコンテンツの数を取得
     */
    fun getUploadedCount(): Flow<Int> = omoideMemoryDao.getUploadedCount()
    /**
     * Hash 値で厳密にアップロードされていないファイルを算出する。
     * アップロード時の厳密なハッシュ計算を想定
     */
    suspend fun getActualPendingFiles(): List<OmoideMemory> {
        // 1. 最初の一回だけ DB から全ハッシュをロードして Set にする
        val uploadedHashSet = omoideMemoryDao.getAllUploadedHashes().toSet()
        return localFileRepository.getPendingFiles { file ->
            // Optimized approach:
            // 1. Calculate Hash
            // 2. Check if Hash exists in DB
            // Calculating hash for gigabytes of video is slow.
            // Strategy:
            // We will calculate hash. If it's too slow, we might need a partial hash or rely on size+name+date, but requirement says "File content base recommended".
            // We'll stick to full hash for reliability as requested, but be aware of performance on large videos.
            val hash = context.calculateFileHash(
                file.getContentUri(FolderUri.content)
            )
            hash.fold(
                onFailure = { None },
                onSuccess = { hashValue ->
                    if (!uploadedHashSet.contains(hashValue)) { // DBになければ Pending
                        OmoideMemory.of(hash = hashValue, localFile = file).some()
                    } else {
                        None
                    }
                }
            )
        }
    }


    suspend fun markAsUploaded(entity: OmoideMemory) {
        omoideMemoryDao.insertUploadedFile(entity)
    }
}

