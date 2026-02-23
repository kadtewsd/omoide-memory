package com.kasakaid.omoidememory.downloader.domain

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.kasakaid.omoidememory.domain.Extension
import com.kasakaid.omoidememory.domain.LocalFile
import com.kasakaid.omoidememory.domain.MediaMetadata
import com.kasakaid.omoidememory.domain.MediaMetadataFactory
import java.nio.file.Path

enum class MediaType(
    private val extensions: Set<String>,
    val directoryName: String,
    val createMediaMetadata: (LocalFile) -> MediaMetadata,
) {
    VIDEO(
        directoryName = "video",
        extensions = setOf("mp4", "mov", "avi", "mkv", "3gp", "webm"),
        createMediaMetadata = { localFile ->
            MediaMetadataFactory.createVideo(localFile)
        },
    ),
    PHOTO(
        directoryName = "photo",
        extensions = setOf("jpg", "jpeg", "png", "heic", "heif", "gif", "webp"),
        createMediaMetadata = { localFile -> MediaMetadataFactory.createPhoto(localFile) },
    ),
    ;

    companion object {
        fun of(fileNameWithExtension: String): Option<MediaType> =
            Extension.of(fileNameWithExtension).value.fold(
                ifEmpty = { None },
                ifSome = { extension -> entries.firstOrNull { extension in it.extensions }?.some() ?: None },
            )
    }
}
