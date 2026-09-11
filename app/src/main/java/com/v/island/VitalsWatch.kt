package com.v.island

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject

// The dashboard's vitals card, read fresh on every requestVitals() rather than watched: neither
// reading changes often enough to be worth a broadcast receiver, and the panel that shows them
// is only ever open for a few seconds at a time.
object VitalsWatch {

    private const val GIGABYTE = 1024.0 * 1024.0 * 1024.0

    fun read(context: Context): JSONObject {
        val json = JSONObject()
        readStorage()?.let { json.put("storage", it) }
        readRam(context)?.let { json.put("ram", it) }
        return json
    }

    private fun readStorage(): JSONObject? = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        if (total <= 0) null else usage(total - free, total)
    }.getOrNull()

    private fun readRam(context: Context): JSONObject? = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        if (info.totalMem <= 0) null else usage(info.totalMem - info.availMem, info.totalMem)
    }.getOrNull()

    private fun usage(used: Long, total: Long): JSONObject = JSONObject()
        .put("usedGigabytes", used / GIGABYTE)
        .put("totalGigabytes", total / GIGABYTE)
        .put("percent", 100 * used / total)
}
