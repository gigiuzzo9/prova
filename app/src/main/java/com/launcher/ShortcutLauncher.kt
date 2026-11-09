fun launch(
    context: Context,
    packageName: String,
    shortcutId: String,
    instanceId: String = "",
    showErrorToast: Boolean = true
) {
    if (shortcutId.isBlank()) {
        AppRepository.launchApp(context, packageName)
        return
    }

    val uniqueKey = when {
        instanceId.isEmpty() -> "$packageName|$shortcutId"
        else -> "$packageName|$shortcutId|$instanceId"
    }

    val savedData = PrefsSync.shortcutIntents(context)[uniqueKey]

    // 1. URL puro salvato (es. "url:https://...")
    if (savedData != null && savedData.startsWith("url:")) {
        val url = savedData.removePrefix("url:")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (tryStart(context, browserIntent)) return

        val forcedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (tryStart(context, forcedIntent)) return
    }

    // 2. PRIORITÀ: se abbiamo un Intent salvato (legacy shortcut), avvialo SUBITO
    if (!savedData.isNullOrBlank() && !savedData.startsWith("url:")) {
        try {
            val pi = Intent.parseUri(savedData, Intent.URI_INTENT_SCHEME)
            if (tryStart(context, pi)) return
        } catch (_: Exception) {}
    }

    // 3. Solo dopo, prova startShortcut (per scorciatoie dinamiche/pinned moderne)
    try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
        return
    } catch (_: Exception) {}

    // 4. Fallback: apri l'app
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null && tryStart(context, launchIntent)) return
    } catch (_: Exception) {}

    if (showErrorToast) {
        Toast.makeText(context.applicationContext, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
    }
}
