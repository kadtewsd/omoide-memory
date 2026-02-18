package com.kasakaid.omoidememory.downloader.adapter.location

// Helper for potentially irregular JSON, though Kotlinx serialization handles basic mapping well.
// But standard approach above should work if field matches.
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object StringOrNullSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrNull", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        return try {
            decoder.decodeString()
        } catch (e: Exception) {
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}
