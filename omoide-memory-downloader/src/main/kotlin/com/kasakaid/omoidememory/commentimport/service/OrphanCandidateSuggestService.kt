package com.kasakaid.omoidememory.commentimport.service

import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentRepository
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.CommentIntegrity
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.ExactlyMatched
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.MatchedFile
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.Missed
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.OrphanFile
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.OrphanFileFactory
import com.kasakaid.omoidememory.utility.CoroutineHelper.mapWithCoroutine
import kotlinx.coroutines.sync.Semaphore
import org.springframework.stereotype.Service

/**
 * CSV の 1 行分のコメント情報。
 */
data class CsvComment(
    val commentBody: String,
    val commenterAndDate: String,
)

@Service
class OrphanCandidateSuggestService(
    private val omoideCommentRepository: OmoideCommentRepository,
) {
    /**
     * 未マッチファイル名のリストに対して候補検索を行い、結果リストを返す。
     *
     * @param orphanFileNames  未マッチファイル名（重複除去・空行除去済み）
     */
    suspend fun suggest(orphanFileNames: List<String>): List<CommentIntegrity> {
        return orphanFileNames.mapWithCoroutine(Semaphore(50)) { rawFileName ->
            val result =
                omoideCommentRepository.findByFileName(
                    rawFileName,
                )
            if (result.isNotEmpty()) {
                return@mapWithCoroutine ExactlyMatched(
                    fileName = rawFileName,
                    mediaType = result.first().mediaType,
                )
            }
            val orphanFiles =
                OrphanFileFactory.create(
                    rawFileName = rawFileName,
                    mediaType = result.first().mediaType,
                )
            searchByOrphan(orphanFiles)
        }
    }

    private suspend fun searchByOrphan(orphanFile: OrphanFile): CommentIntegrity {
        val hit: MatchedFile? =
            orphanFile.likePatterns.firstNotNullOfOrNull { like ->
                val result = omoideCommentRepository.findByFileNameLike(like)
                if (result.isNotEmpty()) {
                    MatchedFile(
                        fileName = like,
                        hitPattern = result.first().fileName,
                        mediaType = result.first().mediaType,
                    )
                } else {
                    null
                }
            }
        return hit ?: Missed(
            fileName = orphanFile.orphanFileName,
            mediaType = orphanFile.mediaType,
        )
    }
}
