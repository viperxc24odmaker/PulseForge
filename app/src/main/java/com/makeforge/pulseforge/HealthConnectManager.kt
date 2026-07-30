package com.makeforge.pulseforge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Wraps all Health Connect reads. This is where "watch data" comes from:
 *  a paired watch (Galaxy Watch, Pixel Watch, Fitbit, etc) syncs its steps +
 *  heart rate into Health Connect via its own app, and we read it here. */
class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean {
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun readToday(): HealthSnapshot {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val agg: AggregationResult = client().aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
            )
        )

        val steps = agg[StepsRecord.COUNT_TOTAL] ?: 0L
        val calories = agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        val distance = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0

        // latest heart rate sample in the past 24h
        val hrRecords = client().readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(24 * 3600), now)
            )
        ).records

        val latestBpm = hrRecords
            .flatMap { it.samples }
            .maxByOrNull { it.time }
            ?.beatsPerMinute

        return HealthSnapshot(steps, calories, distance, latestBpm)
    }
}

data class HealthSnapshot(
    val steps: Long,
    val calories: Double,
    val distanceMeters: Double,
    val latestHeartRate: Long?,
)
