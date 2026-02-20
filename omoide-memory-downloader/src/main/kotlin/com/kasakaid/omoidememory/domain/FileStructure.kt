package com.kasakaid.omoidememory.domain

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import java.nio.file.Path

/**
 * ファイルの構造
 */
class FileStructure(
    val valueWithoutExtension: String,
    val extension: Extension,
) {
    val fullName: String
        get() =
            extension.value.fold(
                ifEmpty = { valueWithoutExtension },
                ifSome = { ext -> "$valueWithoutExtension.$ext" },
            )

    companion object {
        fun of(fileName: String): FileStructure {
            val valueWithoutExtension = fileName.substringBeforeLast(".", fileName)
            val extension = Extension.of(fileName)

            return FileStructure(
                valueWithoutExtension = valueWithoutExtension,
                extension = extension,
            )
        }

        fun of(path: Path): FileStructure = of(path.fileName.toString())
    }

    /**
     * 連番付きのファイル名を生成
     */
    fun withCounter(counter: Int): String {
        val withCounterValue = "${valueWithoutExtension}_$counter"
        return extension.value.fold(
            ifEmpty = { withCounterValue },
            ifSome = { ext -> "$withCounterValue.$ext" },
        )
    }
}

class Extension(
    val value: Option<String>,
    val mimeType: String,
) {
    companion object {
        private val MIME_TYPE_MAP =
            mapOf(
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "png" to "image/png",
                "heic" to "image/heic",
                "heif" to "image/heif",
                "gif" to "image/gif",
                "webp" to "image/webp",
                "mp4" to "video/mp4",
                "mov" to "video/quicktime",
                "avi" to "video/x-msvideo",
                "mkv" to "video/x-matroska",
                "3gp" to "video/3gpp",
                "webm" to "video/webm",
            )

        private const val DEFAULT_MIME_TYPE = "application/octet-stream"

        fun of(fileName: String): Extension {
            val extensionValue =
                fileName.substringAfterLast(".", "").let {
                    if (it.isEmpty()) None else it.some()
                }

            val mimeType =
                extensionValue.fold(
                    ifEmpty = { DEFAULT_MIME_TYPE },
                    ifSome = { ext -> MIME_TYPE_MAP[ext.lowercase()] ?: DEFAULT_MIME_TYPE },
                )

            return Extension(
                value = extensionValue,
                mimeType = mimeType,
            )
        }

        fun of(path: Path): Extension = of(path.fileName.toString())
    }
}
