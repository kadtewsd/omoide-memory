package com.kasakaid.omoidememory.commentimport.adapter

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.FileLine
import com.kasakaid.omoidememory.commentimport.domain.model.FileName
import com.kasakaid.omoidememory.commentimport.service.CommentImportService
import com.kasakaid.omoidememory.commentimport.service.NoneExistenceContentName
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.nio.file.Files
import java.nio.file.Path

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "import-comments")
class CommentImportCommand(
    private val commentImportService: CommentImportService,
    private val transactionalOperator: TransactionalOperator,
) : ApplicationRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments) {
        logger.info { "コメントインポート処理を開始します" }

        val filePathStr = System.getenv("OMOIDE_COMMENT_FILE_PATH")
        if (filePathStr.isNullOrBlank()) {
            logger.error { "環境変数 OMOIDE_COMMENT_FILE_PATH が設定されていません" }
            return
        }

        val filePath = Path.of(filePathStr)
        if (!Files.exists(filePath)) {
            logger.error { "指定されたファイルが存在しません: $filePathStr" }
            return
        }

        val lines = Files.readAllLines(filePath)
        if (lines.isEmpty()) {
            logger.warn { "入力テキストが空です" }
            return
        }

        val commentDuplicationPath = System.getenv("COMMENT_DUPLICATEION_FILE_PATH")
        if (commentDuplicationPath.isNullOrBlank()) {
            logger.error { "環境変数 COMMENT_DUPLICATEION_FILE_PATH が設定されていません" }
            return
        }

        // ID の付与を読み込み順で行いたいので順列で処理していく
        runBlocking {
            importComment(
                lines
                    .map {
                        FileLine(it)
                    }.filterIndexed { index, line ->
                        // ヘッダー行の除去
                        !(index == 0 && line.line.startsWith("コンテンツ")) && line.line.isNotBlank()
                    }.groupBy { line ->
                        line.fileName
                    },
            ).map {
                Files.write(
                    Path.of(commentDuplicationPath),
                    it.joinToString("\n") { fileName -> fileName }.toByteArray(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                )
            }
        }
        logger.info { "コメントインポート処理を終了しました" }
    }

    private suspend fun importComment(groupedLines: Map<FileName, Collection<FileLine>>): Option<List<NoneExistenceContentName>> {
        val fileNames = arrayOfNulls<Option<NoneExistenceContentName>>(groupedLines.size)
        groupedLines.entries.forEachIndexed { index, entry ->
            transactionalOperator.executeAndAwait {
                fileNames[index] =
                    commentImportService.importComment(
                        fileName = entry.key,
                        fileLines = entry.value,
                    )
            }
        }
        return fileNames
            .mapNotNull {
                it?.getOrNull()
            }.let {
                if (it.isNotEmpty()) {
                    it.some()
                } else {
                    None
                }
            }
    }
}
