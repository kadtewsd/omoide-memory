package com.kasakaid.omoidememory.domain

import java.nio.file.Path
import java.time.OffsetDateTime

sealed interface OmoideMemory {
    val localPath: Path
    val name: String
    val mediaType: String
    val driveFileId: String
    val captureTime: OffsetDateTime?
    val deviceMake: String?
    val deviceModel: String?


    data class Photo(
        override val localPath: Path,
        override val name: String,
        override val mediaType: String,
        override val driveFileId: String,
        val aperture: Double?,
        val shutterSpeed: String?,
        val isoSpeed: Int?,
        val focalLength: Double?,
        val focalLength35mm: Int?,
        val whiteBalance: String?,
        val imageWidth: Int?,
        val imageHeight: Int?,
        val orientation: Int?,
        val latitude: Double?,
        val longitude: Double?,
        val altitude: Double?,
        override val captureTime: OffsetDateTime?,
        override val deviceMake: String?,
        override val deviceModel: String?,
    ) : OmoideMemory

    data class Video(
        override val localPath: Path,
        override val name: String,
        override val mediaType: String,
        override val driveFileId: String,
        val durationSeconds: Double?,
        val videoWidth: Int?,
        val videoHeight: Int?,
        val frameRate: Double?,
        val videoCodec: String?,
        val videoBitrateKbps: Int?,
        val audioCodec: String?,
        val audioBitrateKbps: Int?,
        val audioChannels: Int?,
        val audioSampleRate: Int?,
        val thumbnailImage: ByteArray?,   // 動画の1秒目のサムネイル
        val thumbnailMimeType: String?,   // "image/jpeg"
        val fileSizeBytes: Long,
        override val captureTime: OffsetDateTime?,
        override val deviceMake: String?,
        override val deviceModel: String?,
    ) : OmoideMemory
}