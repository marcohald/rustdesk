package com.carriez.flutter_hbb

import android.content.Context
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtils {
    private const val LOG_TAG = "RootUtils"

    fun isRootEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(KEY_SHARED_PREFERENCES, FlutterActivity.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_ROOT_OPT, false)
    }

    fun setRootEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(KEY_SHARED_PREFERENCES, FlutterActivity.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_ROOT_OPT, enabled).apply()
    }

    fun execCmds(vararg commands: String): Pair<Boolean, String> {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes(cmd + "\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            val success = (exitCode == 0)
            Pair(success, output.toString().trim())
        } catch (e: Exception) {
            Log.e(LOG_TAG, "execCmds error", e)
            Pair(false, e.message ?: "Unknown error")
        }
    }

    fun checkRootAccess(): Pair<Boolean, String> {
        val (success, output) = execCmds("id")
        val isRoot = success && (output.contains("uid=0") || output.contains("root"))
        return Pair(isRoot, output)
    }

    fun grantPermissionsViaRoot(context: Context): Boolean {
        val pkgName = context.packageName
        val cmds = arrayOf(
            "appops set $pkgName PROJECT_MEDIA allow",
            "settings put secure enabled_accessibility_services $pkgName/$pkgName.InputService",
            "settings put secure accessibility_enabled 1",
            "appops set $pkgName SYSTEM_ALERT_WINDOW allow",
            "pm grant $pkgName android.permission.SYSTEM_ALERT_WINDOW"
        )
        val (success, _) = execCmds(*cmds)
        return success
    }

    fun wakeAndUnlockScreen(context: Context) {
        val cmds = arrayOf(
            "input keyevent 224", // KEYCODE_WAKEUP
            "wm dismiss-keyguard",
            "input swipe 500 1200 500 300 150"
        )
        execCmds(*cmds)
    }
}
