package com.kasakaid.omoidememory.commentimport.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentedDateFactory
import com.kasakaid.omoidememory.commentimport.service.CommentImportService
import com.kasakaid.omoidememory.domain.Extension
import com.kasakaid.omoidememory.utility.MyUUIDGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

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

        // ID の付与を読み込み順で行いたいので順列で処理していく
        runBlocking {
            importComment(
                lines
                    .filterIndexed { index, line ->
                        !(index == 0 && line.startsWith("コンテンツ")) && line.isNotBlank()
                    }.groupBy { line ->
                        parseCsvLine(line)[0].trim() // fileName
                    }.entries
                    .associate { (fileName, groupedLines) ->
                        // ここで新しいUUIDを「キー」に、行リストを「値」に変換
                        FileKey(fileName) to groupedLines
                    },
            )
        }
        logger.info { "コメントインポート処理を終了しました" }
    }

    class FileKey(
        val name: String,
    ) {
        val mediaId: UUID = MyUUIDGenerator.generateUUIDv7()
        val mediaType = Extension.of(name).mimeType
    }

    private suspend fun importComment(groupedLines: Map<FileKey, Collection<String>>) {
        groupedLines.entries
            .flatMap { entry ->
                val file = entry.key
                val fileLines = entry.value
                fileLines.mapNotNull { line ->
                    val parts = parseCsvLine(line)
                    if (parts.size >= 3) {
                        val authorDate = parts.last().trim()
                        val authorParts = authorDate.split(Regex("[·・]"), limit = 2)
                        val commentedAt =
                            OmoideCommentedDateFactory
                                .create(fileName = file.name, authorParts = authorParts)
                                .fold(
                                    ifLeft = {
                                        throw IllegalStateException("パース不可能なコメントです: ${file.name} $parts $it")
                                    },
                                    ifRight = { it },
                                )

                        OmoideComment(
                            fileName = file.name,
                            commentBody = parts.subList(1, parts.size - 1).joinToString(",").trim(),
                            commenterName = if (authorParts.isNotEmpty()) authorParts[0].trim() else "",
                            commentedAt = commentedAt,
                            mediaType = file.mediaType,
                            feedId = file.mediaId,
                        )
                    } else {
                        logger.warn { "フォーマットが正しくない行をスキップします: $line" }
                        null
                    }
                }
            }.let { comments ->
                transactionalOperator.executeAndAwait {
                    Flux
                        .fromIterable(comments)
                        .concatMap { comment ->
                            mono {
                                logger.info { "${comment.fileName}: ${comment.commenterName}" }
                                commentImportService.importComment(comment)
                            }
                        }.then()
                        .awaitFirstOrNull()
                }
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
