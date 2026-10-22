package com.simplylauncher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LauncherRoot(
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            onOpenDrawer = { onDrawerOpenChange(true) },
            onOpenSettings = onOpenSettings
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            DrawerScreen(
                onClose = { onDrawerOpenChange(false) },
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    val cols by Prefs.colsFlow(context).collectAsState(initial = 4)
    val rows by Prefs.rowsFlow(context).collectAsState(initial = 6)
    val homeItems by Prefs.homeItemsFlow(context).collectAsState(initial = emptyList())

    val allApps = remember { AppRepository.loadInstalledApps(context) }
    val homeApps = remember(homeItems, allApps) {
        homeItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }

    val screenHeight = config.screenHeightDp.dp
    val screenWidth = config.screenWidthDp.dp
    val cellWidth = screenWidth / cols
    val cellHeight = (screenHeight / rows) * 0.9f

    Box(Modifier.fillMaxSize()) {

        if (homeApps.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Apri il cassetto e aggiungi le tue app", color = Color.White)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize().padding(top = 40.dp)
            ) {
                items(homeApps, key = { it.packageName }) { app ->
                    AppIcon(
                        app = app,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        onClick = { AppRepository.launchApp(context, app.packageName) },
                        onLongPress = { },
                        onDragStart = { },
                        onDrag = { _, _ -> },
                        onDragEnd = { }
                    )
                }
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 16.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Impostazioni",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(56.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_drawer),
                contentDescription = "Cassetto",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
