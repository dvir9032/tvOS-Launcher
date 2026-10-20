package com.shelf.launcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shelf.launcher.data.AppRepository
import com.shelf.launcher.data.ContentRepository
import com.shelf.launcher.data.ShelfItem
import com.shelf.launcher.data.InputRepository
import com.shelf.launcher.data.Prefs
import com.shelf.launcher.data.ShelfPrefs
import com.shelf.launcher.data.TvApp
import com.shelf.launcher.data.TvSource
import com.shelf.launcher.home.HomeButtonService
import com.shelf.launcher.update.UpdateState
import com.shelf.launcher.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)
    private val inputRepository = InputRepository(app)
    private val contentRepository = ContentRepository(app)
    private val updater = Updater(app)
    private val prefsStore = Prefs(app)

    val prefs: StateFlow<ShelfPrefs> = prefsStore.state

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    /** Content apps put on the home screen, per package. Empty without permission. */
    private val _content = MutableStateFlow<Map<String, List<ShelfItem>>>(emptyMap())
    val content: StateFlow<Map<String, List<ShelfItem>>> = _content.asStateFlow()

    private val _contentAllowed = MutableStateFlow(contentRepository.hasPermission())
    val contentAllowed: StateFlow<Boolean> = _contentAllowed.asStateFlow()

    private val _sources = MutableStateFlow<List<TvSource>>(emptyList())
    val sources: StateFlow<List<TvSource>> = _sources.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** Bumped every time the Home button is pressed while we're already showing. */
    private val _homePresses = MutableStateFlow(0)
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    /** True when the Home button still opens the TV's own launcher. */
    private val _homeSetupNeeded = MutableStateFlow(false)
    val homeSetupNeeded: StateFlow<Boolean> = _homeSetupNeeded.asStateFlow()

    private val _homeServiceOn = MutableStateFlow(false)
    val homeServiceOn: StateFlow<Boolean> = _homeServiceOn.asStateFlow()

    val versionName: String = BuildConfig.VERSION_NAME

    private var loadJob: Job? = null
    private var lastUpdateCheck = 0L

    /** Called whenever the launcher comes back to the front. */
    init {
        // The pill disappears the moment the service connects, not only on the next resume.
        viewModelScope.launch { HomeButtonService.running.collect { checkHomeSetup() } }
    }

    private fun checkHomeSetup() {
        val app = getApplication<Application>()
        val serviceOn = HomeButtonService.running.value || HomeButtonService.isEnabled(app)
        _homeServiceOn.value = serviceOn
        _homeSetupNeeded.value = !serviceOn && !HomeButtonService.isDefaultHome(app)
    }

    fun refresh() {
        checkHomeSetup()

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { repository.load() }
            if (fresh.map { it.key } != _apps.value.map { it.key }) _apps.value = fresh
            _sources.value = withContext(Dispatchers.IO) { inputRepository.load() }
            _contentAllowed.value = contentRepository.hasPermission()
            _content.value = withContext(Dispatchers.IO) { contentRepository.load() }
        }
        // Coming back from a cancelled install: forget it and look again.
        if (_update.value is UpdateState.Installing || _update.value is UpdateState.Failed) {
            _update.value = UpdateState.Idle
            lastUpdateCheck = 0L
        }
        if (System.currentTimeMillis() - lastUpdateCheck > SIX_HOURS) checkForUpdate()
    }

    fun checkForUpdate() {
        val now = _update.value
        if (now is UpdateState.Checking || now is UpdateState.Downloading) return
        lastUpdateCheck = System.currentTimeMillis()
        viewModelScope.launch {
            _update.value = UpdateState.Checking
            _update.value = updater.check()
        }
    }

    fun installUpdate() {
        val available = _update.value as? UpdateState.Available ?: return
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(0f)
            _update.value = updater.downloadAndInstall(available.url) { p ->
                _update.value = UpdateState.Downloading(p)
            }
        }
    }

    fun onUpdateClicked() {
        if (_update.value is UpdateState.Available) installUpdate() else checkForUpdate()
    }

    fun onHomePressed() {
        _homePresses.value += 1
    }

    fun updatePrefs(transform: (ShelfPrefs) -> ShelfPrefs) = prefsStore.update(transform)

    fun hide(key: String) = prefsStore.update { it.copy(hidden = it.hidden + key) }

    fun unhide(key: String) = prefsStore.update { it.copy(hidden = it.hidden - key) }

    fun saveOrder(keys: List<String>) = prefsStore.update { it.copy(order = keys) }

    fun resetOrder() = prefsStore.update { it.copy(order = emptyList()) }

    fun launch(app: TvApp) {
        HomeButtonService.stayQuiet()
        runCatching { getApplication<Application>().startActivity(app.intent) }
    }

    fun launchItem(item: ShelfItem) {
        val uri = item.intentUri ?: return
        HomeButtonService.stayQuiet()
        runCatching {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }
    }

    fun launchSource(source: TvSource) {
        HomeButtonService.stayQuiet()
        runCatching { getApplication<Application>().startActivity(source.intent) }
    }

    fun openAppInfo(packageName: String) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens the system's "Uninstall this app?" screen. The list refreshes when we come back. */
    fun uninstall(packageName: String) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens the TV's accessibility settings, where the Home button service is switched on. */
    fun openHomeSetup() {
        val app = getApplication<Application>()
        val opened = runCatching {
            app.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) openSystemSettings()
    }

    fun openSystemSettings() {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        const val SIX_HOURS = 6 * 60 * 60 * 1000L
    }
}
