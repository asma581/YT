package com.focustube.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustube.app.R
import com.focustube.app.blocker.BlockerAccessibilityService
import com.focustube.app.domain.FocusSource
import com.focustube.app.ui.components.LockedBanner
import com.focustube.app.ui.components.SectionTitle
import com.focustube.app.ui.components.formatClockTime
import com.focustube.app.ui.components.formatRemaining

private val SESSION_LENGTHS_MINUTES = listOf(25, 50, 60, 120, 240)

@Composable
fun FocusScreen(
    onFindSomethingToLearn: () -> Unit,
    viewModel: FocusViewModel = viewModel(factory = FocusViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    FocusContent(
        state = current,
        onStart = viewModel::startSession,
        onEnd = viewModel::endSession,
        onStrictModeChange = viewModel::setStrictMode,
        onFindSomethingToLearn = onFindSomethingToLearn,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusContent(
    state: FocusUiState,
    onStart: (minutes: Int) -> Unit,
    onEnd: () -> Unit,
    onStrictModeChange: (Boolean) -> Unit,
    onFindSomethingToLearn: () -> Unit,
) {
    var selectedMinutes by rememberSaveable { mutableIntStateOf(50) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        BlockerSetupCard()

        StatusCard(state)

        if (state.locked) {
            Spacer(Modifier.height(12.dp))
            LockedBanner(stringResource(R.string.strict_locked_focus))
        }

        SectionTitle(
            stringResource(if (state.status.isActive) R.string.focus_extend_title else R.string.focus_start_title),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SESSION_LENGTHS_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { selectedMinutes = minutes },
                    label = { Text(formatMinutes(minutes)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onStart(selectedMinutes) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (state.status.isActive) R.string.focus_extend_button else R.string.focus_start_button,
                    formatMinutes(selectedMinutes),
                ),
            )
        }
        if (FocusSource.SESSION in state.status.sources) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEnd, enabled = !state.locked, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.focus_end_button))
            }
        }

        SectionTitle(stringResource(R.string.strict_mode_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.strict_mode_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            )
            Switch(
                checked = state.settings.strictMode,
                onCheckedChange = onStrictModeChange,
                // You can always turn strict mode on, but not off while it's protecting you.
                enabled = !state.locked,
            )
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onFindSomethingToLearn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.focus_find_something))
        }
    }
}

@Composable
private fun StatusCard(state: FocusUiState) {
    val active = state.status.isActive
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(if (active) R.string.focus_on else R.string.focus_off),
                style = MaterialTheme.typography.titleLarge,
            )
            val endsAt = state.status.endsAt
            if (active && endsAt != null) {
                Text(
                    formatRemaining(state.now, endsAt),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                val reason = when {
                    FocusSource.SCHEDULE in state.status.sources && FocusSource.SESSION in state.status.sources ->
                        R.string.focus_reason_both
                    FocusSource.SCHEDULE in state.status.sources -> R.string.focus_reason_schedule
                    else -> R.string.focus_reason_session
                }
                Text(stringResource(reason, formatClockTime(endsAt)), style = MaterialTheme.typography.bodyMedium)
            } else {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.focus_off_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Asks the user to switch on the accessibility service. Google Play requires apps to
 * explain clearly why they need accessibility access *before* sending the user there.
 */
@Composable
private fun BlockerSetupCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BlockerAccessibilityService.isEnabled(context)) }
    var showExplanation by remember { mutableStateOf(false) }

    // The user turns the service on in system Settings, so re-check whenever we come back.
    LifecycleResumeEffect(Unit) {
        enabled = BlockerAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.blocker_setup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.blocker_setup_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showExplanation = true }) {
                Text(stringResource(R.string.blocker_setup_button))
            }
        }
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(stringResource(R.string.blocker_disclosure_title)) },
            text = { Text(stringResource(R.string.blocker_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExplanation = false
                    context.startActivity(BlockerAccessibilityService.openSettingsIntent())
                }) { Text(stringResource(R.string.blocker_disclosure_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { showExplanation = false }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
