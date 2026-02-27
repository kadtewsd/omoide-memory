package com.kasakaid.omoidememory.commentimport.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.ParsedComment
import com.kasakaid.omoidememory.commentimport.service.CommentImportService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
@ConditionalOnProperty(name = [APPLICATION_RUNNER_KEY], havingValue = "import-comments")
class CommentImportCommand(
    private val commentImportService: CommentImportService,
) : ApplicationRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments): Unit =
        runBlocking {
            logger.info { "コメントインポート処理を開始します" }

            val filePathStr = System.getenv("OMOIDE_COMMENT_FILE_PATH")
            if (filePathStr.isNullOrBlank()) {
                logger.error { "環境変数 OMOIDE_COMMENT_FILE_PATH が設定されていません" }
                return@runBlocking
            }

            val filePath = Path.of(filePathStr)
            if (!Files.exists(filePath)) {
                logger.error { "指定されたファイルが存在しません: $filePathStr" }
                return@runBlocking
            }

            val lines = Files.readAllLines(filePath)
            if (lines.isEmpty()) {
                logger.warn { "入力テキストが空です" }
                return@runBlocking
            }

            val parsedComments = mutableListOf<ParsedComment>()
            for ((index, line) in lines.withIndex()) {
                if (index == 0 && line.startsWith("コンテンツ")) continue
                if (line.isBlank()) continue

                val parts = parseCsvLine(line)
                if (parts.size >= 3) {
                    val fileName = parts[0].trim()
                    val authorDate = parts.last().trim()

                    val commentBody = parts.subList(1, parts.size - 1).joinToString(",").trim()

                    val authorParts = authorDate.split("·", limit = 2)
                    val commenterName = if (authorParts.isNotEmpty()) authorParts[0].trim() else ""
                    val dateString = if (authorParts.size == 2) authorParts[1].trim() else ""

                    parsedComments.add(
                        ParsedComment(
                            fileName = fileName,
                            commentBody = commentBody,
                            commenterName = commenterName,
                            dateString = dateString,
                        ),
                    )
                } else {
                    logger.warn { "フォーマットが正しくない行をスキップします: $line" }
                }
            }

            logger.info { "${parsedComments.size} 件のコメントをパースしました" }

            for (comment in parsedComments) {
                try {
                    org.slf4j.MDC.put("requestId", comment.fileName)
                    commentImportService.importComment(comment)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process comment for file: ${comment.fileName}" }
                } finally {
                    org.slf4j.MDC.remove("requestId")
                }
            }

            logger.info { "コメントインポート処理を終了しました" }
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
