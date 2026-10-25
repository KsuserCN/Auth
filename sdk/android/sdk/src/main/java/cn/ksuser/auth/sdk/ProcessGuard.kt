package cn.ksuser.auth.sdk

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

internal object ProcessGuard {
    fun requireDefaultProcess(context: Context) {
        val actual = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            manager?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
                ?: runCatching {
                    File("/proc/self/cmdline").inputStream().use { stream ->
                        val bytes = ByteArray(256)
                        val count = stream.read(bytes)
                        if (count <= 0) null else String(bytes, 0, count, Charsets.UTF_8).substringBefore('\u0000')
                    }
                }.getOrNull()
        }
        requireMatch(context.applicationInfo.processName, actual)
    }

    fun requireMatch(expected: String?, actual: String?) {
        if (expected.isNullOrBlank() || actual.isNullOrBlank() || expected != actual) {
            throw KsuserAuthException(KsuserAuthErrorCode.INVALID_CONFIGURATION)
        }
    }
}
