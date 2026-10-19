package com.shelf.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.shelf.launcher.ui.ShelfHome

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val apps by viewModel.apps.collectAsState()
            val homePresses by viewModel.homePresses.collectAsState()
            ShelfHome(
                apps = apps,
                homePresses = homePresses,
                onLaunch = viewModel::launch,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }
}
