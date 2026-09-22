package com.kasakaid.omoidememory.infrastructure

import com.kasakaid.omoidememory.jooq.omoide_memory.tables.references.LOCATION_CACHE
import com.kasakaid.omoidememory.r2dbc.DSLGenerator
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class LocationCacheRepository(
    private val dslContext: DSLGenerator,
) {
    suspend fun findLocation(
        roundedLatitude: Double,
        roundedLongitude: Double,
    ): String? {
        LOCATION_CACHE.run {
            val record =
                dslContext
                    .invoke()
                    .select(ADDRESS)
                    .from(this)
                    .where(ROUNDED_LATITUDE.eq(roundedLatitude.toBigDecimal()))
                    .and(ROUNDED_LONGITUDE.eq(roundedLongitude.toBigDecimal()))
                    .awaitFirstOrNull()
            return record?.value1()
        }
    }

    suspend fun saveLocation(
        roundedLatitude: Double,
        roundedLongitude: Double,
        formattedAddress: String,
    ) {
        LOCATION_CACHE.run {
            dslContext
                .invoke()
                .insertInto(this)
                .set(ROUNDED_LATITUDE, roundedLatitude.toBigDecimal())
                .set(ROUNDED_LONGITUDE, roundedLongitude.toBigDecimal())
                .set(ADDRESS, formattedAddress)
                .set(CREATED_AT, OffsetDateTime.now())
                .onConflict(ROUNDED_LATITUDE, ROUNDED_LONGITUDE)
                .doNothing()
                .awaitFirstOrNull()
        }
    }
}
