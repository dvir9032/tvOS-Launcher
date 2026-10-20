package com.shelf.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.shelf.launcher.data.FOLDER_PREFIX
import com.shelf.launcher.home.HomeButtonService
import com.shelf.launcher.home.TvPower
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

    /** Everything Control Center shows. Re-read when it opens. */
    data class Controls(
        val volume: Int = 0,
        val maxVolume: Int = 100,
        val muted: Boolean = false,
        val network: Network = Network.UNKNOWN,
        val sleepMinutes: Int = 0,
    )

    enum class Network { WIFI, ETHERNET, OFFLINE, UNKNOWN }

    private val _controls = MutableStateFlow(Controls())
    val controls: StateFlow<Controls> = _controls.asStateFlow()

    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
        if (!app.isFolder && app.packageName != null) {
            prefsStore.update { p -> p.copy(recent = (listOf(app.key) + (p.recent - app.key)).take(RECENT_MAX)) }
        }
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

    // ---------------------------------------------------------------- Control Center

    fun refreshControls() {
        val app = getApplication<Application>()
        _controls.value = Controls(
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            muted = runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false),
            network = network(app),
            sleepMinutes = TvPower.sleepMinutesLeft(app),
        )
    }

    private fun network(app: Application): Network = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return Network.OFFLINE
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Network.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Network.WIFI
            else -> Network.UNKNOWN
        }
    }.getOrDefault(Network.UNKNOWN)

    fun changeVolume(step: Int) {
        val c = _controls.value
        val next = (c.volume + step).coerceIn(0, c.maxVolume)
        runCatching {
            if (c.muted) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        }
        refreshControls()
    }

    fun toggleMute() {
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
        refreshControls()
    }

    /** Next sleep timer choice. Returns false if Shelf isn't allowed to turn the screen off yet. */
    fun cycleSleepTimer(): Boolean {
        val app = getApplication<Application>()
        if (!TvPower.canTurnOff(app)) return false
        val current = TvPower.sleepMinutesLeft(app)
        val choices = TvPower.SLEEP_CHOICES
        val next = choices.firstOrNull { it > current } ?: 0
        TvPower.setSleepTimer(app, next)
        refreshControls()
        return true
    }

    /** False if Shelf isn't allowed to turn the screen off yet. */
    fun screenOff(): Boolean = TvPower.screenOff(getApplication())

    fun canTurnOff(): Boolean = TvPower.canTurnOff(getApplication())

    /** False when the Home button service is off (it's what opens the power menu). */
    fun powerMenu(): Boolean = TvPower.powerMenu()

    fun openPictureSettings() = openSettings(Settings.ACTION_DISPLAY_SETTINGS)

    fun openWifiSettings() = openSettings(Settings.ACTION_WIFI_SETTINGS)

    private fun openSettings(action: String) {
        val app = getApplication<Application>()
        val ok = runCatching { app.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) openSystemSettings()
    }

    // ---------------------------------------------------------------- Folders

    /** Puts [appKey] in folder [folderId]. A new folder takes the app's place on the home screen. */
    fun addToFolder(appKey: String, folderId: String, dockKeys: List<String>) = prefsStore.update { p ->
        val folders = p.folders.mapValues { it.value - appKey }.filterValues { it.isNotEmpty() }.toMutableMap()
        folders[folderId] = folders[folderId].orEmpty() + appKey
        val folderKey = FOLDER_PREFIX + folderId
        val order = dockKeys.toMutableList()
        val at = order.indexOf(appKey)
        when {
            folderKey !in order && at >= 0 -> order[at] = folderKey
            folderKey !in order -> order.add(folderKey)
            at >= 0 -> order.removeAt(at)
        }
        p.copy(folders = folders, order = order + p.hidden)
    }

    /** Takes [appKey] out of its folder; it goes right after the folder (or in its place if it was the last one). */
    fun removeFromFolder(appKey: String, dockKeys: List<String>) = prefsStore.update { p ->
        val folderId = p.folders.entries.firstOrNull { appKey in it.value }?.key ?: return@update p
        val left = p.folders.getValue(folderId) - appKey
        val folderKey = FOLDER_PREFIX + folderId
        val order = dockKeys.toMutableList()
        val at = order.indexOf(folderKey)
        val folders = p.folders.toMutableMap()
        if (left.isEmpty()) {
            folders.remove(folderId)
            if (at >= 0) order[at] = appKey else order.add(appKey)
        } else {
            folders[folderId] = left
            if (at >= 0) order.add(at + 1, appKey) else order.add(appKey)
        }
        p.copy(folders = folders, order = order + p.hidden)
    }

    /** Puts all apps of the folder back on the home screen, where the folder was. */
    fun ungroup(folderId: String, dockKeys: List<String>) = prefsStore.update { p ->
        val apps = p.folders[folderId].orEmpty()
        val folderKey = FOLDER_PREFIX + folderId
        val order = dockKeys.toMutableList()
        val at = order.indexOf(folderKey)
        if (at >= 0) {
            order.removeAt(at)
            order.addAll(at, apps)
        } else {
            order.addAll(apps)
        }
        p.copy(folders = p.folders - folderId, folderNames = p.folderNames - folderId, order = order + p.hidden)
    }

    fun renameFolder(folderId: String, name: String) = prefsStore.update { p ->
        val clean = name.trim()
        p.copy(folderNames = if (clean.isEmpty()) p.folderNames - folderId else p.folderNames + (folderId to clean))
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
        const val RECENT_MAX = 8
    }
}
