package com.simplylauncher

import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.SizeF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

class HomeActivity : AppCompatActivity() {

    private lateinit var homeScreenGrid: HomeScreenGrid
    private lateinit var widgetContainer: FrameLayout
    private lateinit var widgetContainer2: FrameLayout
    private lateinit var pageIndicator: PageIndicatorView
    private lateinit var emptyMessage: TextView
    private lateinit var dockDrawerButton: FrameLayout
    private lateinit var dockLayout: View
    private lateinit var dockSlots: List<FrameLayout>
    private lateinit var resizeOverlay: FrameLayout
    private lateinit var resizeFrame: WidgetResizeFrame
    private lateinit var dragOverlayView: ImageView
    private var dragOverlayPending = false
    private var dragOverlayLastX = 0f
    private var dragOverlayLastY = 0f

    private lateinit var drawerOverlay: FrameLayout
    private lateinit var drawerCard: CardView
    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerAdapter: AppAdapter
    private var drawerOpen = false

    private lateinit var contextMenuView: AppContextMenuView
    private var currentApp: AppInfo? = null
    private var currentAnchor: View? = null
    private var pendingIconPackage: String? = null

    private lateinit var homePopupMenu: HomePopupMenu

    private lateinit var dragController: DragController
    private lateinit var dockController: DockController

    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetHostId = 1001
    private var pendingWidgetId = -1
    private var pendingWidgetPage = 0
    private var pendingWidgetRow = 0
    private var pendingWidgetCol = 0
    private var pendingWidgetWidthCells = 2
    private var pendingWidgetHeightCells = 1
    private val UNINSTALL_APP_REQUEST_CODE = 200
    private val REQUEST_PICK_ICON = 201
    private val REQUEST_PICK_WALLPAPER = 202
    private var resizingWidgetSlot: WidgetSlot? = null
    private var resizingWidgetView: LauncherAppWidgetHostView? = null
    private var widgetPopupWindow: android.widget.PopupWindow? = null
    private var pickerDialog: android.app.Dialog? = null

    private var currentCols = 4
    private var currentRows = 6
    private var currentPageCount = 1
    private var isDropInProgress = false
    private var isResizing = false
    private var currentDrawerColor: Int = Color.BLACK
    private var currentDrawerAlpha: Float = 0.9f

    private var drawerBgApplied = false
    private var adjWidgetPage = -1

    private val REQUEST_BIND_WIDGET = 101
    private val REQUEST_CONFIGURE_WIDGET = 102

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidateCache()
            lifecycleScope.launch {
                AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
                loadHomeApps()
                dockController.refresh()
                val apps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
                drawerAdapter.submitList(apps)
                renderWidgets()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.parseColor("#80000000")

        homeScreenGrid = findViewById(R.id.homeScreenGrid)
        widgetContainer = findViewById(R.id.widgetContainer)
        widgetContainer2 = findViewById(R.id.widgetContainer2)
        pageIndicator = findViewById(R.id.pageIndicator)
        emptyMessage = findViewById(R.id.emptyMessage)
        dockDrawerButton = findViewById(R.id.dockDrawerButton)
        dockLayout = findViewById(R.id.dockLayout)
        resizeOverlay = findViewById(R.id.resizeOverlay)
        resizeFrame = findViewById(R.id.resizeFrame)

        dragOverlayView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (56 * resources.displayMetrics.density).toInt(),
                (56 * resources.displayMetrics.density).toInt()
            )
            visibility = View.GONE
            elevation = 30f * resources.displayMetrics.density
        }
        (findViewById<FrameLayout>(android.R.id.content)).addView(dragOverlayView)

        dockSlots = listOf(
            findViewById(R.id.dockSlot1),
            findViewById(R.id.dockSlot2),
            findViewById(R.id.dockSlot3),
            findViewById(R.id.dockSlot4)
        )

        drawerOverlay = findViewById(R.id.drawerOverlay)
        drawerCard = findViewById(R.id.drawerCard)
        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = object : AppWidgetHost(this, widgetHostId) {
            override fun onCreateView(
                context: Context,
                appWidgetId: Int,
                appWidget: AppWidgetProviderInfo
            ): AppWidgetHostView {
                return LauncherAppWidgetHostView(context)
            }
        }

        setupResizeFrame()
        setupGrid()
        setupDragController()
        setupDockController()
        setupDrawer()
        setupContextMenu()
        setupHomePopup()
        setupDrawerButton()

        currentCols = PrefsSync.cols(this)
        currentRows = PrefsSync.rows(this)
        currentPageCount = PrefsSync.pageCount(this)

        homeScreenGrid.setGridSize(currentCols, currentRows)
        homeScreenGrid.setPageCount(currentPageCount)

        lifecycleScope.launch {
            AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            loadHomeApps()
            dockController.refresh()
            loadDrawerApps()
        }

        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                AppRepository.invalidateCache()
                AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
                loadHomeApps()
                dockController.refresh()
                loadDrawerApps()
                renderWidgets()
            }
        }

        askLauncherIfNeeded()

        registerReceiver(
            reloadReceiver,
            IntentFilter("com.simplylauncher.RELOAD"),
            RECEIVER_NOT_EXPORTED
        )
    }

    private fun setupGrid() {
        homeScreenGrid.itemClickListener = { slot ->
            if (slot.packageName.isNotEmpty()) {
                AppRepository.launchApp(this, slot.packageName)
            }
        }
        homeScreenGrid.itemLongClickListener = { slot, view ->
            val app = AppCache.get(slot.packageName)
            if (app != null) {
                dragController.onLongPress(app, view, DragController.SOURCE_HOME, false)
            }
        }
        homeScreenGrid.emptyCellLongClickListener = { view ->
            showHomePopup(view)
        }

        homeScreenGrid.onSwipeOffsetChanged = { offset ->
            widgetContainer.translationX = offset

            val page = homeScreenGrid.getCurrentPage()
            val adj = when {
                offset > 0 && page > 0 -> page - 1
                offset < 0 && page < currentPageCount - 1 -> page + 1
                else -> -1
            }
            if (adj != -1) {
                if (adjWidgetPage != adj) {
                    adjWidgetPage = adj
                    renderWidgetsForPage(adj, widgetContainer2)
                }
                val sign = if (offset > 0) -1 else 1
                widgetContainer2.translationX = offset + sign * homeScreenGrid.width
            } else {
                widgetContainer2.translationX = 0f
                widgetContainer2.removeAllViews()
                adjWidgetPage = -1
            }
        }

        homeScreenGrid.pageChangeListener = { page ->
            pageIndicator.setCurrentPage(page)
            val tmp = widgetContainer
            widgetContainer = widgetContainer2
            widgetContainer2 = tmp
            widgetContainer.translationX = 0f
            widgetContainer2.translationX = 0f
            widgetContainer2.removeAllViews()
            adjWidgetPage = -1
            renderWidgets()
        }
    }

    private fun setupResizeFrame() {
        resizeFrame.onClickListener = { endResizeMode() }
    }

    private fun endResizeMode() {
        val slot = resizingWidgetSlot
        val hostView = resizingWidgetView

        if (slot != null && hostView != null) {
            val cellW = homeScreenGrid.width / currentCols
            val cellH = homeScreenGrid.height / currentRows
            val lp = hostView.layoutParams as FrameLayout.LayoutParams
            val newWidthCells = (lp.width / cellW).coerceAtLeast(1)
            val newHeightCells = (lp.height / cellH).coerceAtLeast(1)
            val newCol = (lp.leftMargin / cellW).coerceAtLeast(0)
            val newRow = (lp.topMargin / cellH).coerceAtLeast(0)

            val newSlot = slot.copy(
                row = newRow,
                col = newCol,
                widthCells = newWidthCells,
                heightCells = newHeightCells
            )

            val widgets = PrefsSync.widgetSlots(this).toMutableList()
            val index = widgets.indexOfFirst { it.appWidgetId == slot.appWidgetId }
            if (index != -1) {
                widgets[index] = newSlot
                PrefsSync.setWidgetSlots(this, widgets)
            }
        }

        resizingWidgetSlot = null
        resizingWidgetView = null
        isResizing = false
        resizeOverlay.visibility = View.GONE
        resizeFrame.visibility = View.GONE
        widgetContainer.postDelayed({ renderWidgets() }, 50)
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        try { appWidgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            UNINSTALL_APP_REQUEST_CODE -> {
                AppRepository.invalidateCache()
                lifecycleScope.launch {
                    AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
                    loadHomeApps()
                    dockController.refresh()
                    loadDrawerApps()
                }
            }
            REQUEST_PICK_ICON -> {
                if (resultCode == RESULT_OK && data?.data != null) {
                    pendingIconPackage?.let { pkg ->
                        saveCustomIcon(pkg, data.data!!)
                    }
                }
                pendingIconPackage = null
            }
            REQUEST_PICK_WALLPAPER -> {
                if (resultCode == RESULT_OK && data?.data != null) {
                    val uri = data.data!!
                    try {
                        val wm = WallpaperManager.getInstance(this)
                        val dm = resources.displayMetrics
                        val targetW = dm.widthPixels
                        val targetH = dm.heightPixels

                        val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, boundsOptions)
                        }
                        val srcW = boundsOptions.outWidth
                        val srcH = boundsOptions.outHeight

                        var sampleSize = 1
                        while (srcW / (sampleSize * 2) >= targetW && srcH / (sampleSize * 2) >= targetH) {
                            sampleSize *= 2
                        }

                        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        val srcBitmap = contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
                        } ?: throw Exception("Impossibile leggere l'immagine")

                        val srcRatio = srcBitmap.width.toFloat() / srcBitmap.height
                        val targetRatio = targetW.toFloat() / targetH

                        val cropW: Int
                        val cropH: Int
                        if (srcRatio > targetRatio) {
                            cropH = srcBitmap.height
                            cropW = (cropH * targetRatio).toInt()
                        } else {
                            cropW = srcBitmap.width
                            cropH = (cropW / targetRatio).toInt()
                        }
                        val cropX = (srcBitmap.width - cropW) / 2
                        val cropY = (srcBitmap.height - cropH) / 2

                        val cropped = android.graphics.Bitmap.createBitmap(
                            srcBitmap, cropX, cropY, cropW, cropH
                        )
                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                            cropped, targetW, targetH, true
                        )

                        val visibleCrop = android.graphics.Rect(0, 0, scaled.width, scaled.height)
                        wm.setBitmap(scaled, visibleCrop, true, WallpaperManager.FLAG_SYSTEM)

                        Toast.makeText(this, "Sfondo impostato", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQUEST_BIND_WIDGET -> {
                if (resultCode == RESULT_OK) {
                    val boundId = data?.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        pendingWidgetId
                    ) ?: pendingWidgetId
                    pendingWidgetId = boundId

                    val info = appWidgetManager.getAppWidgetInfo(boundId)
                    if (info == null) {
                        Toast.makeText(this, "Widget non disponibile dopo il bind", Toast.LENGTH_SHORT).show()
                        appWidgetHost.deleteAppWidgetId(boundId)
                        pendingWidgetId = -1
                        return
                    }

                    if (info.configure != null) {
                        try {
                            appWidgetHost.startAppWidgetConfigureActivityForResult(
                                this@HomeActivity,
                                pendingWidgetId,
                                0,
                                REQUEST_CONFIGURE_WIDGET,
                                null
                            )
                        } catch (e: Exception) {
                            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                            pendingWidgetId = -1
                        }
                    } else {
                        findFreeCellAndPlaceWidget()
                    }
                } else {
                    Toast.makeText(this, R.string.widget_bind_denied, Toast.LENGTH_SHORT).show()
                    appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = -1
                }
            }
            REQUEST_CONFIGURE_WIDGET -> {
                if (resultCode == RESULT_OK) saveWidgetToHome()
                else Toast.makeText(this, R.string.widget_config_cancelled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCustomIcon(packageName: String, uri: Uri) {
        try {
            val iconsDir = File(filesDir, "custom_icons")
            if (!iconsDir.exists()) iconsDir.mkdirs()
            val destFile = File(iconsDir, "$packageName.png")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            PrefsSync.setCustomIcon(this, packageName, destFile.absolutePath)

            AppRepository.invalidateCache()

            lifecycleScope.launch {
                AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
                loadHomeApps()
                dockController.refresh()
                loadDrawerApps()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Errore salvataggio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreCustomIcon(packageName: String) {
        try {
            val iconsDir = File(filesDir, "custom_icons")
            val destFile = File(iconsDir, "$packageName.png")
            if (destFile.exists()) destFile.delete()

            PrefsSync.removeCustomIcon(this, packageName)

            AppRepository.invalidateCache()

            lifecycleScope.launch {
                AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
                loadHomeApps()
                dockController.refresh()
                loadDrawerApps()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Errore ripristino: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findFreeCellAndPlaceWidget() {
        if (pendingWidgetId == -1) return
        if (currentCols <= 0 || currentRows <= 0) {
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            return
        }

        val slots = PrefsSync.homeSlots(this)
        val widgets = PrefsSync.widgetSlots(this)
        val occupied = mutableSetOf<Pair<Int, Int>>()
        slots.filter { it.page == pendingWidgetPage }.forEach { occupied.add(it.row to it.col) }
        widgets.filter { it.page == pendingWidgetPage }.forEach {
            for (r in it.row until it.row + it.heightCells) {
                for (c in it.col until it.col + it.widthCells) occupied.add(r to c)
            }
        }

        val info = appWidgetManager.getAppWidgetInfo(pendingWidgetId)
        if (info == null) {
            Toast.makeText(this, "Widget non valido", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            return
        }

        var widthCells = 0
        var heightCells = 0

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (info.targetCellWidth > 0) widthCells = info.targetCellWidth
            if (info.targetCellHeight > 0) heightCells = info.targetCellHeight
        }
        if (widthCells <= 0 && info.minWidth > 0) widthCells = getCellCount(info.minWidth)
        if (heightCells <= 0 && info.minHeight > 0) heightCells = getCellCount(info.minHeight)
        if (widthCells <= 0) widthCells = 1
        if (heightCells <= 0) heightCells = 1

        pendingWidgetWidthCells = widthCells.coerceIn(1, currentCols)
        pendingWidgetHeightCells = heightCells.coerceIn(1, currentRows)

        if (pendingWidgetWidthCells > currentCols || pendingWidgetHeightCells > currentRows) {
            Toast.makeText(this, "Widget troppo grande per la griglia", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            return
        }

        var found = false
        for (r in 0..(currentRows - pendingWidgetHeightCells)) {
            for (c in 0..(currentCols - pendingWidgetWidthCells)) {
                var free = true
                for (dr in 0 until pendingWidgetHeightCells) {
                    for (dc in 0 until pendingWidgetWidthCells) {
                        if (occupied.contains(r + dr to c + dc)) { free = false; break }
                    }
                    if (!free) break
                }
                if (free) {
                    pendingWidgetRow = r
                    pendingWidgetCol = c
                    found = true
                    break
                }
            }
            if (found) break
        }

        if (!found) {
            Toast.makeText(this, "Nessuno spazio libero", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            return
        }

        if (info.configure != null) {
            try {
                appWidgetHost.startAppWidgetConfigureActivityForResult(
                    this@HomeActivity,
                    pendingWidgetId,
                    0,
                    REQUEST_CONFIGURE_WIDGET,
                    null
                )
            } catch (e: Exception) {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = -1
            }
        } else {
            saveWidgetToHome()
        }
    }

    private fun getCellCount(sizeDp: Int): Int {
        val density = resources.displayMetrics.density
        val tiles = ceil(((sizeDp / density) - 30) / 70.0).toInt()
        return max(tiles, 1)
    }

    private fun saveWidgetToHome() {
        val widgets = PrefsSync.widgetSlots(this).toMutableList()
        widgets.add(
            WidgetSlot(
                appWidgetId = pendingWidgetId,
                row = pendingWidgetRow,
                col = pendingWidgetCol,
                widthCells = pendingWidgetWidthCells,
                heightCells = pendingWidgetHeightCells,
                page = pendingWidgetPage
            )
        )
        PrefsSync.setWidgetSlots(this, widgets)
        Toast.makeText(this, R.string.widget_added, Toast.LENGTH_SHORT).show()
        widgetContainer.post { renderWidgets() }
    }

    private fun startWidgetPicker() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = appWidgetId
        pendingWidgetPage = homeScreenGrid.getCurrentPage()
        pendingWidgetWidthCells = 2.coerceAtMost(currentCols)
        pendingWidgetHeightCells = 1

        showWidgetPickerDialog(appWidgetId)
    }

    private fun showWidgetPickerDialog(widgetId: Int) {
        val ctx = this
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        fun themeColor(attr: Int): Int {
            val tv = android.util.TypedValue()
            if (theme.resolveAttribute(attr, tv, true)) {
                return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(ctx, tv.resourceId) else tv.data
            }
            return 0xFF000000.toInt()
        }

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
        }

        val header = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(8), dp(16))
        }
        header.addView(TextView(ctx).apply {
            text = "Scegli un widget"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(ctx).apply {
            text = "✕"
            textSize = 22f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { pickerDialog?.dismiss() }
        })
        root.addView(header)

        root.addView(View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(0x22000000)
        })

        val list = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(16))
        }
        val scroll = android.widget.ScrollView(ctx).apply { addView(list) }
        root.addView(scroll, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val providers = try {
            appWidgetManager.installedProviders.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        } catch (e: Exception) { emptyList() }

        if (providers.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = "Nessun widget disponibile"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(themeColor(android.R.attr.textColorSecondary))
                setPadding(0, dp(48), 0, 0)
            })
        } else {
            providers.forEach { info ->
                val row = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    isClickable = true
                    isFocusable = true
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                }

                val previewContainer = android.widget.FrameLayout(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(96), dp(96)).apply { marginEnd = dp(16) }
                }

                val preview: android.graphics.drawable.Drawable? = (
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            info.loadPreviewImage(ctx, resources.displayMetrics.densityDpi)
                        } else null
                    } catch (e: Exception) { null }
                ) ?: (if (info.previewImage != 0) {
                    try { androidx.core.content.ContextCompat.getDrawable(ctx, info.previewImage) } catch (e: Exception) { null }
                } else null) ?: (try {
                    val ai = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationIcon(ai)
                } catch (e: Exception) { null })

                if (preview != null) {
                    val iv = ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(preview)
                    }
                    val wR = if (info.minWidth > 0) info.minWidth else preview.intrinsicWidth
                    val hR = if (info.minHeight > 0) info.minHeight else preview.intrinsicHeight
                    if (wR > 0 && hR > 0) {
                        val ratio = wR.toFloat() / hR.toFloat()
                        val sizePx = dp(96)
                        val lp = if (ratio >= 1f) {
                            android.widget.FrameLayout.LayoutParams(sizePx, (sizePx / ratio).toInt().coerceAtLeast(1)).apply { gravity = Gravity.CENTER }
                        } else {
                            android.widget.FrameLayout.LayoutParams((sizePx * ratio).toInt().coerceAtLeast(1), sizePx).apply { gravity = Gravity.CENTER }
                        }
                        previewContainer.addView(iv, lp)
                    } else {
                        previewContainer.addView(iv, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                } else {
                    previewContainer.addView(ImageView(ctx).apply {
                        setImageResource(android.R.drawable.sym_def_app_icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                row.addView(previewContainer)

                val texts = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                texts.addView(TextView(ctx).apply {
                    text = try { info.loadLabel(packageManager) } catch (e: Exception) { "Widget" }
                    textSize = 16f
                    setTextColor(themeColor(android.R.attr.textColorPrimary))
                })
                val appName = try {
                    val ai = packageManager.getApplicationInfo(info.provider.packageName, 0)
                    packageManager.getApplicationLabel(ai).toString()
                } catch (e: Exception) { "" }
                if (appName.isNotEmpty()) {
                    texts.addView(TextView(ctx).apply {
                        text = appName
                        textSize = 13f
                        setTextColor(themeColor(android.R.attr.textColorSecondary))
                        setPadding(0, dp(2), 0, 0)
                    })
                }
                row.addView(texts)

                row.setOnClickListener {
                    val bound = try {
                        appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, info.provider)
                    } catch (e: Exception) { false }

                    if (bound) {
                        pendingWidgetId = widgetId
                        pickerDialog?.dismiss()
                        findFreeCellAndPlaceWidget()
                    } else {
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        }
                        startActivityForResult(bindIntent, REQUEST_BIND_WIDGET)
                        pickerDialog?.dismiss()
                    }
                }

                list.addView(row)
            }
        }

        pickerDialog = android.app.Dialog(ctx, android.R.style.Theme_DeviceDefault_Light_NoActionBar).apply {
            setContentView(root)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnDismissListener { pickerDialog = null }
            show()
        }
    }

    private fun renderWidgets() {
        renderWidgetsForPage(homeScreenGrid.getCurrentPage(), widgetContainer)
    }

    private fun renderWidgetsForPage(page: Int, container: FrameLayout) {
        if (homeScreenGrid.width == 0 || homeScreenGrid.height == 0) return

        val allWidgets = PrefsSync.widgetSlots(this)
        container.removeAllViews()

        val cellW = homeScreenGrid.width / currentCols
        val cellH = homeScreenGrid.height / currentRows

        val pageWidgets = allWidgets.filter { it.page == page }
        for (slot in pageWidgets) {
            try {
                val info = appWidgetManager.getAppWidgetInfo(slot.appWidgetId) ?: continue
                val hostView = appWidgetHost.createView(baseContext, slot.appWidgetId, info) as LauncherAppWidgetHostView
                hostView.setAppWidget(slot.appWidgetId, info)
                hostView.tag = slot.appWidgetId

                val widgetWidth = cellW * slot.widthCells
                val widgetHeight = cellH * slot.heightCells

                val lp = FrameLayout.LayoutParams(widgetWidth, widgetHeight).apply {
                    leftMargin = slot.col * cellW
                    topMargin = slot.row * cellH
                }
                container.addView(hostView, lp)

                hostView.post {
                    try {
                        val density = resources.displayMetrics.density
                        val wDp = (widgetWidth / density).toInt()
                        val hDp = (widgetHeight / density).toInt()
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp.toFloat(), hDp.toFloat())))
                        } else {
                            hostView.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
                        }
                    } catch (_: Exception) {}
                }

                hostView.onLongPress = {
                    dragController.onWidgetLongPress(slot, info, hostView)
                    showWidgetMenu(slot, info, cellW, cellH, hostView, page)
                }
                hostView.onIgnoreIntercepted = {
                    widgetPopupWindow?.dismiss()
                }
            } catch (e: Exception) {
                android.util.Log.e("WIDGET_DEBUG", "Errore render widget ${slot.appWidgetId}", e)
            }
        }
    }

    private fun startResize(
        slot: WidgetSlot,
        info: AppWidgetProviderInfo,
        cellW: Int,
        cellH: Int,
        hostView: LauncherAppWidgetHostView,
        pageIndex: Int
    ) {
        resizingWidgetSlot = slot
        resizingWidgetView = hostView
        isResizing = true

        val containerLoc = IntArray(2)
        widgetContainer.getLocationOnScreen(containerLoc)
        val overlayLoc = IntArray(2)
        resizeOverlay.getLocationOnScreen(overlayLoc)

        val offsetX = containerLoc[0] - overlayLoc[0]
        val offsetY = containerLoc[1] - overlayLoc[1]

        val frameLeftAbs = offsetX + slot.col * cellW
        val frameTopAbs = offsetY + slot.row * cellH
        val frameRightAbs = frameLeftAbs + slot.widthCells * cellW
        val frameBottomAbs = frameTopAbs + slot.heightCells * cellH

        val sideMargins = Rect(offsetX, offsetY, 0, 0)

        val allWidgets = PrefsSync.widgetSlots(this)
        val allSlots = allWidgets.filter { it.page == pageIndex }.toMutableList() as ArrayList<WidgetSlot>

        resizeFrame.updateFrameCoords(
            coords = Rect(frameLeftAbs, frameTopAbs, frameRightAbs, frameBottomAbs),
            cellWidth = cellW,
            cellHeight = cellH,
            sideMargins = sideMargins,
            slot = slot,
            allSlots = allSlots
        )

        resizeFrame.onResizeListener = { cellsRect ->
            val newSlot = slot.copy(
                row = cellsRect.top,
                col = cellsRect.left,
                widthCells = cellsRect.right - cellsRect.left + 1,
                heightCells = cellsRect.bottom - cellsRect.top + 1
            )
            resizingWidgetSlot = newSlot
            updateWidgetPositionAndSize(hostView, newSlot, cellW, cellH)
        }

        resizeOverlay.setOnClickListener { endResizeMode() }
        resizeOverlay.visibility = View.VISIBLE
        resizeOverlay.bringToFront()
        resizeOverlay.elevation = 20f * resources.displayMetrics.density
        resizeFrame.visibility = View.VISIBLE
    }

    private fun updateWidgetPositionAndSize(
        hostView: LauncherAppWidgetHostView,
        slot: WidgetSlot,
        cellW: Int,
        cellH: Int
    ) {
        val lp = hostView.layoutParams as FrameLayout.LayoutParams
        lp.width = cellW * slot.widthCells
        lp.height = cellH * slot.heightCells
        lp.leftMargin = slot.col * cellW
        lp.topMargin = slot.row * cellH
        hostView.layoutParams = lp

        hostView.post {
            try {
                val density = resources.displayMetrics.density
                val wDp = (lp.width / density).toInt()
                val hDp = (lp.height / density).toInt()
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp.toFloat(), hDp.toFloat())))
                } else {
                    hostView.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
                }
            } catch (_: Exception) {}
        }
    }

    private fun showWidgetMenu(
        slot: WidgetSlot,
        info: AppWidgetProviderInfo,
        cellW: Int,
        cellH: Int,
        hostView: LauncherAppWidgetHostView,
        pageIndex: Int
    ) {
        val ctx = this
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.WHITE)
            }
            background = drawable
            elevation = 8f * density
        }

        fun addItem(text: String, onClick: () -> Unit) {
            val tv = TextView(ctx).apply {
                this.text = text
                setTextColor(Color.BLACK)
                textSize = 20f
                setPadding(dp(26), dp(18), dp(26), dp(18))
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    onClick()
                    widgetPopupWindow?.dismiss()
                }
            }
            container.addView(tv)
        }

        addItem("Ridimensiona") {
            dragController.cancelPendingDrag()
            startResize(slot, info, cellW, cellH, hostView, pageIndex)
        }
        addItem("Rimuovi widget") {
            val current = PrefsSync.widgetSlots(this)
            PrefsSync.setWidgetSlots(
                this,
                current.filter { it.appWidgetId != slot.appWidgetId }
            )
            appWidgetHost.deleteAppWidgetId(slot.appWidgetId)
            renderWidgets()
        }

        widgetPopupWindow?.dismiss()
        widgetPopupWindow = android.widget.PopupWindow(
            container,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            showAtLocation(widgetContainer, Gravity.CENTER, 0, 0)
        }
    }

    private fun setupDragController() {
        dragController = DragController(
            activity = this,
            homeRecyclerView = homeScreenGrid,
            drawerOverlay = drawerOverlay,
            onDragStart = { source, pkg, widgetId, widthCells, heightCells, x, y ->
                showDragOverlay(source, pkg, widgetId, widthCells, heightCells, x, y)
            },
            onDragMove = { x, y -> updateDragOverlay(x, y) },
            onDropOnHome = { source, pkg, widgetId, x, y ->
                hideDragOverlay()
                if (source == DragController.SOURCE_WIDGET) {
                    handleWidgetDrop(widgetId, x, y)
                    true
                } else {
                    handleDropOnHome(source, pkg, x, y)
                }
            },
            onDropOnDrawer = { pkg ->
                hideDragOverlay()
                handleDropOnDrawer(pkg)
            },
            onDropOnDock = { source, pkg, x, y ->
                hideDragOverlay()
                handleDropOnDock(source, pkg, x, y)
            },
            onDragEnd = { hideDragOverlay() },
            onVisualDragStart = { source, pkg ->
                setDraggingApp(pkg, source)
            },
            onVisualDragEnd = { source, pkg ->
                homeScreenGrid.post { setDraggingApp(null, source) }
            }
        )
    }

    private fun setDraggingApp(pkg: String?, source: String) {
        when (source) {
            DragController.SOURCE_HOME -> {
                homeScreenGrid.setDraggingPackage(pkg, homeScreenGrid.getCurrentPage())
            }
            DragController.SOURCE_DRAWER -> {
                drawerAdapter.setDraggingPackage(pkg)
            }
            DragController.SOURCE_DOCK -> {
                dockController.setDraggingPackage(pkg)
            }
        }
    }

    private fun showDragOverlay(
        source: String,
        pkg: String,
        widgetId: Int,
        widthCells: Int,
        heightCells: Int,
        x: Float,
        y: Float
    ) {
        if (source == DragController.SOURCE_WIDGET) {
            val cellW = homeScreenGrid.width / currentCols
            val cellH = homeScreenGrid.height / currentRows
            val widgetWidthPx = cellW * widthCells
            val widgetHeightPx = cellH * heightCells
            dragOverlayView.layoutParams = FrameLayout.LayoutParams(widgetWidthPx, widgetHeightPx)

            val hostView = (0 until widgetContainer.childCount)
                .map { widgetContainer.getChildAt(it) }
                .firstOrNull { it.tag == widgetId } as? LauncherAppWidgetHostView

            if (hostView != null && hostView.width > 0 && hostView.height > 0) {
                val bitmap = Bitmap.createBitmap(
                    hostView.width,
                    hostView.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                hostView.draw(canvas)
                dragOverlayView.setImageBitmap(bitmap)
            } else {
                dragOverlayView.setImageDrawable(null)
            }
        } else {
            val sizePx = (56 * resources.displayMetrics.density).toInt()
            dragOverlayView.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            val bitmap = AppCache.getIconBitmap(pkg, sizePx)
            if (bitmap != null) {
                dragOverlayView.setImageBitmap(bitmap)
            } else {
                dragOverlayView.setImageDrawable(null)
            }
        }

        dragOverlayView.visibility = View.VISIBLE
        updateDragOverlay(x, y)
    }

    private fun updateDragOverlay(x: Float, y: Float) {
        if (dragOverlayView.visibility != View.VISIBLE) return
        dragOverlayLastX = x
        dragOverlayLastY = y
        if (dragOverlayPending) return
        dragOverlayPending = true
        dragOverlayView.postOnAnimation {
            dragOverlayPending = false
            dragOverlayView.x = dragOverlayLastX - dragOverlayView.width / 2f
            dragOverlayView.y = dragOverlayLastY - dragOverlayView.height / 2f
        }
    }

    private fun hideDragOverlay() {
        dragOverlayView.visibility = View.GONE
    }

    fun isPointInDock(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        dockLayout.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + dockLayout.width &&
                y >= loc[1] && y <= loc[1] + dockLayout.height
    }

    fun isPointInDrawer(x: Float, y: Float): Boolean {
        if (!drawerOpen) return false
        val loc = IntArray(2)
        drawerOverlay.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + drawerOverlay.width &&
                y >= loc[1] && y <= loc[1] + drawerOverlay.height
    }

    private fun handleDropOnDock(source: String, pkg: String, x: Float, y: Float) {
        var closestIndex = 0
        var closestDist = Float.MAX_VALUE
        dockSlots.forEachIndexed { index, slot ->
            val loc = IntArray(2)
            slot.getLocationOnScreen(loc)
            val cx = loc[0] + slot.width / 2f
            val cy = loc[1] + slot.height / 2f
            val dist = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()
            if (dist < closestDist) { closestDist = dist; closestIndex = index }
        }

        lifecycleScope.launch {
            if (source == DragController.SOURCE_DOCK) {
                dockController.swapDockSlots(pkg, closestIndex)
            } else {
                dockController.addToDockAtPublic(pkg, closestIndex)
            }
        }
    }

    private fun handleWidgetDrop(widgetId: Int, x: Float, y: Float) {
        val pageIndex = homeScreenGrid.getCurrentPage()
        val cellW = homeScreenGrid.width / currentCols
        val cellH = homeScreenGrid.height / currentRows
        val loc = IntArray(2)
        homeScreenGrid.getLocationOnScreen(loc)
        val localX = x - loc[0]
        val localY = y - loc[1]
        val col = (localX / cellW).toInt().coerceIn(0, currentCols - 1)
        val row = (localY / cellH).toInt().coerceIn(0, currentRows - 1)
        moveWidget(widgetId, row, col, pageIndex)
    }

    private fun setupDockController() {
        dockController = DockController(
            activity = this,
            slots = dockSlots,
            onAppLongPressed = { app, view ->
                dragController.onLongPress(app, view, DragController.SOURCE_DOCK, false)
            }
        )
        dockController.setup()
    }

    private fun setupDrawer() {
        drawerRecyclerView.layoutManager = MyGridLayoutManager(this, 4)
        drawerRecyclerView.setHasFixedSize(true)
        drawerRecyclerView.itemAnimator = null
        drawerRecyclerView.isNestedScrollingEnabled = false
        drawerRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                outRect.bottom = (16 * resources.displayMetrics.density).toInt()
            }
        })
        drawerAdapter = AppAdapter(
            onClick = { app ->
                AppRepository.launchApp(this, app.packageName)
                closeDrawer()
            },
            onLongPress = { app, view ->
                dragController.onLongPress(app, view, DragController.SOURCE_DRAWER, true)
            }
        )
        drawerRecyclerView.adapter = drawerAdapter
        drawerOverlay.setOnClickListener { closeDrawer() }
    }

    private fun setupContextMenu() {
        contextMenuView = AppContextMenuView(this)
        contextMenuView.onMove = { contextMenuView.dismiss() }
        contextMenuView.onUninstall = {
            currentApp?.let { app ->
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = android.net.Uri.fromParts("package", app.packageName, null)
                }
                startActivityForResult(intent, UNINSTALL_APP_REQUEST_CODE)
            }
        }
        contextMenuView.onRename = {
            currentApp?.let { app ->
                RenameDialog.show(this, app) {
                    AppRepository.invalidateCache()
                    AppRepository.loadInstalledAppsWithCustomLabels(this)
                    homeScreenGrid.setItems(PrefsSync.homeSlots(this))
                    val apps = AppRepository.loadInstalledAppsWithCustomLabels(this)
                    drawerAdapter.submitList(apps)
                }
            }
        }
        contextMenuView.onRemove = {
            currentApp?.let { app ->
                val homeSlots = PrefsSync.homeSlots(this)
                val dockItems = PrefsSync.dockItems(this)
                PrefsSync.setHomeSlots(this, homeSlots.filter { it.packageName != app.packageName })
                PrefsSync.setDockItems(this, dockItems.map { if (it == app.packageName) "" else it })
                loadHomeApps()
                dockController.refresh()
            }
        }
        contextMenuView.onChangeIcon = {
            currentApp?.let { app ->
                pendingIconPackage = app.packageName
                val hasCustomIcon = PrefsSync.customIcons(this).containsKey(app.packageName)

                val builder = AlertDialog.Builder(this)
                    .setTitle("Cambia icona")
                    .setMessage("Per un risultato migliore, usa un'immagine PNG con sfondo trasparente, di dimensioni 512x512 pixel.")
                    .setPositiveButton("Scegli immagine") { _, _ ->
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        startActivityForResult(intent, REQUEST_PICK_ICON)
                    }
                    .setNegativeButton("Annulla", null)

                if (hasCustomIcon) {
                    builder.setNeutralButton("Ripristina") { _, _ ->
                        restoreCustomIcon(app.packageName)
                    }
                }

                builder.show()
            }
        }
        contextMenuView.onDismiss = { currentApp = null; currentAnchor = null }
    }

    private fun setupHomePopup() {
        homePopupMenu = HomePopupMenu(this)
        homePopupMenu.onAddWidget = {
            homePopupMenu.dismiss()
            startWidgetPicker()
        }
        homePopupMenu.onAddPage = {
            currentPageCount += 1
            PrefsSync.setPageCount(this, currentPageCount)
            pageIndicator.setPageCount(currentPageCount)
            homeScreenGrid.setPageCount(currentPageCount)
        }
        homePopupMenu.onChangeWallpaper = {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_PICK_WALLPAPER)
        }
        homePopupMenu.onRemovePage = {
            if (currentPageCount > 1) {
                val currentPageIndex = homeScreenGrid.getCurrentPage()
                val slots = PrefsSync.homeSlots(this)
                val widgets = PrefsSync.widgetSlots(this)
                val itemsOnPage = slots.count { it.page == currentPageIndex } +
                        widgets.count { it.page == currentPageIndex }
                if (itemsOnPage > 0) {
                    Toast.makeText(this, "La pagina non è vuota", Toast.LENGTH_SHORT).show()
                } else {
                    val newSlots = slots.map {
                        if (it.page > currentPageIndex) it.copy(page = it.page - 1) else it
                    }
                    val newWidgets = widgets.map {
                        if (it.page > currentPageIndex) it.copy(page = it.page - 1) else it
                    }
                    PrefsSync.setHomeSlots(this, newSlots)
                    PrefsSync.setWidgetSlots(this, newWidgets)
                    currentPageCount -= 1
                    if (currentPageCount < 1) currentPageCount = 1
                    PrefsSync.setPageCount(this, currentPageCount)
                    pageIndicator.setPageCount(currentPageCount)
                    homeScreenGrid.setPageCount(currentPageCount)
                    loadHomeApps()
                    renderWidgets()
                }
            }
        }
    }

    private fun setupDrawerButton() {
        dockDrawerButton.removeAllViews()
        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_drawer)
            val size = (62 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        }
        dockDrawerButton.addView(imageView)
        dockDrawerButton.setOnClickListener { openDrawer() }
    }

    private fun showHomePopup(anchor: View) {
        homePopupMenu.show(anchor, showRemovePage = currentPageCount > 1)
    }

    fun showContextMenuForDrag(app: AppInfo, anchor: View, fromDrawer: Boolean) {
        currentApp = app
        currentAnchor = anchor
        contextMenuView.show(
            anchor,
            showRemove = !fromDrawer,
            drawerIsWhite = fromDrawer && (currentDrawerColor == Color.WHITE)
        )
    }

    fun dismissDragPopup() {
        contextMenuView.dismiss()
        currentApp = null
        currentAnchor = null
    }

    fun dismissWidgetPopup() {
        widgetPopupWindow?.dismiss()
        widgetPopupWindow = null
    }

    fun onDragFromDrawerStarted() {
        drawerOverlay.visibility = View.GONE
        drawerOverlay.alpha = 1f
        drawerOpen = false
        homeScreenGrid.visibility = View.VISIBLE
        pageIndicator.visibility = View.VISIBLE
        dockLayout.visibility = View.VISIBLE
        widgetContainer.visibility = View.VISIBLE
        widgetContainer2.visibility = View.VISIBLE
    }

    fun onDragFromDrawerEnded() {
        if (!drawerOpen) drawerOverlay.alpha = 1f
    }

    fun getCurrentCols(): Int = currentCols
    fun getCurrentRows(): Int = currentRows

    fun moveWidget(widgetId: Int, row: Int, col: Int, pageIndex: Int) {
        val widgets = PrefsSync.widgetSlots(this).toMutableList()
        val index = widgets.indexOfFirst { it.appWidgetId == widgetId }
        if (index == -1) return
        val slot = widgets[index]

        val newRow = (row - (slot.heightCells - 1) / 2).coerceIn(0, currentRows - slot.heightCells)
        val newCol = (col - (slot.widthCells - 1) / 2).coerceIn(0, currentCols - slot.widthCells)

        val occupiedCells = mutableSetOf<Pair<Int, Int>>()
        val slots = PrefsSync.homeSlots(this)
        slots.filter { it.page == pageIndex }.forEach { occupiedCells.add(it.row to it.col) }
        widgets.filter { it.page == pageIndex && it.appWidgetId != widgetId }.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupiedCells.add(r to c)
                }
            }
        }

        var canMove = true
        for (r in newRow until newRow + slot.heightCells) {
            for (c in newCol until newCol + slot.widthCells) {
                if (occupiedCells.contains(r to c)) {
                    canMove = false
                    break
                }
            }
            if (!canMove) break
        }

        if (canMove) {
            widgets[index] = slot.copy(row = newRow, col = newCol, page = pageIndex)
            PrefsSync.setWidgetSlots(this, widgets)
            renderWidgets()
        } else {
            Toast.makeText(this, "Spazio occupato", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDropOnHome(source: String, pkg: String, x: Float, y: Float): Boolean {
        if (isDropInProgress) return false
        isDropInProgress = true
        val pageIndex = homeScreenGrid.getCurrentPage()

        val loc = IntArray(2)
        homeScreenGrid.getLocationOnScreen(loc)
        val localX = x - loc[0]
        val localY = y - loc[1]

        val cellW = homeScreenGrid.width / currentCols
        val cellH = homeScreenGrid.height / currentRows
        val col = (localX / cellW).toInt().coerceIn(0, currentCols - 1)
        val row = (localY / cellH).toInt().coerceIn(0, currentRows - 1)

        val widgetSlots = PrefsSync.widgetSlots(this)
        val widgetCells = mutableSetOf<Pair<Int, Int>>()
        widgetSlots.filter { it.page == pageIndex }.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    widgetCells.add(r to c)
                }
            }
        }

        if (widgetCells.contains(row to col)) {
            var found = false
            val currentSlots = PrefsSync.homeSlots(this)
            for (r in 0 until currentRows) {
                for (c in 0 until currentCols) {
                    val occupiedBySlot = currentSlots.any {
                        it.page == pageIndex && it.row == r && it.col == c && it.packageName != pkg
                    }
                    val occupiedByWidget = widgetCells.contains(r to c)
                    if (!occupiedBySlot && !occupiedByWidget) {
                        moveToCell(pkg, r, c, pageIndex)
                        found = true
                        break
                    }
                }
                if (found) break
            }
            isDropInProgress = false
            return true
        }

        moveToCell(pkg, row, col, pageIndex)
        isDropInProgress = false
        return true
    }

    private fun moveToCell(pkg: String, row: Int, col: Int, pageIndex: Int) {
        val slots = PrefsSync.homeSlots(this).toMutableList()

        val existingSlot = slots.firstOrNull {
            it.page == pageIndex && it.row == row && it.col == col && it.packageName != pkg
        }
        val sourceSlot = slots.firstOrNull { it.packageName == pkg }

        slots.removeAll { it.packageName == pkg }

        if (existingSlot != null) {
            if (sourceSlot != null) {
                slots.removeAll { it.page == sourceSlot.page && it.row == sourceSlot.row && it.col == sourceSlot.col }
                slots.add(existingSlot.copy(row = sourceSlot.row, col = sourceSlot.col, page = sourceSlot.page))
            } else {
                slots.removeAll { it.page == pageIndex && it.row == row && it.col == col }
            }
        }

        slots.add(HomeSlot(pkg, row, col, pageIndex))
        PrefsSync.setHomeSlots(this, slots)

        val dockItems = PrefsSync.dockItems(this)
        if (dockItems.contains(pkg)) {
            PrefsSync.setDockItems(this, dockItems.map { if (it == pkg) "" else it })
            dockController.refresh()
        }

        homeScreenGrid.setGridSize(currentCols, currentRows)
        homeScreenGrid.setPageCount(currentPageCount)
        homeScreenGrid.setItems(PrefsSync.homeSlots(this))
    }

    private fun handleDropOnDrawer(pkg: String) {
        val slots = PrefsSync.homeSlots(this)
        PrefsSync.setHomeSlots(this, slots.filter { it.packageName != pkg })
        val dockItems = PrefsSync.dockItems(this)
        PrefsSync.setDockItems(this, dockItems.map { if (it == pkg) "" else it })
        homeScreenGrid.setItems(PrefsSync.homeSlots(this))
        dockController.refresh()
    }

    private fun loadHomeApps() {
        val slots = PrefsSync.homeSlots(this)
        val maxPage = slots.maxOfOrNull { it.page } ?: 0
        val effectivePageCount = maxOf(currentPageCount, maxPage + 1)
        if (effectivePageCount != currentPageCount) {
            currentPageCount = effectivePageCount
            PrefsSync.setPageCount(this, currentPageCount)
            pageIndicator.setPageCount(currentPageCount)
            homeScreenGrid.setPageCount(currentPageCount)
        }

        if (AppCache.size() == 0) {
            AppRepository.loadInstalledAppsWithCustomLabels(this)
        }

        homeScreenGrid.setGridSize(currentCols, currentRows)
        homeScreenGrid.setPageCount(currentPageCount)
        homeScreenGrid.setItems(slots)

        pageIndicator.setPageCount(currentPageCount)
        pageIndicator.setCurrentPage(homeScreenGrid.getCurrentPage().coerceIn(0, currentPageCount - 1))

        if (!drawerOpen) {
            emptyMessage.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun reloadHomeGrid() { loadHomeApps() }

    fun removeAppFromGridOnly(pkg: String) {
        val allSlots = PrefsSync.homeSlots(this)
        homeScreenGrid.setItems(allSlots.filter { it.packageName != pkg })
    }

    private fun loadDrawerApps() {
        lifecycleScope.launch {
            val apps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            drawerAdapter.submitList(apps)
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        homeScreenGrid.visibility = View.INVISIBLE
        widgetContainer.visibility = View.INVISIBLE
        widgetContainer2.visibility = View.INVISIBLE
        pageIndicator.visibility = View.INVISIBLE
        dockLayout.visibility = View.INVISIBLE
        emptyMessage.visibility = View.INVISIBLE
        resizeOverlay.visibility = View.GONE
        resizeFrame.visibility = View.GONE
        applyDrawerBackground()
        drawerOverlay.alpha = 1f
        drawerOverlay.visibility = View.VISIBLE
        drawerOverlay.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_up))
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawerOverlay.visibility = View.GONE
        homeScreenGrid.visibility = View.VISIBLE
        widgetContainer.visibility = View.VISIBLE
        widgetContainer2.visibility = View.VISIBLE
        pageIndicator.visibility = View.VISIBLE
        dockLayout.visibility = View.VISIBLE
    }

    private fun applyDrawerBackground() {
        if (drawerBgApplied) return
        lifecycleScope.launch {
            currentDrawerColor = Prefs.colorFlow(this@HomeActivity).first()
            currentDrawerAlpha = Prefs.alphaFlow(this@HomeActivity).first()
            drawerCard.setCardBackgroundColor(
                Color.argb(
                    (currentDrawerAlpha * 255).toInt(),
                    Color.red(currentDrawerColor),
                    Color.green(currentDrawerColor),
                    Color.blue(currentDrawerColor)
                )
            )
            drawerBgApplied = true
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::dragController.isInitialized && !isResizing && dragController.onTouch(ev)) return true

        if (::contextMenuView.isInitialized && contextMenuView.isShowing()) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                if (!contextMenuView.isPointInside(ev.rawX, ev.rawY)) {
                    contextMenuView.dismiss()
                    currentApp = null
                    currentAnchor = null
                }
            }
        }

        if (!drawerOpen && ev.actionMasked == MotionEvent.ACTION_DOWN && !isResizing) {
            val loc = IntArray(2)
            widgetContainer.getLocationOnScreen(loc)
            val localX = ev.rawX - loc[0]
            val localY = ev.rawY - loc[1]

            var targetView: View? = null
            for (i in 0 until widgetContainer.childCount) {
                val child = widgetContainer.getChildAt(i)
                if (child.visibility == View.VISIBLE &&
                    localX >= child.x && localX <= child.x + child.width &&
                    localY >= child.y && localY <= child.y + child.height
                ) {
                    targetView = child
                    break
                }
            }

            if (targetView != null) {
                (targetView as? LauncherAppWidgetHostView)?.dispatchTouchEvent(ev)
                return true
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isResizing) { endResizeMode(); return }
        if (::contextMenuView.isInitialized && contextMenuView.isShowing()) {
            contextMenuView.dismiss(); currentApp = null; currentAnchor = null; return
        }
        if (widgetPopupWindow?.isShowing == true) { widgetPopupWindow?.dismiss(); return }
        if (::homePopupMenu.isInitialized && homePopupMenu.isShowing()) { homePopupMenu.dismiss(); return }
        if (drawerOpen) closeDrawer()
    }

    override fun onResume() {
        super.onResume()
        if (drawerOpen) closeDrawer()
        drawerBgApplied = false

        AppRepository.invalidateCache()

        currentCols = PrefsSync.cols(this)
        currentRows = PrefsSync.rows(this)
        currentPageCount = PrefsSync.pageCount(this)

        homeScreenGrid.setGridSize(currentCols, currentRows)
        homeScreenGrid.setPageCount(currentPageCount)

        lifecycleScope.launch {
            AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            loadHomeApps()
            dockController.refresh()
            loadDrawerApps()
            renderWidgets()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    private fun askLauncherIfNeeded() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(homeIntent, 0)
        if (res?.activityInfo?.packageName == packageName) return
        val prefs = getSharedPreferences("launcher_flags", MODE_PRIVATE)
        if (prefs.getBoolean("asked_launcher", false)) return
        prefs.edit().putBoolean("asked_launcher", true).apply()
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
        }
    }
}
