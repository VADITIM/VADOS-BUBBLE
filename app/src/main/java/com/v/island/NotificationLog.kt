package com.v.island

import android.app.PendingIntent
import org.json.JSONArray
import org.json.JSONObject

/**
 * The notifications the bubble is standing in for. The payload travels to the
 * WebView as JSON, but a [PendingIntent] cannot, so it is parked here under the
 * same key and fired when the bubble asks to open one.
 *
 * Memory only: this is what the bubble is showing right now, not an archive.
 */
object NotificationLog {

    /** Enough to fill the held-open list; older than that, the shade is the archive. */
    private const val LIMIT = 20

    private class Entry(val key: String, val payload: JSONObject, val intent: PendingIntent?)

    private val entries = ArrayDeque<Entry>()

    /** Reset when the list is opened or a notification is tapped, never on dismissal. */
    var unread = 0
        private set

    fun add(key: String, payload: JSONObject, intent: PendingIntent?) {
        entries.removeAll { it.key == key }
        entries.addFirst(Entry(key, payload, intent))
        while (entries.size > LIMIT) entries.removeLast()
        unread++
    }

    fun markRead() {
        unread = 0
    }

    fun history(): JSONArray = JSONArray().apply { entries.forEach { put(it.payload) } }

    /** The most recent notification from one styled app, for the debug replay buttons. */
    fun lastOf(styleKey: String): JSONObject? =
        entries.firstOrNull { it.payload.optString("app") == styleKey }?.payload

    fun open(key: String): Boolean {
        val intent = entries.firstOrNull { it.key == key }?.intent ?: return false
        // A cancelled intent is the normal case once the posting app has moved on,
        // and it must not take the bubble down with it.
        return runCatching { intent.send() }.isSuccess
    }

    fun forget(key: String) {
        entries.removeAll { it.key == key }
    }
}
