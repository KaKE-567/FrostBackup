package io.github.project.vault

import android.app.Application
import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VaultApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Check root access on startup asynchronously
        applicationScope.launch {
            RootShell.isRootAvailable()
        }
    }

    companion object {
        lateinit var instance: VaultApplication
            private set
    }
}
