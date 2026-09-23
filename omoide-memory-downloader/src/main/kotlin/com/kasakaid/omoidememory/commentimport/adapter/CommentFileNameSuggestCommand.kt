package com.kasakaid.omoidememory.commentimport.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentFileFactory
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.ExactlyMatched
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.MatchedFile
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.Missed
import com.kasakaid.omoidememory.commentimport.service.OrphanCandidateSuggestService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "suggest-comment-filenames")
class CommentFileNameSuggestCommand(
    private val orphanCandidateSuggestService: OrphanCandidateSuggestService,
) : ApplicationRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments) {
        logger.info { "コメントファイル名修正候補出力を開始します" }

        val orphanFilePathStr = System.getenv("COMMENT_ORPHAN_FILE_PATH")
        if (orphanFilePathStr.isNullOrBlank()) {
            logger.error { "環境変数 COMMENT_ORPHAN_FILE_PATH が設定されていません" }
            return
        }

        val orphanFilePath = Path.of(orphanFilePathStr)
        if (!Files.exists(orphanFilePath)) {
            logger.error { "指定された orphan ファイルが存在しません: $orphanFilePathStr" }
            return
        }

        val candidateOutputPathStr = System.getenv("COMMENT_CANDIDATE_OUTPUT_FILE_PATH")
        if (candidateOutputPathStr.isNullOrBlank()) {
            logger.error { "環境変数 COMMENT_CANDIDATE_OUTPUT_FILE_PATH が設定されていません" }
            return
        }

        // 孤立ファイルは元CSV の全列形式。OmoideCommentFileFactory でパースして使う
        val orphanRecords =
            Files
                .readAllLines(orphanFilePath, StandardCharsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { line -> OmoideCommentFileFactory.create(line).getOrNull() }

        val checkResults =
            runBlocking {
                orphanCandidateSuggestService.suggest(
                    orphanFileNames = orphanRecords.map { it.omoideComment.fileName },
                )
            }

        val csvLines =
            listOf("コメントファイルのコンテンツ名,コメント内容,曖昧検索でのパターン,DBファイル名,試行結果の型") +
                checkResults.zip(orphanRecords).map { (result, record) ->
                    val commentBody = record.omoideComment.commentBody
                    when (result) {
                        is ExactlyMatched -> "${result.fileName},$commentBody,,,${result::class.simpleName}"
                        is MatchedFile -> "${result.fileName},$commentBody,${result.likePattern},${result.actualFileName},${result::class.simpleName}"
                        is Missed -> "${result.fileName},$commentBody,,,${result::class.simpleName}"
                    }
                }

        Files.write(
            Path.of(candidateOutputPathStr),
            csvLines.joinToString("\n").toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        val matchedCount = checkResults.count { it is MatchedFile }
        logger.info {
            "修正候補出力完了 " +
                "入力総数=${checkResults.size} " +
                "EXACT_MATCH=${checkResults.count { it is ExactlyMatched }} " +
                "MATCH=$matchedCount " +
                "MISS=${checkResults.count { it is Missed }}"
        }
    }
}
