package com.simplylauncher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf

class HomeActivity : ComponentActivity() {

    private val drawerOpen = mutableStateOf(false)
    private val reloadTick = mutableStateOf(0)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadTick.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        maybeAskLauncherRole()

        registerReceiver(
            reloadReceiver,
            IntentFilter("com.simplylauncher.RELOAD"),
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            reloadTick.value
            LauncherRoot(
                drawerOpen = drawerOpen.value,
                onDrawerOpenChange = { drawerOpen.value = it },
                onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    private fun maybeAskLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!rm.isRoleHeld(RoleManager.ROLE_HOME) && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
                roleLauncher.launch(intent)
            }
        }
    }
}
