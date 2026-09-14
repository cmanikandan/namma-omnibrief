package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AppDestination
import com.example.ui.components.OmniNavBar
import com.example.ui.components.OmniTopBar
import com.example.ui.screens.ArticleToXScreen
import com.example.ui.screens.ConferenceReporterScreen
import com.example.ui.screens.HeadlinesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      // Hoisted so the theme can react to the font-size preference without a restart.
      val appViewModel: MainViewModel = viewModel()
      val scale by appViewModel.fontScale.collectAsStateWithLifecycle()

      // Refresh the Today feed when the app comes back to the foreground. The ViewModel's hourly
      // timer only ticks while the process is alive, so without this, reopening the app after it
      // had been swapped out would show whatever was last fetched. The ViewModel decides whether
      // the feed is actually stale enough to warrant a request.
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appViewModel.onAppResumed()
      }

      MyApplicationTheme(fontScale = scale) {
        OmniBriefApp(appViewModel)
      }
    }
  }
}

@Composable
fun OmniBriefApp(viewModel: MainViewModel = viewModel()) {
  val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
  val storedCount = viewModel.storedItems.collectAsStateWithLifecycle().value.size

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      OmniTopBar(
        currentModel = viewModel.prefs.geminiModel,
        onSettingsClick = { viewModel.navigateTo(AppDestination.SETTINGS) }
      )
    },
    bottomBar = {
      OmniNavBar(
        selectedDestination = currentDestination,
        onDestinationSelected = { viewModel.navigateTo(it) },
        historyCount = storedCount
      )
    }
  ) { innerPadding ->
    when (currentDestination) {
      AppDestination.HEADLINES -> {
        HeadlinesScreen(
          viewModel = viewModel,
          modifier = Modifier.padding(innerPadding)
        )
      }
      AppDestination.ARTICLE_TO_X -> {
        ArticleToXScreen(
          viewModel = viewModel,
          modifier = Modifier.padding(innerPadding)
        )
      }
      AppDestination.CONFERENCE -> {
        ConferenceReporterScreen(
          viewModel = viewModel,
          modifier = Modifier.padding(innerPadding)
        )
      }
      AppDestination.HISTORY -> {
        HistoryScreen(
          viewModel = viewModel,
          modifier = Modifier.padding(innerPadding)
        )
      }
      AppDestination.SETTINGS -> {
        SettingsScreen(
          viewModel = viewModel,
          modifier = Modifier.padding(innerPadding)
        )
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}
