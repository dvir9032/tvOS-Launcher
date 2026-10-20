package com.shelf.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.shelf.launcher.data.ContentRepository
import com.shelf.launcher.ui.ShelfHome

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    // "Allow Shelf to see TV listings?": lets Shelf show content from apps on the home screen.
    private val askContent = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }
}
