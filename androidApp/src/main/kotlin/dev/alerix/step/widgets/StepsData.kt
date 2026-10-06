package dev.alerix.step.widgets

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter

data class DaySteps(val date: LocalDate, val steps: Long)

sealed interface StepsState {
    data class Ready(
        val today: Long,
        val goal: Int, // daily goal
        val updatedAt: LocalTime,
    ) : StepsState

    data object NeedsSetup : StepsState
    data object Unavailable : StepsState
    data class Error(val message: String) : StepsState
}

object Goal {
    private const val KEY = "daily_goal"
    private fun prefs(c: Context) = c.getSharedPreferences("steps", Context.MODE_PRIVATE)
    fun get(c: Context): Int = prefs(c).getInt(KEY, 10_000)
    fun set(context: Context, value: Int) = prefs(context).edit { putInt(KEY, value) }}

object StepsRepository {

    private val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Steps permission, plus background reading if this phone's Health Connect supports it. */
    fun permissions(context: Context): Set<String> {
        val client = HealthConnectClient.getOrCreate(context)
        val bg = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return if (bg) setOf(READ_STEPS, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        else setOf(READ_STEPS)
    }

    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        val granted = HealthConnectClient.getOrCreate(context)
            .permissionController.getGrantedPermissions()
        return granted.containsAll(permissions(context))
    }

    suspend fun load(context: Context): StepsState {
        if (!isAvailable(context)) return StepsState.Unavailable
        if (!hasPermissions(context)) return StepsState.NeedsSetup

        val client = HealthConnectClient.getOrCreate(context)

        // Aggregation merges phone + watch data and removes duplicates,
        // so adding a watch later needs no code change.
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                TimeRangeFilter.after(LocalDate.now().atStartOfDay()),
            )
        )

        val today = response[StepsRecord.COUNT_TOTAL] ?: 0L

        return StepsState.Ready(today = today, goal = 10_000, updatedAt = LocalTime.now())
    }
}