package com.kasakaid.omoidememory.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.bind
import arrow.core.raise.context.either
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.kasakaid.omoidememory.utility.JsonMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class MetadataExtractError(
    val ex: Exception,
)

object VideoMetaInfoExtractor {
    /**
     * ffmpeg が入っていると思われるパス。インストール済みであれば パスが通っている前提
     */
    val ffmpegPath = System.getenv("FFMPEG_PATH") ?: "ffmpeg"
    val ffprobePath = System.getenv("FFPROBE_PATH") ?: "ffprobe"

    private val ffmpegLimiter = Semaphore(4)

    /**
     *  メタ情報を抽出します。
     */
    suspend fun extractAll(videoPath: Path): Either<MetadataExtractError, VideoMetadataDto> =
        ffmpegLimiter.withPermit {
            withContext(Dispatchers.IO) {
                either {
                    val json: JsonNode = runFfprobe(videoPath).bind()
                    val streams = json["streams"]
                    val format = json["format"]

                    val videoStream = streams.firstOrNull { it["codec_type"]?.asText() == "video" }
                    val audioStream = streams.firstOrNull { it["codec_type"]?.asText() == "audio" }

                    val frameRate =
                        videoStream?.get("r_frame_rate")?.asText()?.let {
                            val parts = it.split("/")
                            if (parts.size == 2 && parts[1] != "0") {
                                parts[0].toDouble() / parts[1].toDouble()
                            } else {
                                null
                            }
                        }

                    val thumbnailBytes = generateThumbnailByFFmpeg(videoPath).getOrNull()

                    VideoMetadataDto(
                        durationSeconds = format?.get("duration")?.asText()?.toDoubleOrNull(),
                        videoWidth = videoStream?.get("width")?.asInt(),
                        videoHeight = videoStream?.get("height")?.asInt(),
                        frameRate = frameRate,
                        videoCodec = videoStream?.get("codec_name")?.asText(),
                        videoBitrateKbps = videoStream?.get("bit_rate")?.asLong()?.div(1000),
                        audioCodec = audioStream?.get("codec_name")?.asText(),
                        audioBitrateKbps = audioStream?.get("bit_rate")?.asLong()?.div(1000),
                        audioChannels = audioStream?.get("channels")?.asInt(),
                        audioSampleRate = audioStream?.get("sample_rate")?.asInt(),
                        thumbnailBytes = thumbnailBytes?.first,
                        thumbnailMimeType = thumbnailBytes?.second,
                    )
                }
            }
        }

    private fun runFfprobe(videoPath: Path): Either<MetadataExtractError, JsonNode> {
        val command =
            listOf(
                ffprobePath,
                "-v",
                "quiet",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                videoPath.toAbsolutePath().toString(),
            )

        logger.debug { "コマンド: $command " }
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            JsonMapper.mapper.readTree(output).right()
        } else {
            MetadataExtractError(IllegalStateException("ffprobe が失敗: exitCode: $exitCode 出力文字列: $output")).left()
        }
    }

    /**
     * 動画の1秒目のフレームをJPEGサムネイルとして生成
     * ffmpegPath を使うことでサムネイルを生成します。
     */
    private fun generateThumbnailByFFmpeg(videoPath: Path): Either<MetadataExtractError, Pair<ByteArray, String>> {
        logger.debug { "サムネイルを ${videoPath.fileName} で生成" }

        return try {
            val tempThumbnail = Files.createTempFile("thumbnail_", ".jpg")

            val process =
                ProcessBuilder(
                    listOf(
                        ffmpegPath,
                        "-ss",
                        "00:00:01", // 1秒目にシーク
                        "-i",
                        videoPath.toString(), // 入力ファイル
                        "-vframes",
                        "1", // 1フレームだけ抽出
                        "-q:v",
                        "2", // JPEG品質（2が高品質）
                        "-y", // 上書き確認なし
                        tempThumbnail.toAbsolutePath().toString(),
                    ),
                ).redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(10, TimeUnit.SECONDS)

            if (!finished || process.exitValue() != 0) {
                MetadataExtractError(
                    IllegalArgumentException("Thumbnail generation failed: $output"),
                ).left()
            }

            val thumbnailBytes = Files.readAllBytes(tempThumbnail)
            Files.deleteIfExists(tempThumbnail)

            (thumbnailBytes to "image/jpeg").right()
        } catch (e: Exception) {
            logger.warn { "ffmpegが存在しないかもしれません。サムネイルなしで保存します。" }
            logger.warn { "インストール方法: ${getInstallInstructions()}" }
            MetadataExtractError(e).left()
        }
    }

    private fun getInstallInstructions(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> {
                "brew install ffmpeg"
            }

            os.contains("win") -> {
                "https://www.gyan.dev/ffmpeg/builds/ からダウンロードしてC:\\ffmpeg\\binに配置してください"
            }

            else -> {
                "sudo apt-get install ffmpeg (Debian/Ubuntu) または yum install ffmpeg (CentOS/RHEL)"
            }
        }
    }
}

class VideoMetadataDto(
    val durationSeconds: Double?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val frameRate: Double?,
    val videoCodec: String?,
    val videoBitrateKbps: Long?,
    val audioCodec: String?,
    val audioBitrateKbps: Long?,
    val audioChannels: Int?,
    val audioSampleRate: Int?,
    val thumbnailBytes: ByteArray?,
    val thumbnailMimeType: String?,
)
