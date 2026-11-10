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
        val input = EditText(context).apply {
            hint = context.getString(R.string.rename_hint)
            
            // Se è una scorciatoia, mostra il nome attuale della scorciatoia (se esiste)
            val currentLabel = if (shortcutId.isNotEmpty()) {
                val key = if (instanceId.isEmpty()) "${app.packageName}|$shortcutId" 
                          else "${app.packageName}|$shortcutId|$instanceId"
                PrefsSync.customShortcutLabels(context)[key] ?: app.label
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
                            // È una scorciatoia: salva nella mappa separata
                            val key = if (instanceId.isEmpty()) "${app.packageName}|$shortcutId" 
                                      else "${app.packageName}|$shortcutId|$instanceId"
                            PrefsSync.setCustomShortcutLabel(context, key, newName)
                        } else {
                            // È un'app normale
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
