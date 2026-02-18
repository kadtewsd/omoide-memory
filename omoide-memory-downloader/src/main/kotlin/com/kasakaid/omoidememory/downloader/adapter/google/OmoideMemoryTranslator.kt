package com.kasakaid.omoidememory.downloader.adapter.google

import arrow.core.None
import arrow.core.Option
import arrow.core.some
import com.google.api.services.drive.model.File
import com.kasakaid.omoidememory.domain.OmoideMemory
import com.kasakaid.omoidememory.downloader.adapter.location.LocationService
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class OmoideMemoryTranslator(
    private val environment: Environment,
) {
    suspend fun translate(googleFile: File): Option<OmoideMemory> {
        // Windows と Mac でいい感じで絶対パスを表現できないか？ OS のタイプを環境変数に入れるか?
        Path.of(environment.getProperty("omoide_backup_destination") + "\\${googleFile.name}").let { path ->
            return when (googleFile.mimeType) {
                "video/mp4", "他のメディア...." -> OmoideMemory.Video(
                    localPath = path,
                ).some() // Video になりそうな MediaType を全部列挙
                "jpeg", "png" -> OmoideMemory.Photo(
                    path = path,
                    locationName = LocationService.getLocationName(longitude = googleFile.linkShareMetadata...,)
                ).some() // Picture になりそうな MediaType を全部列挙
                else -> None
            }
        }
    }
}