package com.v.island

import org.json.JSONObject













object SystemToggles {

    



    private const val READ_ALL =
        "settings get global wifi_on;" +
            "settings get global bluetooth_on;" +
            "svc usb getFunctions;" +
            "settings get global zen_mode;" +
            "settings get secure reduce_bright_colors_activated;" +
            "settings get system accelerometer_rotation;" +
            "settings get global low_power;" +
            "settings get global mobile_data;" +
            "settings get system screen_brightness;" +
            "settings get global airplane_mode_on;" +
            
            
            "settings get secure location_mode;" +
            
            
            "cmd media_session volume --stream 3 --get 2>&1 | grep -m1 'volume is'"

    





    private val brightnessMax: Int by lazy {
        val resources = android.content.res.Resources.getSystem()
        val id = resources.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        if (id == 0) 0 else runCatching { resources.getInteger(id) }.getOrDefault(0)
    }

    
    private fun volumeOf(line: String): Int {
        val index = line.substringAfter("volume is ", "").substringBefore(' ').toIntOrNull() ?: return -1
        val top = line.substringAfter("..", "").substringBefore(']').toIntOrNull() ?: return -1
        if (top <= 0) return -1
        volumeTop = top
        return index * 100 / top
    }

    fun read(): JSONObject {
        val answer = JSONObject()
        
        
        val lines = ShizukuShell.run(READ_ALL)?.lines() ?: return answer
        fun line(index: Int) = lines.getOrNull(index)?.trim().orEmpty()
        answer.put("wifi", line(0) == "1")
        answer.put("bluetooth", line(1) == "1")
        answer.put("usb", line(2).contains("mtp"))
        
        
        answer.put("modus", line(3).toIntOrNull()?.let { it != 0 } ?: false)
        answer.put("dim", line(4) == "1")
        answer.put("rotate", line(5) == "1")
        answer.put("saver", line(6) == "1")
        answer.put("mobile", line(7) == "1")
        answer.put("mic", MicrophoneAccess.read() == MicrophoneAccess.ALLOWED)
        answer.put("plane", line(9) == "1")
        
        
        answer.put("gps", line(10).toIntOrNull()?.let { it != 0 } ?: false)
        
        
        
        
        
        
        val brightness = line(8).toIntOrNull() ?: -1
        if (brightness >= 0 && brightnessMax > 0) {
            answer.put("brightness", brightness * 100 / brightnessMax)
        }
        volumeOf(line(11)).takeIf { it >= 0 }?.let { answer.put("volume", it) }
        
        
        
        return answer
    }

    






    fun setLevel(name: String, percent: Int): Boolean {
        val share = percent.coerceIn(0, 100)
        val command = when (name) {
            
            
            "brightness" -> if (brightnessMax <= 0) return false else
                "settings put system screen_brightness_mode 0;" +
                    "settings put system screen_brightness " +
                    (share * brightnessMax / 100).coerceAtLeast(1)
            "volume" -> "cmd media_session volume --stream 3 --set " + volumeIndex(share)
            else -> return false
        }
        return ShizukuShell.run(command) != null
    }

    
    private fun volumeIndex(share: Int): Int {
        val top = volumeTop.takeIf { it > 0 } ?: return 0
        return share * top / 100
    }

    
    private var volumeTop = 0

    
    fun set(name: String, isOn: Boolean): Boolean {
        val one = if (isOn) "1" else "0"
        val command = when (name) {
            "wifi" -> "svc wifi " + if (isOn) "enable" else "disable"
            "bluetooth" -> "svc bluetooth " + if (isOn) "enable" else "disable"
            
            "usb" -> "svc usb setFunctions " + if (isOn) "mtp" else ""
            "mobile" -> "svc data " + if (isOn) "enable" else "disable"
            "modus" -> "cmd notification set_dnd " + if (isOn) "on" else "off"
            "dim" -> "settings put secure reduce_bright_colors_activated $one"
            "rotate" -> "settings put system accelerometer_rotation $one"
            "saver" -> "settings put global low_power $one"
            
            
            
            
            
            "plane" -> "cmd connectivity airplane-mode " + if (isOn) "enable" else "disable"
            "gps" -> "cmd location set-location-enabled " + if (isOn) "true" else "false"
            
            
            
            
            "hotspot" -> "cmd statusbar click-tile " +
                "com.android.systemui/com.android.systemui.qs.tiles.HotspotTile"
            
            
            "recording" -> "cmd statusbar click-tile " +
                "com.samsung.android.app.smartcapture/" +
                "com.samsung.android.app.smartcapture.screenrecorder.ScreenRecorderTileService"
            "mic" -> return MicrophoneAccess.set(isOn)
            else -> return false
        }
        return ShizukuShell.run(command) != null
    }
}
