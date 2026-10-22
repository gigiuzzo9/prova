package com.simplylauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DrawerScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    val color by Prefs.colorFlow(context).collectAsState(initial = 0xFF000000.toInt())
    val alpha by Prefs.alphaFlow(context).collectAsState(initial = 0.9f)
    val cols by Prefs.colsFlow(context).collectAsState(initial = 4)
    val rows by Prefs.rowsFlow(context).collectAsState(initial = 6)

    val apps = remember { AppRepository.loadInstalledApps(context) }

    val screenHeight = config.screenHeightDp.dp
    val screenWidth = config.screenWidthDp.dp
    val cellWidth = screenWidth / cols
    val cellHeight = (screenHeight / rows) * 0.85f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(color).copy(alpha = alpha))
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppIcon(
                        app = app,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        onClick = {
                            AppRepository.launchApp(context, app.packageName)
                            onClose()
                        },
                        onLongPress = { },
                        onDragStart = { },
                        onDrag = { _, _ -> },
                        onDragEnd = { }
                    )
                }
            }
        }
    }
}
