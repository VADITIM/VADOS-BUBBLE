package com.v.island




















object StatusBarPolicy {

    const val HIDDEN = "hidden"
    const val SHOWING = "showing"
    const val UNAVAILABLE = "unavailable"

    private const val SETTING = "policy_control"

    
    private const val IMMERSIVE = "immersive.status=*"

    fun read(): String {
        val value = ShizukuShell.run("settings get global $SETTING")?.trim() ?: return UNAVAILABLE
        return if (value.contains("immersive.status") || value.contains("immersive.full")) HIDDEN
        else SHOWING
    }

    fun set(isHidden: Boolean) {
        
        
        ShizukuShell.run("settings put global $SETTING ${if (isHidden) IMMERSIVE else "null*"}")
    }
}
