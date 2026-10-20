package com.shelf.launcher.data

import android.content.Context
import androidx.annotation.StringRes
import com.shelf.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ClockMode(@StringRes val label: Int) {
    SYSTEM(R.string.clock_system), H24(R.string.clock_24), H12(R.string.clock_12)
}

enum class GlassLevel(@StringRes val label: Int, val alpha: Float) {
    LIGHT(R.string.glass_light, 0.08f), NORMAL(R.string.glass_normal, 0.13f), STRONG(R.string.glass_strong, 0.22f)
}

enum class BackdropStyle(@StringRes val label: Int) {
    BLURRED(R.string.backdrop_blurred), SHARP(R.string.backdrop_sharp), COLOR(R.string.backdrop_color)
}

/** Dark, Light, or Auto (dark from 7 pm to 6 am). */
enum class ThemeMode(@StringRes val label: Int) {
    DARK(R.string.theme_dark), LIGHT(R.string.theme_light), AUTO(R.string.theme_auto)
}

inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}

data class ShelfPrefs(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val columns: Int = 6,
    val clock: ClockMode = ClockMode.SYSTEM,
    val glass: GlassLevel = GlassLevel.NORMAL,
    val backdrop: BackdropStyle = BackdropStyle.BLURRED,
    val showInputs: Boolean = true,
    val showContent: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    /** soft tick when focus moves, like tvOS */
    val focusSound: Boolean = true,
    /** folder id → app keys inside, in order */
    val folders: Map<String, List<String>> = emptyMap(),
    /** folder id → name the user typed (otherwise the preset name) */
    val folderNames: Map<String, String> = emptyMap(),
    /** apps opened from Shelf, newest first */
    val recent: List<String> = emptyList(),
)

/** Saved launcher settings. Reads once, then every change is written straight away. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ShelfPrefs> = _state.asStateFlow()

    fun update(transform: (ShelfPrefs) -> ShelfPrefs) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read() = ShelfPrefs(
        order = sp.getString("order", "").orEmpty().split(',').filter { it.isNotBlank() },
        hidden = sp.getStringSet("hidden", emptySet()).orEmpty().toSet(),
        columns = sp.getInt("columns", 6).coerceIn(5, 7),
        clock = enumOr(sp.getString("clock", null), ClockMode.SYSTEM),
        glass = enumOr(sp.getString("glass", null), GlassLevel.NORMAL),
        backdrop = enumOr(sp.getString("backdrop", null), BackdropStyle.BLURRED),
        showInputs = sp.getBoolean("showInputs", true),
        showContent = sp.getBoolean("showContent", true),
        theme = enumOr(sp.getString("theme", null), ThemeMode.DARK),
        focusSound = sp.getBoolean("focusSound", true),
        folders = readFolders(sp.getString("folders", null)),
        folderNames = readNames(sp.getString("folderNames", null)),
        recent = sp.getString("recent", "").orEmpty().split(',').filter { it.isNotBlank() },
    )

    private fun write(p: ShelfPrefs) {
        sp.edit()
            .putString("order", p.order.joinToString(","))
            .putStringSet("hidden", HashSet(p.hidden))
            .putInt("columns", p.columns)
            .putString("clock", p.clock.name)
            .putString("glass", p.glass.name)
            .putString("backdrop", p.backdrop.name)
            .putBoolean("showInputs", p.showInputs)
            .putBoolean("showContent", p.showContent)
            .putString("theme", p.theme.name)
            .putBoolean("focusSound", p.focusSound)
            .putString("folders", JSONObject(p.folders.mapValues { JSONArray(it.value) }).toString())
            .putString("folderNames", JSONObject(p.folderNames).toString())
            .putString("recent", p.recent.joinToString(","))
            .apply()
    }

    private fun readFolders(json: String?): Map<String, List<String>> = runCatching {
        val obj = JSONObject(json ?: return emptyMap())
        val out = LinkedHashMap<String, List<String>>()
        for (id in obj.keys()) {
            val array = obj.getJSONArray(id)
            out[id] = (0 until array.length()).map { array.getString(it) }
        }
        out
    }.getOrDefault(emptyMap())

    private fun readNames(json: String?): Map<String, String> = runCatching {
        val obj = JSONObject(json ?: return emptyMap())
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }.getOrDefault(emptyMap())

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}

/** Saved order first; apps that are new since then go at the end (they arrive sorted A–Z). */
fun arrange(apps: List<TvApp>, order: List<String>): List<TvApp> {
    if (order.isEmpty()) return apps
    val byKey = apps.associateBy { it.key }
    val ordered = order.mapNotNull { byKey[it] }
    val known = order.toSet()
    return ordered + apps.filter { it.key !in known }
}
