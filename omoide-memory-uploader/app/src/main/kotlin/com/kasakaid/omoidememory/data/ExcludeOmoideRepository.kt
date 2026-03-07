package com.kasakaid.omoidememory.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcludeOmoideRepository
    @Inject
    constructor(
        private val omoideMemoryDao: OmoideMemoryDao,
    ) {
        suspend fun revive(omoideIds: List<Long>) {
            omoideMemoryDao.delete(omoideIds)
        }

        suspend fun findBy(): List<ExcludeOmoide> =
            omoideMemoryDao.findBy(UploadState.EXCLUDED).map {
                ExcludeOmoide(it.id)
            }
    }
