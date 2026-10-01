package com.focustube.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustube.app.R
import com.focustube.app.domain.YouTubeCategories
import com.focustube.app.ui.components.LockedBanner
import com.focustube.app.ui.components.SectionTitle
import com.focustube.app.ui.components.formatMinuteOfDay
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: run {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val settings = current.settings
    val editable = !current.locked

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)
        if (current.locked) {
            Spacer(Modifier.height(12.dp))
            LockedBanner(stringResource(R.string.strict_locked_settings))
        }

        // --- Schedule ---
        SectionTitle(stringResource(R.string.schedule_title))
        SwitchRow(
            text = stringResource(R.string.schedule_enabled),
            checked = settings.schedule.enabled,
            enabled = editable,
            onCheckedChange = viewModel::setScheduleEnabled,
        )
        DayPicker(
            selected = settings.schedule.days,
            enabled = editable,
            onToggle = viewModel::toggleDay,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            TimeButton(
                label = stringResource(R.string.schedule_from),
                minuteOfDay = settings.schedule.startMinute,
                enabled = editable,
                onPicked = viewModel::setStart,
                modifier = Modifier.weight(1f),
            )
            TimeButton(
                label = stringResource(R.string.schedule_to),
                minuteOfDay = settings.schedule.endMinute,
                enabled = editable,
                onPicked = viewModel::setEnd,
                modifier = Modifier.weight(1f),
            )
        }

        // --- What counts as educational ---
        SectionTitle(stringResource(R.string.categories_title))
        Text(stringResource(R.string.categories_body), style = MaterialTheme.typography.bodySmall)
        YouTubeCategories.ALL.forEach { (id, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = id in settings.allowedCategoryIds,
                    onCheckedChange = { viewModel.setCategoryAllowed(id, it) },
                    enabled = editable,
                )
                Text(name)
            }
        }
        SwitchRow(
            text = stringResource(R.string.block_shorts),
            checked = settings.blockShorts,
            enabled = editable,
            onCheckedChange = viewModel::setBlockShorts,
        )

        // --- Blocked words ---
        SectionTitle(stringResource(R.string.keywords_title))
        Text(stringResource(R.string.keywords_body), style = MaterialTheme.typography.bodySmall)
        KeywordEditor(
            keywords = settings.blockedKeywords.sorted(),
            canRemove = editable,
            onAdd = viewModel::addKeyword,
            onRemove = viewModel::removeKeyword,
        )

        // --- Trusted channels ---
        SectionTitle(stringResource(R.string.trusted_title))
        if (settings.trustedChannels.isEmpty()) {
            Text(stringResource(R.string.trusted_empty), style = MaterialTheme.typography.bodySmall)
        }
        settings.trustedChannels.forEach { channel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.title, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.untrustChannel(channel.id) }, enabled = editable) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                }
            }
        }

        // --- API key ---
        SectionTitle(stringResource(R.string.api_key_title))
        ApiKeyEditor(
            savedKey = settings.apiKeyOverride,
            hasBuiltInKey = current.hasBuiltInApiKey,
            enabled = editable,
            onSave = viewModel::setApiKey,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPicker(selected: Set<DayOfWeek>, enabled: Boolean, onToggle: (DayOfWeek) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                enabled = enabled,
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(
    label: String,
    minuteOfDay: Int,
    enabled: Boolean,
    onPicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, enabled = enabled, modifier = modifier) {
        Text("$label ${formatMinuteOfDay(minuteOfDay)}")
    }
    if (showPicker) {
        val pickerState = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(pickerState.hour * 60 + pickerState.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordEditor(
    keywords: List<String>,
    canRemove: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newKeyword by rememberSaveable { mutableStateOf("") }
    val add = {
        onAdd(newKeyword)
        newKeyword = ""
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        keywords.forEach { keyword ->
            InputChip(
                selected = false,
                onClick = { if (canRemove) onRemove(keyword) },
                label = { Text(keyword) },
                trailingIcon = if (canRemove) {
                    {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.remove),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
    // Adding words only makes the filter stricter, so it's allowed even in strict mode.
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newKeyword,
            onValueChange = { newKeyword = it },
            label = { Text(stringResource(R.string.keywords_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = add, enabled = newKeyword.isNotBlank()) { Text(stringResource(R.string.add)) }
    }
}

@Composable
private fun ApiKeyEditor(savedKey: String, hasBuiltInKey: Boolean, enabled: Boolean, onSave: (String) -> Unit) {
    var key by rememberSaveable(savedKey) { mutableStateOf(savedKey) }
    Text(
        stringResource(if (hasBuiltInKey) R.string.api_key_built_in else R.string.api_key_missing),
        style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.api_key_label)) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onSave(key) }, enabled = enabled && key != savedKey) {
            Text(stringResource(R.string.save))
        }
    }
}
