slot.setOnClickListener {
    if (shortcutId.isNotEmpty()) {
        try {
            val launcherApps = activity.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(
                app.packageName,
                shortcutId,
                null,
                null,
                android.os.Process.myUserHandle()
            )
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                activity,
                "Impossibile avviare la scorciatoia",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    } else {
        AppRepository.launchApp(activity, app.packageName)
    }
}
