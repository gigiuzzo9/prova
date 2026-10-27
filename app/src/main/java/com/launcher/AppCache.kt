package com.simplylauncher

object AppCache {
    private val map = mutableMapOf<String, AppInfo>()

    fun putAll(apps: List<AppInfo>) {
        map.clear()
        apps.forEach { map[it.packageName] = it }
    }

    fun put(app: AppInfo) {
        map[app.packageName] = app
    }

    fun get(pkg: String): AppInfo? = map[pkg]

    fun remove(pkg: String) {
        map.remove(pkg)
    }

    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size
}
