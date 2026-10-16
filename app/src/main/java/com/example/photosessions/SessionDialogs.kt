package com.example.photosessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One column keeps dialog actions readable on phones and at larger text sizes. */
@Composable
internal fun SessionDialog(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp)) {
            Surface(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = actions)
                }
            }
        }
    }
}

@Composable
internal fun DialogAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    outlined: Boolean = false,
    destructive: Boolean = false,
    supportingText: String? = null
) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    val shape = RoundedCornerShape(12.dp)
    val padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    val text: @Composable RowScope.() -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, textAlign = TextAlign.Start)
            if(supportingText != null) Text(supportingText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
        }
    }
    if(outlined) OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = shape, contentPadding = padding, content = text)
    else Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, contentPadding = padding,
        colors = if(destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(), content = text)
}

@Composable
internal fun UploadNetworkDialog(sessionLabel: String, onWifi: () -> Unit, onAnyNetwork: () -> Unit, onDismiss: () -> Unit, googlePhotos: Boolean = false) {
    SessionDialog(title = if(googlePhotos) "Add to Google Photos" else "Upload to Google Drive", onDismissRequest = onDismiss,
        content = {
            if(googlePhotos) {
                Text("Photos go into an album named ${photosAlbumTitle(sessionLabel)}.")
                Text("Photos are sent at original quality and may count toward your Google account storage. Unchanged files are recognized as the same photo; edited versions may be separate photos.")
            } else Text("Photos go to My Drive / $DRIVE_ROOT_FOLDER / ${DriveQuery.folderName(sessionLabel)}.")
            Text(if(googlePhotos) "Photo Sessions skips photos it has already added to this album. Uploading uses about as much data as the photos' size." else "Only photos not already uploaded are sent. Uploading uses about as much data as the photos' size.")
        }, actions = {
            DialogAction("Wait for Wi-Fi", onClick = onWifi)
            DialogAction("Upload now", onClick = onAnyNetwork, outlined = true, supportingText = "Use Wi-Fi or mobile data")
            DialogAction("Cancel", onClick = onDismiss, outlined = true)
        })
}

@Composable
internal fun HoursField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.take(8)) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), label = { Text("Custom hours") }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        isError = value.isNotEmpty() && durationFromHours(value) == null,
        supportingText = { Text("Whole hours only, from 1 to 168.") })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StartSessionDialog(busy: Boolean, onStart: (String, Long) -> Unit, onDismiss: () -> Unit) {
    var title by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var hours by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var presetMinutes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(60) }
    val duration = if(presetMinutes > 0) presetMinutes * 60_000L else durationFromHours(hours)
    SessionDialog(title = "Start a session", onDismissRequest = { if(!busy) onDismiss() }, content = {
        Text("Give it a name and choose how long to keep taking photos. Any active session will be completed.")
        OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), label = { Text("Session name") }, singleLine = true)
        Text("Session length", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(30 to "30 minutes", 60 to "1 hour", 120 to "2 hours").forEach { (minutes, label) ->
                FilterChip(selected = presetMinutes == minutes, onClick = { presetMinutes = minutes; hours = "" },
                    enabled = !busy, label = { Text(label) })
            }
        }
        HoursField(hours, { hours = it; presetMinutes = 0 }, !busy)
        Text("The timer starts when you tap Start session. Taking photos won't reset it.", style = MaterialTheme.typography.bodySmall)
    }, actions = {
        DialogAction("Start session", onClick = { duration?.let { onStart(title.trim(), it) } }, enabled = !busy && title.isNotBlank() && duration != null)
        DialogAction("Cancel", onClick = onDismiss, enabled = !busy, outlined = true)
    })
}

@Composable
internal fun ExtensionChoices(enabled: Boolean, onExtend: (Long) -> Unit) {
    var hours by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Keep this session going", style = MaterialTheme.typography.titleMedium)
        Text("Resume the same session for a fixed time starting now. Any other active session will be completed.", style = MaterialTheme.typography.bodySmall)
        DialogAction("30 minutes", onClick = { onExtend(HOUR / 2) }, enabled = enabled, outlined = true)
        DialogAction("1 hour", onClick = { onExtend(HOUR) }, enabled = enabled, outlined = true)
        DialogAction("2 hours", onClick = { onExtend(2 * HOUR) }, enabled = enabled, outlined = true)
        HoursField(hours, { hours = it }, enabled)
        DialogAction("Resume for custom hours", onClick = { durationFromHours(hours)?.let(onExtend) },
            enabled = enabled && durationFromHours(hours) != null, outlined = true)
    }
}
