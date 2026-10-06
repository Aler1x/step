package dev.alerix.step

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val GOAL_STEP = 500
private const val GOAL_MIN = 1_000
private const val GOAL_MAX = 50_000
private val PRESETS = listOf(6_000, 8_000, 10_000, 12_000)

private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val TabularNums = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun App(today: Long?, goal: Int, onGoalChange: (Int) -> Unit) {
    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProgressRing(today, goal)
                }
                GoalPicker(goal, onGoalChange)
            }
        }
    }
}

@Composable
private fun ProgressRing(today: Long?, goal: Int) {
    val progress by animateFloatAsState(
        targetValue = ((today ?: 0L).toFloat() / goal).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = EaseOut),
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawBehind {
                val stroke = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
                val topLeft = Offset(stroke.width / 2, stroke.width / 2)
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
                if (progress > 0f) drawArc(fill, -90f, 360f * progress, false, topLeft, arcSize, style = stroke)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                today?.let(::grouped) ?: "—",
                style = MaterialTheme.typography.displayLarge.merge(TabularNums),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    today == null -> "No step data"
                    today >= goal -> "Goal reached"
                    else -> "of ${grouped(goal.toLong())} steps"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalPicker(goal: Int, onGoalChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    fun set(value: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onGoalChange(value.coerceIn(GOAL_MIN, GOAL_MAX))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Daily goal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Snap to the step grid, so an old goal like 7,234 goes to 7,000 / 7,500.
                FilledTonalIconButton(
                    onClick = { set((goal - 1) / GOAL_STEP * GOAL_STEP) },
                    enabled = goal > GOAL_MIN,
                    modifier = Modifier.size(56.dp),
                ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                Text(
                    grouped(goal.toLong()),
                    style = MaterialTheme.typography.headlineLarge.merge(TabularNums),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { set((goal / GOAL_STEP + 1) * GOAL_STEP) },
                    enabled = goal < GOAL_MAX,
                    modifier = Modifier.size(56.dp),
                ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = goal == preset,
                        onClick = { set(preset) },
                        label = { Text("${preset / 1_000}k") },
                    )
                }
            }
        }
    }
}

// ponytail: fixed comma grouping, switch to a locale formatter if the app gets translated
private fun grouped(n: Long) = n.toString().reversed().chunked(3).joinToString(",").reversed()
