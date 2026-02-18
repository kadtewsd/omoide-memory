package com.kasakaid.omoidememory.data

import android.content.Context
import android.util.Log
import arrow.core.None
import arrow.core.some
import com.kasakaid.omoidememory.extension.HashGenerator.calculateFileHash
import com.kasakaid.omoidememory.os.FolderUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmoideMemoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val omoideMemoryDao: OmoideMemoryDao,
    private val localFileRepository: LocalFileRepository,
) {

    companion object {
        val TAG = "OmoideMemoryRepository"
    }

    /**
     * すでにアップロードされたコンテンツの数を取得
     */
    fun getUploadedCount(): Flow<Int> = omoideMemoryDao.getUploadedCount()

    /**
     * Hash 値で厳密にアップロードされていないファイルを算出する。
     * アップロード時の厳密なハッシュ計算を想定
     * Flow を返すことになるので、fun で定義。val にしてしまうと購読元によって皮が破壊される可能性がある！
     */
    fun getActualPendingFiles(): Flow<OmoideMemory> = flow {
        // 1. 最初の一回だけ DB から全ハッシュをロードして Set にする
        // 🚀 .first() を使うことで、その瞬間のスナップショットを 1 回だけ取得する
        // 購読（collect）された瞬間に、1回だけ最新のハッシュセットを取りに行く
        val uploadedHashSet = omoideMemoryDao.getAllUploadedHashes().toSet()
        Log.d(TAG, "アップロード済みハッシュセット ${uploadedHashSet.size}件取得")
        localFileRepository.getPendingFiles { file ->
            Log.d(TAG, "${file.name} のフィルタ開始")
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
            Log.d(TAG, "$hash を算出")
            hash.fold(
                onFailure = { None },
                onSuccess = { hashValue ->
                    if (!uploadedHashSet.contains(hashValue)) { // DBになければ Pending
                        Log.d(TAG, "${file.name} がヒット")
                        OmoideMemory.of(hash = hashValue, localFile = file).some()
                    } else {
                        None
                    }
                }
            )
        }.let {
            // collect していった内容を最終的に emit
            emitAll(it)
        }
    }.flowOn(Dispatchers.IO) // 🚀 これで誰がどこで呼んでも、重い処理はバックグラウンド！

    suspend fun markAsUploaded(entity: OmoideMemory) {
        omoideMemoryDao.insertUploadedFile(entity)
    }
}

