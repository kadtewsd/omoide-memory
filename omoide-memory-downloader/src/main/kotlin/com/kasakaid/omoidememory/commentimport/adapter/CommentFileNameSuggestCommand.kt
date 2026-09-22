package com.kasakaid.omoidememory.commentimport.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.commentintegrity.CommentIntegrity
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

        val orphanFileNames =
            Files
                .readAllLines(orphanFilePath, StandardCharsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

        val integrities =
            runBlocking {
                orphanCandidateSuggestService.suggest(
                    orphanFileNames = orphanFileNames,
                )
            }

        val csvLines =
            listOf("オリジナルファイル名,種類,検索結果区分,ヒットしたファイル名") + integrities.map { it.toCsvRow() }

        Files.write(
            Path.of(candidateOutputPathStr),
            csvLines.joinToString("\n").toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        val exactlyMatchedCount = integrities.count { it is ExactlyMatched }
        val matchedCount = integrities.count { it is MatchedFile }
        val missedCount = integrities.count { it is Missed }
        logger.info {
            "修正候補出力完了 " +
                "未マッチ総数=${integrities.size} " +
                "EXACT_MATCH=$exactlyMatchedCount " +
                "MATCH=$matchedCount " +
                "MISS=$missedCount"
        }
    }

    /**
     * CommentIntegrity 1件を CSV の1行に変換する。
     * 「検索結果区分」と「ヒットしたファイル名」は型ごとに内容が変わる。
     */
    private fun CommentIntegrity.toCsvRow(): String {
        val (statusLabel, hitFileName) =
            when (this) {
                is ExactlyMatched -> "EXACT_MATCH" to ""
                is MatchedFile -> "MATCH" to hitPattern
                is Missed -> "MISS" to ""
            }

        return listOf(fileName, mediaType, statusLabel, hitFileName)
            .joinToString(",") { escapeCsvField(it) }
    }

    private fun escapeCsvField(raw: String): String =
        if (raw.contains(',') || raw.contains('"') || raw.contains('\n')) {
            "\"${raw.replace("\"", "\"\"")}\""
        } else {
            raw
        }
}
