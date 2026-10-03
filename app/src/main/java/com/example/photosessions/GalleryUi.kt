package com.example.photosessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

// A quiet gallery palette: neutral surfaces keep the photographs in focus.
@Composable
internal fun PhotoSessionsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF245BDD), onPrimary = Color.White,
            primaryContainer = Color(0xFFE8EEFF), onPrimaryContainer = Color(0xFF173C91),
            secondary = Color(0xFF526078), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDF0F6), onSecondaryContainer = Color(0xFF26344C),
            tertiary = Color(0xFF24704B), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE5F3EA), onTertiaryContainer = Color(0xFF185234),
            background = Color(0xFFF7F8FA), onBackground = Color(0xFF202124),
            surface = Color.White, onSurface = Color(0xFF202124),
            surfaceVariant = Color(0xFFEDF0F5), onSurfaceVariant = Color(0xFF5B6372),
            surfaceContainer = Color(0xFFF1F3F7), surfaceContainerLow = Color(0xFFF7F8FA),
            surfaceContainerHigh = Color(0xFFEDF0F5), surfaceContainerHighest = Color(0xFFE7EAF0),
            outline = Color(0xFF747D8C), outlineVariant = Color(0xFFDDE1E8)
        ),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
        ), content = content
    )
}

@Composable
internal fun AlbumIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(R.drawable.ic_session), contentDescription = null, modifier = modifier, tint = tint)
}

@Composable
internal fun SessionBadge(active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(color = if(active) colors.tertiaryContainer else colors.secondaryContainer, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if(active) Box(Modifier.size(6.dp).background(colors.tertiary, CircleShape))
            Text(if(active) "Active" else "Complete", style = MaterialTheme.typography.labelMedium,
                color = if(active) colors.onTertiaryContainer else colors.onSecondaryContainer)
        }
    }
}

@Composable
internal fun EmptyGallery(title: String, message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            AlbumIcon(Modifier.padding(26.dp).size(44.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(message, Modifier.widthIn(max = 360.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

@Composable
internal fun StartSessionButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Start a session")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionLibrary(
    catalog: Catalog, refresh: Long, driveAccount: String?, busy: Boolean,
    onStart: () -> Unit, onOpen: (Long) -> Unit, status: @Composable () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val photosBySession = remember(catalog.photos) { catalog.photos.groupBy { it.sessionId } }
    val sessions = catalog.sessions.filter { when(filter) { "Active" -> !it.complete; "Complete" -> it.complete; else -> true } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.padding(end = 24.dp)) {
                    Text("Sessions", style = MaterialTheme.typography.headlineLarge)
                    Text(if(catalog.sessions.isEmpty()) "Your photos, grouped together" else "${catalog.sessions.size} sessions  •  ${catalog.photos.size} photos",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(catalog.sessions.isNotEmpty()) StartSessionButton(!busy, onStart)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { status() }
        if(catalog.sessions.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Complete").forEach { label ->
                    FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) })
                }
            }
        }
        if(sessions.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyGallery(
                title = if(catalog.sessions.isEmpty()) "Your first session starts here" else "No ${filter.lowercase()} sessions",
                message = if(catalog.sessions.isEmpty()) "Name a session, then take photos with your Camera app. Find them all together here." else "Choose All to see the rest of your sessions."
            ) {
                if(catalog.sessions.isEmpty()) StartSessionButton(!busy, onStart)
                else TextButton(onClick = { filter = "All" }) { Text("Show all sessions") }
            }
        }
        items(sessions, key = { it.id }) { session ->
            val photos = photosBySession[session.id].orEmpty()
            Card(onClick = { onOpen(session.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background), shape = RoundedCornerShape(16.dp)) {
                Box {
                    Thumbnail(photos.firstOrNull { it.available } ?: photos.firstOrNull(), refresh, Modifier.fillMaxWidth().aspectRatio(1f))
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { SessionBadge(!session.complete) }
                }
                Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(session.name ?: "Unnamed session", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${photos.size} ${if(photos.size == 1) "photo" else "photos"}  •  ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.start))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val uploaded = photos.count { it.driveState == "uploaded" && driveAccount != null && it.driveAccount == driveAccount }
                    if(uploaded > 0) Text("$uploaded in Drive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun MonitoringStatus(message: String?, onCheck: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Photo discovery is on", style = MaterialTheme.typography.bodyMedium)
                if(message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCheck) { Icon(Icons.Outlined.Refresh, "Check now") }
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Photos remain lazy in both layouts, including large sessions. */
@Composable
internal fun SessionGallery(
    sessionId: Long, photos: List<Photo>, refresh: Long, transferSummary: String,
    header: @Composable () -> Unit, tools: @Composable () -> Unit, onPhoto: (Photo) -> Unit
) {
    var showTools by rememberSaveable(sessionId) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
                if(!wide) item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            TextButton(onClick = { showTools = !showTools }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text("Upload & copies", style = MaterialTheme.typography.titleSmall)
                                    Text(transferSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if(showTools) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    if(showTools) "Hide upload and copy controls" else "Show upload and copy controls")
                            }
                            if(showTools) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { tools() }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Photos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                if(photos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGallery("No photos yet", "Take photos with your Camera app. New photos will appear in this session while it is active.")
                }
                items(photos, key = { it.key }) { photo ->
                    Card(onClick = { onPhoto(photo) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                        Thumbnail(photo, refresh, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(when(photo.copyState) { "copied" -> "Copied"; "failed" -> "Copy failed · retry"; "copying" -> "Needs retry"; else -> "Not copied" },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                    }
                }
            }
            if(wide) Surface(modifier = Modifier.width(320.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Upload & copies", style = MaterialTheme.typography.titleLarge)
                    tools()
                }
            }
        }
    }
}
