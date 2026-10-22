package com.simplylauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val color by Prefs.colorFlow(context).collectAsState(initial = 0xFF000000.toInt())
    val alpha by Prefs.alphaFlow(context).collectAsState(initial = 0.9f)
    val cols by Prefs.colsFlow(context).collectAsState(initial = 4)
    val rows by Prefs.rowsFlow(context).collectAsState(initial = 6)

    val palette = listOf(
        0xFF000000.toInt(),
        0xFF1E1E1E.toInt(),
        0xFF2E3A59.toInt(),
        0xFF3A2E59.toInt(),
        0xFF593A2E.toInt(),
        0xFF2E593A.toInt()
    )

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Colore sfondo", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palette.forEach { c ->
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color(c), CircleShape)
                        .clickable { scope.launch { Prefs.setColor(context, c) } }
                )
            }
        }

        Text(
            "Trasparenza: ${(alpha * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = alpha,
            onValueChange = { scope.launch { Prefs.setAlpha(context, it) } },
            valueRange = 0.3f..1f
        )

        Text("Colonne: $cols", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(3, 4).forEach { n ->
                FilterChip(
                    selected = cols == n,
                    onClick = { scope.launch { Prefs.setCols(context, n) } },
                    label = { Text("$n") }
                )
            }
        }

        Text("Righe: $rows", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(5, 6, 7).forEach { n ->
                FilterChip(
                    selected = rows == n,
                    onClick = { scope.launch { Prefs.setRows(context, n) } },
                    label = { Text("$n") }
                )
            }
        }
    }
}
