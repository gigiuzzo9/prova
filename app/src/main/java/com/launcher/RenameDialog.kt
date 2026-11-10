package com.simplylauncher

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object RenameDialog {

    fun show(
        context: Context,
        app: AppInfo,
        shortcutId: String = "",
        instanceId: String = "",
        onRenamed: () -> Unit
    ) {
        // Calcola la chiave "pulita" (safeKey) come facciamo per le icone
        val safeKey = if (shortcutId.isNotEmpty()) {
            val rawKey = if (instanceId.isEmpty()) "${app.packageName}|$shortcutId" 
                         else "${app.packageName}|$shortcutId|$instanceId"
            rawKey.replace("|", "_").replace(":", "_").replace("/", "_")
        } else {
            app.packageName
        }

        val input = EditText(context).apply {
            hint = context.getString(R.string.rename_hint)
            
            // Legge il nome attuale usando la chiave pulita
            val currentLabel = if (shortcutId.isNotEmpty()) {
                PrefsSync.customShortcutLabels(context)[safeKey] ?: app.label
            } else {
                app.label
            }
            
            setText(currentLabel)
            setSelection(text.length)
            textSize = 21f
            setPadding(42, 21, 42, 21)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.rename_title))
            .setView(input)
            .setPositiveButton(context.getString(R.string.rename_confirm)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    (context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
                        if (shortcutId.isNotEmpty()) {
                            // Salva usando la chiave pulita
                            PrefsSync.setCustomShortcutLabel(context, safeKey, newName)
                        } else {
                            PrefsSync.setCustomLabel(context, app.packageName, newName)
                        }
                        onRenamed()
                    }
                }
            }
            .setNegativeButton(context.getString(R.string.rename_cancel), null)
            .show()
    }
}
