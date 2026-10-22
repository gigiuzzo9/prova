package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AppContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    app: AppInfo,
    isInHome: Boolean,
    onAddToHome: () -> Unit,
    onRemoveFromHome: () -> Unit,
    context: Context
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Apri") },
            onClick = {
                AppRepository.launchApp(context, app.packageName)
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Info app") },
            onClick = {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Disinstalla") },
            onClick = {
                val i = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                onDismiss()
            }
        )
        if (isInHome) {
            DropdownMenuItem(
                text = { Text("Rimuovi dalla home") },
                onClick = {
                    onRemoveFromHome()
                    onDismiss()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Aggiungi alla home") },
                onClick = {
                    onAddToHome()
                    onDismiss()
                }
            )
        }
    }
}
