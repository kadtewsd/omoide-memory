package com.kasakaid.omoidememory.commentimport.service

import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.CommentIntegrity
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.ExactlyMatched
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.MatchedFile
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.Missed
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.OrphanFileFactory
import com.kasakaid.omoidememory.domain.OmoideMemoryRepository
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import kotlinx.coroutines.sync.Semaphore
import org.springframework.stereotype.Service

@Service
class OrphanCandidateSuggestService(
    private val omoideMemoryRepository: OmoideMemoryRepository,
) {
    /**
     * 未マッチファイル名のリストに対して候補検索を行い、結果リストを返す。
     *
     * @param orphanFileNames  未マッチファイル名（重複除去・空行除去済み）
     */
    suspend fun suggest(orphanFileNames: List<String>): List<CommentIntegrity> {
        return orphanFileNames.mapWithCoroutine(Semaphore(50)) { rawFileName ->
            val exactMatch = omoideMemoryRepository.findByFileName(rawFileName)
            if (exactMatch != null) {
                return@mapWithCoroutine ExactlyMatched(fileName = rawFileName)
            }
            val orphanFile = OrphanFileFactory.create(rawFileName = rawFileName)
            orphanFile.likePatterns.firstNotNullOfOrNull { candidate ->
                omoideMemoryRepository.findByLikeFileName(candidate)?.let { found ->
                    MatchedFile(
                        fileName = rawFileName,
                        likePattern = candidate,
                        actualFileName = found.name,
                    )
                }
            } ?: Missed(fileName = rawFileName)
        }
    }
}
