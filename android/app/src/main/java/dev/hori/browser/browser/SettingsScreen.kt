package dev.hori.browser.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Settings screen. Currently covers the start page and search engine, both
 * persisted through the Rust core. Reached from the overflow menu.
 */
@Composable
fun SettingsScreen(vm: BrowserViewModel) {
    BackHandler(enabled = true) { vm.closeSettings() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = vm::closeSettings) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(
                title = "Start page",
                description = "The page Hori opens on launch and when you tap Home.",
            ) {
                SettingField(
                    value = vm.homeUrlField,
                    onChange = vm::onHomeUrlFieldChange,
                    label = "Home URL",
                    icon = Icons.Filled.Language,
                    keyboardType = KeyboardType.Uri,
                    onSubmit = { vm.saveHomeUrl() },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveHomeUrl() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save start page") }
            }

            SettingsSection(
                title = "Search engine",
                description = "Used when address-bar text isn't a URL. Put %s where the query goes.",
            ) {
                SettingField(
                    value = vm.searchEngineField,
                    onChange = vm::onSearchEngineFieldChange,
                    label = "Search URL template",
                    icon = Icons.Filled.Search,
                    keyboardType = KeyboardType.Uri,
                    onSubmit = { vm.saveSearchEngine() },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveSearchEngine() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save search engine") }
            }

            vm.settingsMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = vm::resetSettingsToDefaults,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset to defaults")
            }

            HorizontalDivider()
            Text(
                text = "Hori Browser 0.1 — Rust core + GeckoView",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(icon, contentDescription = null) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}