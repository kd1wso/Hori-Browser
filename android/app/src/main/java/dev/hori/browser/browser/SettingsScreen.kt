package dev.hori.browser.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hori.browser.bridge.HoriCore

/**
 * Settings: appearance (theme), search engine, start page, privacy/history,
 * and an entry point to the About screen.
 */
@Composable
fun SettingsScreen(
    vm: BrowserViewModel,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    BackHandler(enabled = true) { vm.closeSettings() }

    val downloads by vm.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = vm::closeSettings) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
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
            // --- Appearance ----------------------------------------------
            SettingsCard(title = "Оформление") {
                Text(
                    "Тема приложения",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(
                        label = "Системная",
                        icon = Icons.Filled.SettingsBrightness,
                        selected = vm.themeMode == HoriCore.ThemeMode.SYSTEM,
                        onClick = { vm.chooseTheme(HoriCore.ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeChip(
                        label = "Светлая",
                        icon = Icons.Filled.LightMode,
                        selected = vm.themeMode == HoriCore.ThemeMode.LIGHT,
                        onClick = { vm.chooseTheme(HoriCore.ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeChip(
                        label = "Тёмная",
                        icon = Icons.Filled.DarkMode,
                        selected = vm.themeMode == HoriCore.ThemeMode.DARK,
                        onClick = { vm.chooseTheme(HoriCore.ThemeMode.DARK) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // --- Search engine -------------------------------------------
            SettingsCard(
                title = "Поисковая система",
                description = "Выберите движок по умолчанию для поиска из адресной строки.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.searchEngines.forEach { engine ->
                        EngineRow(
                            engine = engine,
                            selected = vm.searchEngineField == engine.urlTemplate,
                            onClick = { vm.selectSearchEngine(engine) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.searchEngineField,
                    onValueChange = vm::onSearchEngineFieldChange,
                    label = { Text("Свой шаблон (URL с %s)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.saveSearchEngine() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveSearchEngine() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить поисковую систему") }
            }

            // --- Start page ----------------------------------------------
            SettingsCard(
                title = "Стартовая страница",
                description = "Что открывается при запуске и по кнопке «Домой».",
            ) {
                OutlinedTextField(
                    value = vm.homeUrlField,
                    onValueChange = vm::onHomeUrlFieldChange,
                    label = { Text("Адрес") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.saveHomeUrl() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveHomeUrl() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить стартовую страницу") }
            }

            // --- Library -------------------------------------------------
            SettingsCard(title = "Библиотека") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NavigationRow(
                        icon = Icons.Filled.Star,
                        title = "Закладки",
                        subtitle = "Сохранено: ${vm.bookmarkCount}",
                        onClick = onOpenBookmarks,
                    )
                    NavigationRow(
                        icon = Icons.Filled.Download,
                        title = "Загрузки",
                        subtitle = if (downloads.isEmpty()) {
                            "Нет загрузок"
                        } else {
                            "В списке: ${downloads.size}"
                        },
                        onClick = onOpenDownloads,
                    )
                    NavigationRow(
                        icon = Icons.Filled.Info,
                        title = "История и недавние сайты",
                        subtitle = "Записей: ${vm.historyCount}",
                        onClick = onOpenHistory,
                    )
                }
            }

            // --- About ---------------------------------------------------
            SettingsCard(title = "О приложении") {
                NavigationRow(
                    icon = Icons.Filled.Info,
                    title = "О браузере Hori",
                    subtitle = "Версия, движок, ядро",
                    onClick = onOpenAbout,
                )
            }

            vm.settingsMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String? = null,
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
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun EngineRow(
    engine: HoriCore.Engine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    RoundedCornerShape(6.dp),
                ),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(engine.name, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}