package com.kasakaid.omoidememory.patchtool.filedirmodification.domain

import java.time.OffsetDateTime

interface ReorganizeMisplacedFilesRepository {
    suspend fun update(reorganizedFile: ReorganizedFile)
}
