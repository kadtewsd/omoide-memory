package com.kasakaid.omoidememory.domain

interface OmoideMemoryRepository {
    suspend fun save(memory: OmoideMemory): OmoideMemory
    suspend fun existsPhotoByFileName(fileName: String): Boolean
    suspend fun existsVideoByFileName(fileName: String): Boolean
}