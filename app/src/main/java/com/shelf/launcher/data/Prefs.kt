package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClockMode(val label: String) { SYSTEM("לפי המערכת"), H24("24 שעות"), H12("12 שעות") }

enum class GlassLevel(val label: String, val alpha: Float) {
    LIGHT("עדינה", 0.08f), NORMAL("רגילה", 0.13f), STRONG("חזקה", 0.22f)
}

enum class BackdropStyle(val label: String) { BLURRED("מטושטש"), SHARP("חד"), COLOR("צבע בלבד") }

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
            .apply()
    }

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
