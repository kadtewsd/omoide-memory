package com.kasakaid.omoidememory.patchtool.filedirmodification.domain

interface ReorganizeMisplacedFilesRepository {
    suspend fun update(reorganizedFile: ReorganizedFile)
}
