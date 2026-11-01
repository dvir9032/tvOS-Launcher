package com.shelf.launcher

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import com.shelf.launcher.data.ArtLoader
import com.shelf.launcher.data.ContentRepository
import com.shelf.launcher.home.HomeButtonService
import com.shelf.launcher.ui.ShelfHome

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    // "Allow Shelf to see TV listings?": lets Shelf show content from apps on the home screen.
    private val askContent = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (HomeButtonService.isStale(intent)) moveTaskToBack(true)
        setContent { ShelfHome(viewModel, onAllowContent = ::requestContent) }

        // Ask once, on first launch. After that only from Settings → Appearance.
        val prefs = getSharedPreferences("shelf", Context.MODE_PRIVATE)
        if (!ContentRepository(this).hasPermission() && !prefs.getBoolean("askedContent", false)) {
            prefs.edit().putBoolean("askedContent", true).apply()
            requestContent()
        }
    }

    private fun requestContent() {
        runCatching { askContent.launch(ContentRepository.PERMISSION) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /**
     * While an app is playing, Shelf waits in the background with its artwork in memory. When the TV
     * runs short (common with 1 GB of RAM), give the pictures back so the app in front isn't killed.
     * Never while Shelf is on screen: the pictures would blink out.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            ArtLoader.trim()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A late "open Shelf" from the Home button service: an app was opened since, keep it in front.
        if (HomeButtonService.isStale(intent)) {
            moveTaskToBack(true)
            return
        }
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }
}
