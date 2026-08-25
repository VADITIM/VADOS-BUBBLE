package com.v.island

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/** Thin front door to a shell-UID command runner, bound lazily and never assumed present. */
object ShizukuShell {

    private const val PERMISSION_REQUEST = 4711

    private var service: IShellService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShellService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** True only when Shizuku is running, has granted us access, and the runner is bound. */
    val isReady: Boolean get() = service != null

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() = runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }

    fun bind(context: Context) {
        if (service != null || !isRunning() || !hasPermission()) return
        val arguments = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell")
            .version(1)
        runCatching { Shizuku.bindUserService(arguments, connection) }
    }

    /** Null means the command could not be run at all, which is not the same as empty output. */
    fun run(command: String): String? =
        service?.let { runCatching { it.execute(command) }.getOrNull() }
}
