package dev.alerix.step.widgets

import android.content.Context
import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.alerix.step.R
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class StepsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = try {
            StepsRepository.load(context)
        } catch (_: SecurityException) {
            StepsState.NeedsSetup
        } catch (e: Exception) {
            StepsState.Error(e.message ?: "Couldn't load steps")
        }
        provideContent {
            GlanceTheme {
                Content(state)
            }
        }
    }
}

@Composable
private fun Content(state: StepsState) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius),
        contentAlignment = Alignment.BottomCenter,
    ) {
        when (state) {
            is StepsState.Ready -> Steps(state)
            StepsState.NeedsSetup -> Message("Open app")
            StepsState.Unavailable -> Message("No Health Connect")
            is StepsState.Error -> Message("Error")
        }
    }
}

private val BORDER = 8.dp

@Composable
private fun Steps(state: StepsState.Ready) {
    val ratio = (state.today.toFloat() / state.goal).coerceIn(0f, 1f)
//    val ratio = 0.2f
    val label = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val compact = CompactDecimalFormat.getInstance(
        LocalContext.current.resources.configuration.locales[0],
        CompactDecimalFormat.CompactStyle.SHORT,
    )

    // Fill grows from the bottom. The outer corner radius clips it.
    Box(GlanceModifier.fillMaxSize().padding(BORDER)) {
        Box(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(android.R.dimen.system_app_widget_inner_radius),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (ratio > 0f) {
                Box(
                    GlanceModifier
                        .fillMaxWidth()
                        .height(LocalSize.current.height * ratio)
                        .cornerRadius(android.R.dimen.system_app_widget_inner_radius)
                        .background(GlanceTheme.colors.secondary)
                ) {}
            }
            Image(
                provider = ImageProvider(R.drawable.ic_steps),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp).padding(bottom = 10.dp),
                colorFilter =
                    if (ratio < 0.2f)
                        ColorFilter.tint(GlanceTheme.colors.secondary)
                    else
                        ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
            )
            Column(
                GlanceModifier.fillMaxSize().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(compact.format(state.today), style = label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 50, heightDp = 90)
@Composable
private fun StepsPreview() {
    GlanceTheme {
        Content(StepsState.Ready(today = 6_400, goal = 10_000, updatedAt = LocalTime.NOON))
    }
}

// ---------- Tap to refresh ----------

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        StepsWidget().update(context, glanceId)
    }
}

// ---------- Receiver ----------

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}

// ---------- Refresh every 15 minutes (Android's minimum for periodic work) ----------

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        StepsWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "steps-widget-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
