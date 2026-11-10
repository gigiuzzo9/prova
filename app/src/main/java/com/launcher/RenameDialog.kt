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
        uniqueKey: String = "", // <--- AGGIUNTO
        onRenamed: () -> Unit
    ) {
        val input = EditText(context).apply {
            hint = context.getString(R.string.rename_hint)
            
            // Usa la chiave esatta per leggere il nome attuale
            val currentLabel = if (uniqueKey.isNotEmpty() && shortcutId.isNotEmpty()) {
                PrefsSync.customShortcutLabels(context)[uniqueKey] ?: app.label
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
                        if (uniqueKey.isNotEmpty() && shortcutId.isNotEmpty()) {
                            // Salva usando la chiave esatta
                            PrefsSync.setCustomShortcutLabel(context, uniqueKey, newName)
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
