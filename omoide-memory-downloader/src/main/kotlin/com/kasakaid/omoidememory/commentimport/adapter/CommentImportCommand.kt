package com.kasakaid.omoidememory.commentimport.adapter

import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.GooglePhotoCommentParser
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

            val lines = mutableListOf<String>()

            if (args.containsOption("file")) {
                val filePaths = args.getOptionValues("file")
                if (!filePaths.isNullOrEmpty()) {
                    val filePath = Path.of(filePaths[0])
                    if (Files.exists(filePath)) {
                        lines.addAll(Files.readAllLines(filePath))
                    } else {
                        logger.error { "指定されたファイルが存在しません: ${filePaths[0]}" }
                        return@runBlocking
                    }
                }
            } else {
                logger.info { "標準入力からコメントテキストを読み込みます..." }
                System.`in`.bufferedReader().useLines {
                    lines.addAll(it.toList())
                }
            }

            if (lines.isEmpty()) {
                logger.warn { "入力テキストが空です" }
                return@runBlocking
            }

            val parsedComments = GooglePhotoCommentParser.parseLines(lines)
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
}
