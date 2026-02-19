package com.kasakaid.omoidememory.domain

/**
 * コンテンツの永続化レイヤー
 */
interface OmoideMemoryRepository {
    suspend fun save(memory: OmoideMemory): OmoideMemory
    suspend fun existsPhotoByFileName(fileName: String): Boolean
    suspend fun existsVideoByFileName(fileName: String): Boolean
}