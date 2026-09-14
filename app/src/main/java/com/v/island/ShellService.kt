package com.v.island

import android.net.IIntResultListener
import android.net.ITetheringConnector
import android.net.TetheringRequestParcel
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit






class ShellService : IShellService.Stub() {

    override fun execute(command: String): String {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        process.waitFor()
        return output
    }

    // The hotspot tile is SystemUI's own rather than a TileService, so `cmd statusbar click-tile` never reached it, and `cmd wifi start-softap` is gated on root rather than on a permission. This process is the one place left that can do it: it runs as shell, which holds TETHER_PRIVILEGED, and the tethering binder takes the call straight.
    override fun setHotspot(isOn: Boolean): Boolean {
        val connector = ITetheringConnector.Stub.asInterface(
            SystemServiceHelper.getSystemService(TETHERING_SERVICE)
        ) ?: return false
        val answered = CountDownLatch(1)
        var result = TETHER_ERROR_UNKNOWN
        val listener = object : IIntResultListener.Stub() {
            override fun onResult(resultCode: Int) {
                result = resultCode
                answered.countDown()
            }
        }
        if (isOn) {
            val request = TetheringRequestParcel(
                requestType = REQUEST_TYPE_EXPLICIT,
                tetheringType = TETHERING_WIFI,
                exemptFromEntitlementCheck = false,
                showProvisioningUi = true,
                connectivityScope = CONNECTIVITY_SCOPE_GLOBAL,
                uid = UID_NONE
            )
            connector.startTethering(request, CALLER_PACKAGE, null, listener)
        } else {
            connector.stopTethering(TETHERING_WIFI, CALLER_PACKAGE, null, listener)
        }
        answered.await(ANSWER_WAIT_SECONDS, TimeUnit.SECONDS)
        return result == TETHER_ERROR_NO_ERROR
    }

    private companion object {
        const val TETHERING_SERVICE = "tethering"
        
        const val CALLER_PACKAGE = "com.android.shell"
        const val TETHERING_WIFI = 0
        const val REQUEST_TYPE_EXPLICIT = 0
        const val CONNECTIVITY_SCOPE_GLOBAL = 1
        const val UID_NONE = -1
        const val TETHER_ERROR_NO_ERROR = 0
        const val TETHER_ERROR_UNKNOWN = -1
        
        const val ANSWER_WAIT_SECONDS = 6L
    }
}
