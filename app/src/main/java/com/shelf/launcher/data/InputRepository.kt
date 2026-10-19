package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

const val SOURCE_PREFIX = "src:"

/** An HDMI port on the TV. */
data class TvSource(val id: String, val label: String, val intent: Intent)

/** Reads the TV's HDMI inputs. Boxes without HDMI inputs simply return an empty list. */
class InputRepository(private val context: Context) {

    fun load(): List<TvSource> = runCatching {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()
        manager.tvInputList
            // Physical HDMI ports only, not the devices plugged into them (those have a parent).
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
            .map { info ->
                val label = (info.loadCustomLabel(context) ?: info.loadLabel(context)).toString()
                TvSource(
                    id = info.id,
                    label = label,
                    intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            .sortedBy { it.label }
    }.getOrDefault(emptyList())
}
