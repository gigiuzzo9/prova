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
        onRenamed: () -> Unit
    ) {
        val input = EditText(context).apply {
            hint = context.getString(R.string.rename_hint)
            setText(app.label)
            setSelection(text.length)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.rename_title))
            .setView(input)
            .setPositiveButton(context.getString(R.string.rename_confirm)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    (context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
                        Prefs.setCustomLabel(context, app.packageName, newName)
                        onRenamed()
                    }
                }
            }
            .setNegativeButton(context.getString(R.string.rename_cancel), null)
            .show()
    }
}
