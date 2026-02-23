package com.kasakaid.omoidememory.domain

import java.nio.file.Path
import java.time.OffsetDateTime

/**
 * ドライブ上にアップロードされたコンテンツ
 * 写真か動画かいずれかになる
 */
sealed interface OmoideMemory {
    val localPath: Path
    val name: String
    val mediaType: String
    val driveFileId: String? // ドライブから取得されていないデータもあるため、整合性をとるため NULL 許可
    val captureTime: OffsetDateTime
    val fileSize: Long

    class Photo(
        override val localPath: Path,
        override val name: String,
        override val mediaType: String,
        override val driveFileId: String?,
        override val fileSize: Long,
        val locationName: String?,
        val aperture: Float?,
        val shutterSpeed: String?,
        val isoSpeed: Int?,
        val focalLength: Float?,
        val focalLength35mm: Int?,
        val whiteBalance: String?,
        val imageWidth: Int?,
        val imageHeight: Int?,
        val orientation: Int?,
        val latitude: Double?,
        val longitude: Double?,
        val altitude: Double?,
        override val captureTime: OffsetDateTime,
        val deviceMake: String?,
        val deviceModel: String?,
    ) : OmoideMemory

    class Video(
        override val localPath: Path,
        override val name: String,
        override val mediaType: String,
        override val driveFileId: String?,
        override val fileSize: Long,
        val metadata: VideoMetadataDto,
        override val captureTime: OffsetDateTime,
    ) : OmoideMemory
}
