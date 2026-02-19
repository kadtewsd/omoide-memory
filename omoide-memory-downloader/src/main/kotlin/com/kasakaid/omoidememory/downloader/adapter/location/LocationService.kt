package com.kasakaid.omoidememory.downloader.adapter.location

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

object LocationService {

    private val logger = KotlinLogging.logger {}

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    suspend fun getLocationName(latitude: Double, longitude: Double): String? {
        // Rate limiting: Nominatim requires at least 1 second between requests per User-Agent
        delay(1100)

        return try {
            val response: NominatimResponse = client.get("https://nominatim.openstreetmap.org/reverse") {
                parameter("format", "json")
                parameter("lat", latitude)
                parameter("lon", longitude)
                parameter("accept-language", "ja")
                header("User-Agent", "OmoideMemoryDownloader/1.0")
            }.body()

            response?.displayName
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get location name for $latitude, $longitude" }
            null
        }
    }

    @Serializable
    data class NominatimResponse(
        @Serializable(with = StringOrNullSerializer::class)
        val display_name: String? = null
    ) {
        val displayName: String? get() = display_name
    }
}


