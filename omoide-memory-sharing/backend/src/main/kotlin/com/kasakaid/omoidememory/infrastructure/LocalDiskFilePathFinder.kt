package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.domain.model.FilePathFinder
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Component
class LocalDiskFilePathFinder : FilePathFinder {
    override fun findPath(serverPath: String): Path? {
        if (serverPath.isEmpty()) return null
        return try {
            val path = Paths.get(serverPath)
            if (Files.exists(path)) path else null
        } catch (e: Exception) {
            null
        }
    }
}
