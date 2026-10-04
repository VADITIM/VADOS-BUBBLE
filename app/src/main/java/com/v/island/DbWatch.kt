package com.v.island

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

object DbWatch {

    const val PACKAGE = "de.hafas.android.db"

    private val trainPattern =
        Regex("""\b(ICE|IC|EC|ECE|IRE|RE|RB|S|U|STR|FLX|NJ|EN|RJX?|TGV|Bus)\s?\d+[A-Z]?\b""")
    private val clockPattern = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val arrivalPattern =
        Regex("""\b(?:Ankunft|an|arr\.?|arrival)\s*:?\s*(\d{1,2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val delayPattern = Regex("""(?<![\w:])(\+\s?\d{1,3})(?:\s?min)?\b""")
    private val platformPattern =
        Regex("""\b(?:Gleis|Gl\.|Bstg\.?|Steig|Platform|Pl\.)\s*([0-9]+[A-Za-z]?(?:\s?[a-g]-[a-g])?)""", RegexOption.IGNORE_CASE)
    private val nextStopPattern =
        Regex("""(?:Nächster Halt|Next stop)\s*:?\s*(.+)""", RegexOption.IGNORE_CASE)
    private val exitPattern =
        Regex("""(?:Ausstieg(?:\s+in)?|Aussteigen(?:\s+in)?|Get off(?:\s+at)?|Exit(?:\s+at)?)\s*:?\s*(.+)""", RegexOption.IGNORE_CASE)
    private val routePattern = Regex("""(.+?)\s*(?:→|➔|->)\s*(.+)""")

    fun isTrip(statusBarNotification: StatusBarNotification): Boolean {
        if (statusBarNotification.packageName != PACKAGE) return false
        val notification = statusBarNotification.notification
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
        return notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }

    fun describe(context: Context, statusBarNotification: StatusBarNotification): JSONObject {
        val notification = statusBarNotification.notification
        val style = AppStyles.of(statusBarNotification.packageName)
        val lines = linesOf(context, notification)
        val joined = lines.joinToString("\n")

        val route = lines.firstNotNullOfOrNull { routePattern.find(it) }
        val arrival = arrivalPattern.find(joined)?.groupValues?.get(1)

        return JSONObject()
            .put("key", statusBarNotification.key)
            .put("app", style.key)
            .put("appName", style.label)
            .put("accent", style.accent)
            .put("train", trainPattern.find(joined)?.value ?: JSONObject.NULL)
            .put("origin", route?.groupValues?.get(1)?.let { trainPattern.replace(it, "").trim() }?.ifBlank { null } ?: JSONObject.NULL)
            .put("destination", route?.groupValues?.get(2)?.trim() ?: JSONObject.NULL)
            .put("nextStop", nextStopPattern.find(joined)?.groupValues?.get(1)?.trim() ?: JSONObject.NULL)
            .put("exitStop", exitPattern.find(joined)?.groupValues?.get(1)?.trim() ?: JSONObject.NULL)
            .put("arrival", arrival ?: JSONObject.NULL)
            .put("endsAt", endsAtByChronometer(notification) ?: arrival?.let(::endsAtByClock) ?: JSONObject.NULL)
            .put("delay", delayPattern.find(joined)?.groupValues?.get(1)?.replace(" ", "") ?: JSONObject.NULL)
            .put("platform", platformPattern.find(joined)?.groupValues?.get(1) ?: JSONObject.NULL)
            .put("lines", JSONArray(lines))
            .put("actions", actions(notification))
    }

    fun act(statusBarNotification: StatusBarNotification, index: Int) {
        val action = statusBarNotification.notification.actions?.getOrNull(index) ?: return
        runCatching { action.actionIntent.send() }
    }

    fun dump(context: Context, statusBarNotification: StatusBarNotification) {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val log = { line: String -> android.util.Log.d("IslandBubble", "db $line") }
        log("key=${statusBarNotification.key} flags=${notification.flags} category=${notification.category} channel=${notification.channelId} when=${notification.`when`} now=${System.currentTimeMillis()} contentIntent=${notification.contentIntent != null}")
        extras.keySet().sorted().forEach { key -> log("extra $key=${describeValue(extras.get(key))}") }
        notification.actions?.forEachIndexed { index, action -> log("action $index=${action.title}") }
        log("contentView=${notification.contentView != null} bigContentView=${notification.bigContentView != null} remoteTexts=${remoteTexts(context, notification)}")
    }

    private fun describeValue(value: Any?): String = when (value) {
        is Array<*> -> value.joinToString(" | ", "[", "]") { describeValue(it) }
        is Bundle -> value.keySet().joinToString(", ", "{", "}") { "$it=${describeValue(value.get(it))}" }
        else -> value.toString()
    }

    private fun linesOf(context: Context, notification: Notification): List<String> {
        val extras = notification.extras
        val fromExtras = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        ) + extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.split('\n').orEmpty() +
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
        val lines = fromExtras.map { it.toString().trim() }.filter { it.isNotBlank() }.distinct()
        return lines.ifEmpty { remoteTexts(context, notification) }
    }

    private fun remoteTexts(context: Context, notification: Notification): List<String> {
        val views = notification.bigContentView ?: notification.contentView ?: return emptyList()
        val root = runCatching { views.apply(context, FrameLayout(context)) }.getOrNull() ?: return emptyList()
        val texts = mutableListOf<String>()
        fun walk(view: View) {
            if (view is TextView && view.visibility == View.VISIBLE) {
                view.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(texts::add)
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(root)
        return texts.distinct()
    }

    private fun actions(notification: Notification): JSONArray = JSONArray().apply {
        notification.actions?.forEachIndexed { index, action ->
            put(JSONObject().put("index", index).put("title", action.title?.toString().orEmpty()))
        }
    }

    private fun endsAtByChronometer(notification: Notification): Long? {
        if (!notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)) return null
        return notification.`when`.takeIf { it > System.currentTimeMillis() }
    }

    private fun endsAtByClock(clock: String): Long? {
        val match = clockPattern.find(clock) ?: return null
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, match.groupValues[1].toInt())
            set(Calendar.MINUTE, match.groupValues[2].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis < System.currentTimeMillis() - 60_000L) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }
}
