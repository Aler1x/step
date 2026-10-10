package dev.alerix.step.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.alerix.step.R
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val WORKER_TICK = longPreferencesKey("worker_tick")

class StepsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadState(context)
        provideContent {
            var state by remember { mutableStateOf(initial) }
            var refreshing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            // update() on a live session only re-reads Glance state, so the worker bumps this key to force a reload.
            val workerTick = currentState(WORKER_TICK)
            LaunchedEffect(workerTick) { state = loadState(context) }
            GlanceTheme {
                Content(state, refreshing) {
                    scope.launch {
                        refreshing = true
                        delay(500.milliseconds) // ponytail: the load is near instant, so keep the spinner visible long enough to notice
                        state = loadState(context)
                        refreshing = false
                    }
                }
            }
        }
    }

    private suspend fun loadState(context: Context): StepsState = try {
        StepsRepository.load(context)
    } catch (_: SecurityException) {
        StepsState.NeedsSetup
    } catch (e: Exception) {
        StepsState.Error(e.message ?: "Couldn't load steps")
    }
}

@Composable
private fun Content(state: StepsState, refreshing: Boolean, onRefresh: () -> Unit) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(onRefresh),
        contentAlignment = Alignment.BottomCenter,
    ) {
        when (state) {
            is StepsState.Ready -> Steps(state, refreshing)
            StepsState.NeedsSetup -> Message("Open app", refreshing)
            StepsState.Unavailable -> Message("No Health Connect", refreshing)
            is StepsState.Error -> Message("Error", refreshing)
        }
    }
}

private val BORDER = 8.dp
private const val FILL_FRAMES = 6

@Composable
private fun Steps(state: StepsState.Ready, refreshing: Boolean) {
    val ratio = (state.today.toFloat() / state.goal).coerceIn(0f, 1f)
//    val ratio = 0.2f
    // ponytail: widgets get no smooth animation, so each step is a full RemoteViews update. More frames make it smoother but cost more.
    var shown by remember { mutableFloatStateOf(ratio) }
    LaunchedEffect(ratio) {
        val from = shown
        if (from == ratio) return@LaunchedEffect
        for (i in 1..FILL_FRAMES) {
            delay(100.milliseconds)
            shown = from + (ratio - from) * i / FILL_FRAMES
        }
    }
    @SuppressLint("RestrictedApi") // Launcher resolves the resource, so dynamic colors follow the wallpaper.
    val textColor = ColorProvider(R.color.widget_steps_text)
    val label = TextStyle(color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val compact = CompactDecimalFormat.getInstance(
        LocalContext.current.resources.configuration.locales[0],
        CompactDecimalFormat.CompactStyle.SHORT,
    )
    val barHeight = LocalSize.current.height - BORDER * 2

    // Fill grows from the bottom. Rounded track clips the fill, so the fill keeps a straight top edge, like the system battery widget.
    Box(GlanceModifier.fillMaxSize().padding(BORDER)) {
        Box(
            GlanceModifier
                .fillMaxSize()
                .background(R.color.widget_steps_track)
                .cornerRadius(android.R.dimen.system_app_widget_inner_radius),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (shown > 0f) {
                Box(
                    GlanceModifier
                        .fillMaxWidth()
                        .height(barHeight * shown)
                        .background(R.color.widget_steps_fill)
                ) {}
            }
            Image(
                provider = ImageProvider(R.drawable.ic_steps),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp).padding(bottom = 10.dp),
                colorFilter = ColorFilter.tint(textColor),
            )
            Column(
                GlanceModifier.fillMaxSize().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) CircularProgressIndicator(GlanceModifier.size(16.dp), color = textColor)
                else Text(compact.format(state.today), style = label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Message(text: String, refreshing: Boolean) {
    Box(GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
        if (refreshing) CircularProgressIndicator(GlanceModifier.size(16.dp), color = GlanceTheme.colors.onSurface)
        else Text(
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
        Content(StepsState.Ready(today = 6_400, goal = 10_000, updatedAt = LocalTime.NOON), refreshing = false) {}
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
        GlanceAppWidgetManager(applicationContext).getGlanceIds(StepsWidget::class.java).forEach { id ->
            updateAppWidgetState(applicationContext, id) { it[WORKER_TICK] = System.currentTimeMillis() }
        }
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
