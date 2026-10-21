package io.github.project.vault.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process

/**
 * Isolated trampoline activity running in its own process (:restart) to reliably
 * restart the main application process.
 *
 * This ensures that when KernelSU, Magisk, or APatch requires the target app's
 * process to be recreated from zygote for superuser credentials to attach,
 * the old process is terminated and a completely fresh process is spawned
 * without race conditions or self-terminating timers.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0) {
            Process.killProcess(mainPid)
        }

        val restartIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESTART_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESTART_INTENT)
        }

        if (restartIntent != null) {
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(restartIntent)
        }

        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val EXTRA_MAIN_PID = "extra_main_pid"
        private const val EXTRA_RESTART_INTENT = "extra_restart_intent"

        fun restart(context: Context) {
            val packageName = context.packageName
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } ?: return

            val intent = Intent(context, RestartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_MAIN_PID, Process.myPid())
                putExtra(EXTRA_RESTART_INTENT, launchIntent)
            }

            context.startActivity(intent)
            if (context is Activity) {
                context.finishAffinity()
            }
            Runtime.getRuntime().exit(0)
        }
    }
}
