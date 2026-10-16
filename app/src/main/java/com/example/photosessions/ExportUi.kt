package com.example.photosessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExportCounts(local: Int, drive: Int, photos: Int) {
    // Wrap whole labels only when necessary; never reserve three separate status rows.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(local to "Local", drive to "Drive", photos to "Photos").forEach { (count, destination) ->
            Text("$count in $destination", style = MaterialTheme.typography.labelSmall,
                color = if(count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ExportActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalCopyActions(copied: Int, total: Int, busy: Boolean, named: Boolean,
    onCopy: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit) {
    ExportActionButton("Copy Locally", enabled = !busy && total > 0 && named, onClick = onCopy)
    if(copied > 0) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !busy, onClick = onOpen) { Text("Open Local") }
        if(copied == total) TextButton(enabled = !busy, onClick = onShare) { Text("Share copies…") }
    }
}

@Composable
internal fun LocalCopiesGallery(session: Session, photos: List<Photo>, refresh: Long, folder: String?, onPhoto: (Photo) -> Unit) {
    val copies = photos.filter { it.copyState == "copied" && it.copyUri != null }
    LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(SessionLabels.title(session), style = MaterialTheme.typography.headlineSmall)
                Text("${copies.size} local ${if(copies.size == 1) "copy" else "copies"}", style = MaterialTheme.typography.titleSmall)
                folder?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if(copies.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyGallery("No local copies yet", "Return to the session and choose Copy Locally.")
        }
        items(copies, key = { it.key }) { photo ->
            Card(onClick = { onPhoto(photo) }, shape = RoundedCornerShape(12.dp)) {
                Thumbnail(photo.copy(uri = photo.copyUri!!, available = true), refresh, Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
    }
}
