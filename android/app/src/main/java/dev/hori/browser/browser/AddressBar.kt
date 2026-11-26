package dev.hori.browser.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Address bar: a compact rounded field. Collapsed, it shows the page location
 * with a security glyph; tapped, it becomes an editable single-line field whose
 * text is interpreted by Rust on submit. A Home button sits to the right.
 */
@Composable
fun AddressBar(
    url: String,
    title: String,
    loading: Boolean,
    editing: String?,
    onStartEditing: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onSubmit: (String) -> Unit,
    onEditChange: (String) -> Unit,
    onClearEditing: () -> Unit,
    onHome: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                AnimatedContent(
                    targetState = editing != null,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "addressMode",
                ) { isEditing ->
                    if (isEditing) {
                        EditField(
                            value = editing.orEmpty(),
                            onChange = onEditChange,
                            onClose = onCancelEditing,
                            onClear = onClearEditing,
                            onSubmit = { onSubmit(editing.orEmpty()) },
                        )
                    } else {
                        CollapsedBar(
                            url = url,
                            title = title,
                            loading = loading,
                            onClick = { onStartEditing(url) },
                        )
                    }
                }
            }
            IconButton(onClick = onHome) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CollapsedBar(
    url: String,
    title: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val (glyph, tint) = when {
            loading -> Icons.Filled.Search to MaterialTheme.colorScheme.onSurfaceVariant
            url.startsWith("https://") -> Icons.Filled.Lock to MaterialTheme.colorScheme.primary
            url.isBlank() -> Icons.Filled.Search to MaterialTheme.colorScheme.onSurfaceVariant
            else -> Icons.Filled.Language to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = displayUrl(url, title, loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditField(
    value: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) {
            field = TextFieldValue(value, TextRange(0, value.length))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))

        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onChange(it.text)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            decorationBox = { inner ->
                if (field.text.isEmpty()) {
                    Text(
                        text = "Search or enter address",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )

        // With text: cross clears it. Empty: cross exits editing mode.
        IconButton(
            onClick = {
                if (field.text.isNotEmpty()) {
                    field = TextFieldValue("")
                    onClear()
                } else {
                    onClose()
                }
            },
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = if (field.text.isNotEmpty()) "Clear" else "Cancel",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

internal fun displayUrl(url: String, title: String, loading: Boolean): String = when {
    loading && url.isEmpty() -> "Loading…"
    url.isEmpty() -> title.ifEmpty { "Hori Browser" }
    else -> url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
}