package com.v.island

import android.app.PendingIntent
import org.json.JSONArray
import org.json.JSONObject








object NotificationLog {

    
    private const val LIMIT = 20

    private class Entry(val key: String, val payload: JSONObject, val intent: PendingIntent?)

    private val entries = ArrayDeque<Entry>()

    
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

    
    fun lastOf(styleKey: String): JSONObject? =
        entries.firstOrNull { it.payload.optString("app") == styleKey }?.payload

    fun open(key: String): Boolean {
        val intent = entries.firstOrNull { it.key == key }?.intent ?: return false
        
        
        return runCatching { intent.send() }.isSuccess
    }

    fun forget(key: String) {
        entries.removeAll { it.key == key }
    }
}
