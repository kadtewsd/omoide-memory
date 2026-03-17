package com.kasakaid.omoidememory.commentimport.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentedDateFactory
import com.kasakaid.omoidememory.commentimport.service.CommentImportService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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

        lines
            .mapIndexedNotNull { index, line ->
                if (index == 0 && line.startsWith("コンテンツ")) return@mapIndexedNotNull null
                if (line.isBlank()) return@mapIndexedNotNull null

                val parts = parseCsvLine(line)
                if (parts.size >= 3) {
                    val fileName = parts[0].trim()
                    val authorDate = parts.last().trim()
                    val authorParts = authorDate.split(Regex("[·・]"), limit = 2)

                    OmoideComment(
                        fileName = fileName,
                        commentBody = parts.subList(1, parts.size - 1).joinToString(",").trim(),
                        commenterName = if (authorParts.isNotEmpty()) authorParts[0].trim() else "",
                        commentedAt =
                            OmoideCommentedDateFactory.create(fileName = fileName, authorParts = authorParts).fold(
                                ifLeft = {
                                    throw IllegalStateException("問題あり: $fileName $parts")
                                },
                                ifRight = { it },
                            ),
                    )
                } else {
                    logger.warn { "フォーマットが正しくない行をスキップします: $line" }
                    null
                }
            }.let { omoideComments ->
                logger.info { "${omoideComments.size} 件のコメントをパースしました" }

                // ID の付与を読み込み順で行いたいので順列で処理していく
                runBlocking {
                    importComment(omoideComments)
                }
                logger.info { "コメントインポート処理を終了しました" }
            }
    }

    private suspend fun importComment(omoideComments: List<OmoideComment>) {
        transactionalOperator.executeAndAwait {
            Flux
                .fromIterable(omoideComments)
                .concatMap { comment ->
                    mono {
                        logger.info { "${comment.fileName}: ${comment.commenterName}" }
                        commentImportService.importComment(comment)
                    }
                }.then()
                .awaitFirstOrNull()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = java.lang.StringBuilder()
        var inQuotes = false
        for (char in line) {
            if (char == '"') {
                inQuotes = !inQuotes
            } else if (char == ',' && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
