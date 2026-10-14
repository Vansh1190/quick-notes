package com.vansh.quicknotes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vansh.quicknotes.data.settings.SettingsRepository
import com.vansh.quicknotes.ui.design.LocalQNPalette
import com.vansh.quicknotes.ui.design.QNType
import com.vansh.quicknotes.ui.design.qnPressable

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val palette = LocalQNPalette.current

    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var model by remember(state.model) { mutableStateOf(state.model) }
    var apiKey by remember(state.hasApiKey) { mutableStateOf(if (state.hasApiKey) "••••••••" else "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .testTag("settings_root"),
    ) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                style = QNType.largeTitle,
                color = palette.accent,
                modifier = Modifier.qnPressable(onClick = onBack).testTag("settings_back"),
            )
            Spacer(Modifier.width(10.dp))
            Text("Settings", style = QNType.largeTitle, color = palette.textPrimary, modifier = Modifier.testTag("settings_title"))
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("AI (your endpoint)")
        SettingsCard {
            LabeledField("Base URL", SettingsRepository.DefaultBaseUrl, baseUrl, { baseUrl = it; viewModel.saveBaseUrl(it) }, "settings_base_url")
            LabeledField("Model", "cora_chat", model, { model = it; viewModel.saveModel(it) }, "settings_model")
            LabeledField(
                "API key",
                if (state.hasApiKey) "saved (keystore)" else "sk-...",
                apiKey,
                { apiKey = it; if (!it.all { c -> c == '•' }) viewModel.saveApiKey(it) },
                "settings_api_key",
                password = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accent)
                        .qnPressable(enabled = state.baseUrl.isNotBlank() && state.hasApiKey, onClick = viewModel::runTest)
                        .padding(horizontal = 22.dp)
                        .testTag("settings_test_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (testState) {
                            is SettingsViewModel.TestState.Running -> "Testing…"
                            else -> "Test connection"
                        },
                        style = QNType.subheadline,
                        color = palette.onAccent,
                    )
                }
                Spacer(Modifier.width(12.dp))
                when (val t = testState) {
                    is SettingsViewModel.TestState.Ok -> Text("✓ ${t.reply.take(40)}", style = QNType.caption, color = palette.success, modifier = Modifier.testTag("settings_test_ok"))
                    is SettingsViewModel.TestState.Failed -> Text("✗ ${t.message}", style = QNType.caption, color = palette.destructive, modifier = Modifier.testTag("settings_test_fail"))
                    else -> Unit
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingRow("Server-side transcription", "endpoint has no /audio/transcriptions — leave off", state.serverTranscription, viewModel::saveServerTranscription, "settings_server_stt")
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Appearance")
        SettingsCard {
            SettingRow("Reduce motion", "fades instead of springs", state.reduceMotion, viewModel::saveReduceMotion, "settings_reduce_motion")
            Text(
                "Accent: Apple Notes yellow (v1 fixed)",
                style = QNType.caption,
                color = palette.textTertiary,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Backup")
        val backupState by viewModel.backupState.collectAsStateWithLifecycle()
        val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.importBackup(it, resolver) } }
        SettingsCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(palette.accent)
                        .qnPressable(enabled = backupState !is SettingsViewModel.BackupState.Busy, onClick = viewModel::exportBackup)
                        .testTag("settings_export"),
                    contentAlignment = Alignment.Center,
                ) { Text("Export ZIP", style = QNType.subheadline, color = palette.onAccent) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(palette.surface)
                        .qnPressable(enabled = backupState !is SettingsViewModel.BackupState.Busy) { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                        .testTag("settings_import"),
                    contentAlignment = Alignment.Center,
                ) { Text("Import ZIP", style = QNType.subheadline, color = palette.textPrimary) }
            }
            Spacer(Modifier.height(8.dp))
            when (val b = backupState) {
                is SettingsViewModel.BackupState.Busy -> Text("Working…", style = QNType.caption, color = palette.textTertiary)
                is SettingsViewModel.BackupState.Exported -> Text("Saved ${b.notes} notes → ${b.path}", style = QNType.caption, color = palette.success, modifier = Modifier.testTag("settings_backup_ok"))
                is SettingsViewModel.BackupState.Imported -> Text("Imported ${b.notes} notes (${b.skipped} already existed)", style = QNType.caption, color = palette.success, modifier = Modifier.testTag("settings_backup_ok"))
                is SettingsViewModel.BackupState.Failed -> Text(b.message, style = QNType.caption, color = palette.destructive)
                else -> Text("Notes + media + tags as one ZIP, with a Markdown copy per note inside. Keys never included.", style = QNType.caption, color = palette.textTertiary)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Quick access")
        SettingsCard {
            Text(
                "Widgets: home-screen picker → \"Quick Notes\" (3 sizes). QS tile: add \"New note\". " +
                    "Shortcuts: long-press the app icon. Dump & Sort voice: Lists screen.",
                style = QNType.caption,
                color = palette.textSecondary,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalQNPalette.current
    Text(
        text.uppercase(),
        style = QNType.captionTinted,
        color = palette.textTertiary,
        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = LocalQNPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun LabeledField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    password: Boolean = false,
) {
    val palette = LocalQNPalette.current
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, style = QNType.caption, color = palette.textTertiary)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = palette.textTertiary) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = palette.card,
                unfocusedContainerColor = palette.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    val palette = LocalQNPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = QNType.body, color = palette.textPrimary)
            Text(subtitle, style = QNType.caption, color = palette.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            modifier = Modifier.testTag(tag),
        )
    }
}
