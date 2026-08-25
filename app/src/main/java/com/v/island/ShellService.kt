package com.v.island

import java.io.BufferedReader

/**
 * Runs inside the Shizuku process, so it holds the shell UID. Everything the app
 * cannot do itself — the microphone-access toggle lives behind signature-level
 * permissions no sideloaded app can hold — goes through here as a shell command.
 */
class ShellService : IShellService.Stub() {

    override fun execute(command: String): String {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        process.waitFor()
        return output
    }
}
