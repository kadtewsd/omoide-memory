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
    val familyId: String
    val mediaType: String
    val driveFileId: String? // ドライブから取得されていないデータもあるため、整合性をとるため NULL 許可
    val captureTime: OffsetDateTime
    val fileSize: Long

    /**
     * メタデータ抽出済みのインスタンスに対し、配置先パスだけを確定させる。
     * captureTime 等の抽出結果はそのまま維持し、localPath のみ差し替える。
     */
    fun fixPath(newPath: Path): OmoideMemory

    data class Photo(
        override val localPath: Path,
        override val name: String,
        override val familyId: String,
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
    ) : OmoideMemory {
        override fun fixPath(newPath: Path): OmoideMemory = copy(localPath = newPath)
    }

    data class Video(
        override val localPath: Path,
        override val name: String,
        override val familyId: String,
        override val mediaType: String,
        override val driveFileId: String?,
        override val fileSize: Long,
        val metadata: VideoMetadataDto,
        override val captureTime: OffsetDateTime,
    ) : OmoideMemory {
        override fun fixPath(newPath: Path): OmoideMemory = copy(localPath = newPath)
    }
}
