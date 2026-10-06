package dev.alerix.step

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.glance.appwidget.updateAll
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import dev.alerix.step.widgets.Goal
import dev.alerix.step.widgets.StepsRepository
import dev.alerix.step.widgets.StepsWidget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestStepsPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            lifecycleScope.launch { StepsWidget().updateAll(this@MainActivity) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            var goal by remember { mutableIntStateOf(Goal.get(this@MainActivity)) }

            App(
                goal = goal,
                onGoalChange = { new ->
                    goal = new                                   // 1. redraw the app screen
                    Goal.set(this@MainActivity, new)             // 2. save to disk
                    lifecycleScope.launch {                      // 3. redraw the widget
                        StepsWidget().updateAll(this@MainActivity)
                    }
                },
            )
        }

        lifecycleScope.launch {
            if (StepsRepository.isAvailable(this@MainActivity) && !StepsRepository.hasPermissions(this@MainActivity)) {
                requestStepsPermissions.launch(StepsRepository.permissions(this@MainActivity))
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        goal = 1000,
        onGoalChange = {},
    )
}
