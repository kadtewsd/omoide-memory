package com.kasakaid.omoidememory.worker

import com.kasakaid.omoidememory.data.OmoideMemory
import com.kasakaid.omoidememory.data.OmoideMemoryRepository
import com.kasakaid.omoidememory.ui.fileselection.SelectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileCleaner
    @Inject
    constructor(
        private val localFileRepository: OmoideMemoryRepository,
    ) {
        /**
         * リスト内のファイルがローカルストレージに存在するかチェックし、
         * 存在しないファイルをDBから削除（指定時のみ）し、存在するファイルのみのリストを返します。
         *
         * @return ローカルに存在するファイルのリスト
         */
        suspend fun cleanUpAndFilter(
            files: List<OmoideMemory>,
            currentMode: SelectionMode,
        ): List<OmoideMemory> =
            when (currentMode) {
                SelectionMode.TARGET -> cleanUp(files)
                SelectionMode.EXCLUDED, SelectionMode.DONE -> emptyList()
            }

        private suspend fun cleanUp(files: List<OmoideMemory>): List<OmoideMemory> =
            withContext(Dispatchers.IO) {
                val (existing, missing) =
                    files.partition { file ->
                        file.filePath?.let { File(it).exists() } == true
                    }
                if (missing.isNotEmpty()) {
                    localFileRepository.delete(missing.map { it.id })
                }
                existing
            }
    }
