package com.kasakaid.omoidememory.downloader.domain

import io.github.oshai.kotlinlogging.KotlinLogging

data class ParsedComment(
    val fileName: String,
    val commentBody: String,
    val commenterName: String,
    val dateString: String,
)

object GooglePhotoCommentParser {
    private val logger = KotlinLogging.logger {}

    private val extensions =
        listOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".heic",
            ".heif",
            ".gif",
            ".webp",
            ".mp4",
            ".mov",
            ".avi",
            ".mkv",
            ".3gp",
            ".webm",
        )

    fun parseLines(lines: List<String>): List<ParsedComment> {
        val result = mutableListOf<ParsedComment>()
        var currentFileName = ""
        var currentCommentBody = ""

        for (rawLine in lines) {
            val line = rawLine
            val trimmedLine = line.trim()

            // Skip headers or empty lines
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("コンテンツの名前")) {
                continue
            }

            // If the line contains the middle dot '·', it's the commenter info line
            if (trimmedLine.contains("·")) {
                val parts = trimmedLine.split("·", limit = 2)
                if (parts.size == 2 && currentFileName.isNotEmpty()) {
                    val commenter = parts[0].trim()
                    val dateStr = parts[1].trim()
                    result.add(ParsedComment(currentFileName, currentCommentBody.trim(), commenter, dateStr))

                    // Reset state
                    currentFileName = ""
                    currentCommentBody = ""
                } else {
                    logger.warn { "Unexpected commenter line format or missing photo line: $trimmedLine" }
                }
                continue
            }

            // Check if this line starts a new file + comment block
            var matchedExt = ""
            var matchedIndex = -1
            for (ext in extensions) {
                val idx = line.indexOf(ext, ignoreCase = true)
                if (idx != -1) {
                    if (matchedIndex == -1 || idx < matchedIndex) {
                        matchedIndex = idx
                        matchedExt = line.substring(idx, idx + ext.length)
                    }
                }
            }

            if (matchedIndex != -1) {
                val splitIndex = matchedIndex + matchedExt.length
                currentFileName = line.substring(0, splitIndex).trim()
                currentCommentBody = line.substring(splitIndex).trim()
            } else {
                // If it doesn't have an extension, it might be a multi-line comment.
                if (currentFileName.isNotEmpty()) {
                    currentCommentBody += "\n" + trimmedLine
                } else {
                    logger.warn { "Could not find extension in line and no active photo: $trimmedLine" }
                }
            }
        }
        return result
    }
}
