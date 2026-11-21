package com.jarvisai.assistant

import android.app.Application
import com.jarvisai.assistant.crash.CrashHandler
import com.jarvisai.assistant.data.ssh.SshSessionFactory

/**
 * Custom Application entry point installing the process-isolated [CrashHandler]
 * and warming up SSH/PC-Connect repositories.
 */
class AgentraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        if (CrashHandler.isCrashProcess(this)) {
            return
        }
        SshSessionFactory.init(this)
    }
}
