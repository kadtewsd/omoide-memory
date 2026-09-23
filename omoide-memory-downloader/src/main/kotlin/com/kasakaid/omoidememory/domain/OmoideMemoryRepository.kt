package com.kasakaid.omoidememory.domain

import com.kasakaid.omoidememory.commentimport.domain.model.FileName

/**
 * コンテンツの永続化レイヤー
 */
interface OmoideMemoryRepository {
    suspend fun save(memory: OmoideMemory): OmoideMemory

    suspend fun existsPhotoByFileName(fileName: FileName): Boolean

    suspend fun existsVideoByFileName(fileName: FileName): Boolean

    suspend fun findByFileName(fileName: FileName): OmoideMemory?

    suspend fun findByLikeFileName(fileName: FileName): OmoideMemory?

    suspend fun deleteByFileName(fileName: FileName)
}
