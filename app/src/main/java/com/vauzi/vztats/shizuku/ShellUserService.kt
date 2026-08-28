package com.vauzi.vztats.shizuku

import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku-spawned process with shell (UID 2000) privileges, so the
 * commands it runs can read SurfaceFlinger frame timings that a normal app can't.
 */
class ShellUserService : IUserService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    } catch (t: Throwable) {
        "ERROR: ${t.message}"
    }
}
