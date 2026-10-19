package com.shelf.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shelf.launcher.data.AppRepository
import com.shelf.launcher.data.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    /** Bumped every time the Home button is pressed while we're already showing. */
    private val _homePresses = MutableStateFlow(0)
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    private var loadJob: Job? = null

    /** Re-reads installed apps; called whenever the launcher comes back to the front. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { repository.load() }
            // Skip the update when nothing was installed or removed, so the
            // screen doesn't flash or lose focus every time you come back.
            if (fresh.map { it.key } != _apps.value.map { it.key }) {
                _apps.value = fresh
            }
        }
    }

    fun onHomePressed() {
        _homePresses.value += 1
    }

    fun launch(app: TvApp) {
        runCatching { getApplication<Application>().startActivity(app.intent) }
    }
}
