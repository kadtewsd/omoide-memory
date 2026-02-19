package com.kasakaid.omoidememory.domain

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import java.nio.file.Path

class Exif(
    val exifIFD0: ExifIFD0Directory?,
    val exifSubIFD: ExifSubIFDDirectory?,
    val gpsDirectory: GpsDirectory?,
) {
    companion object {
        fun of(path: Path): Exif {
            return ImageMetadataReader.readMetadata(path.toFile()).let { metadata ->
                Exif(
                    //EXIF基本情報
                    exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java),
                    exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java),
                    gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory::class.java),
                )
            }
        }
    }
}
