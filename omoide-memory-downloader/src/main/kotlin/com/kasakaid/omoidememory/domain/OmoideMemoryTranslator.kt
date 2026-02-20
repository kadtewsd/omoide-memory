package com.kasakaid.omoidememory.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class MetadataExtractError(
    val ex: Exception,
)

object OmoideMemoryTranslator {
    /**
     * 動画の1秒目のフレームをJPEGサムネイルとして生成
     */
    fun generateThumbnail(videoPath: Path): Either<MetadataExtractError, Pair<ByteArray, String>> {
        logger.debug { "サムネイルを ${videoPath.name} で生成" }
        return try {
            val tempThumbnail = Files.createTempFile("thumbnail_", ".jpg")

            // ffmpegを直接呼び出し
            val command =
                listOf(
                    "ffmpeg",
                    "-ss",
                    "00:00:01", // 1秒目にシーク
                    "-i",
                    videoPath.toString(), // 入力ファイル
                    "-vframes",
                    "1", // 1フレームだけ抽出
                    "-q:v",
                    "2", // JPEG品質（2が高品質）
                    "-y", // 上書き確認なし
                    tempThumbnail.toString(),
                )

            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.warn { "ffmpegエラー: $errorOutput" }
                return MetadataExtractError(IllegalArgumentException("サムネイル生成に失敗しました。$errorOutput")).left()
            }

            val thumbnailBytes = Files.readAllBytes(tempThumbnail)
            Files.delete(tempThumbnail)

            (thumbnailBytes to "image/jpeg").right()
        } catch (e: Exception) {
            logger.warn(e) { "サムネイル生成に失敗: $videoPath" }
            MetadataExtractError(e).left()
        }
    }
}
