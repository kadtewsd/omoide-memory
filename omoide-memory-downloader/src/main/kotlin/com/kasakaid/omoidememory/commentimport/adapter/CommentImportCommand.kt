package com.kasakaid.omoidememory.commentimport.adapter

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.APPLICATION_RUNNER_KEY
import com.kasakaid.omoidememory.commentimport.domain.model.FileName
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideComment
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentFile
import com.kasakaid.omoidememory.commentimport.domain.model.OmoideCommentFileFactory
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

        val orphanFilePath = System.getenv("COMMENT_ORPHAN_FILE_PATH")
        if (orphanFilePath.isNullOrBlank()) {
            logger.error { "環境変数 COMMENT_ORPHAN_FILE_PATH が設定されていません" }
            return
        }

        // ID の付与を読み込み順で行いたいので順列で処理していく
        runBlocking {
            val parsed =
                lines
                    .filterIndexed { index, line ->
                        // ヘッダー行の除去
                        !(index == 0 && line.startsWith("コンテンツ")) && line.isNotBlank()
                    }.map {
                        OmoideCommentFileFactory.create(it)
                    }
            // エラーと成功に分解。orNull と mapNotNull でどっちかにわかれるだろうということ。!! をつかってもいいが、心理的にやだ、というあほらしい理由でこれにしている... 果たしてそこまで頑張る意味があるのだろうか？と思わせるコード
            val (errors, omoideComment) =
                parsed.partition { it.isLeft() }.let { (l, r) ->
                    l.mapNotNull { it.leftOrNull() } to
                        r.mapNotNull { it.getOrNull() }
                }
            if (errors.isNotEmpty()) {
                val message = errors.joinToString("\n") { error -> error.message }
                throw IllegalStateException(message)
            }
            importComment(
                omoideComment.groupBy { line ->
                    line.omoideComment.fileName
                },
            ).map {
                Files.write(
                    Path.of(orphanFilePath),
                    it.joinToString("\n") { fileName -> fileName }.toByteArray(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                )
            }
        }
        logger.info { "コメントインポート処理を終了しました" }
    }

    private suspend fun importComment(groupedLines: Map<FileName, Collection<OmoideCommentFile>>): Option<List<NoneExistenceContentName>> {
        val fileNames = arrayOfNulls<Option<NoneExistenceContentName>>(groupedLines.size)
        groupedLines.entries.forEachIndexed { index, entry ->
            transactionalOperator.executeAndAwait {
                fileNames[index] =
                    commentImportService.importComment(
                        fileName = entry.key,
                        omoideComments = entry.value.map { it.omoideComment }.toSet(),
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
