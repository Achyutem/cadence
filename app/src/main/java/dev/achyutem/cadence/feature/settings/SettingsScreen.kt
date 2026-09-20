package dev.achyutem.cadence.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.datastore.ButtonShape
import dev.achyutem.cadence.core.datastore.ThemeMode
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.labelRes
import dev.achyutem.cadence.core.designsystem.theme.swatch
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.designsystem.token.TouchTarget
import dev.achyutem.cadence.core.time.shortLabel
import java.time.DayOfWeek

/**
 * Settings.
 *
 * Kept deliberately short. Everything here is a preference someone would plausibly change more
 * than once; anything that would only ever be set once belongs in a default, not a switch.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Storage Access Framework: the user picks the file, so Cadence needs no storage permission
    // and never sees anything they did not choose.
    // Requested in context, the first time the user turns a reminder on, never at launch.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.rescheduleReminders() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.export(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.import(context, it) } }

    var confirmImport by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var notificationsAllowed by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    // Re-read on resume: the user may have granted it in system settings and come back.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notificationsAllowed = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    SettingsContent(
        preferences = preferences,
        transfer = transfer,
        onDisplayName = viewModel::setDisplayName,
        onThemeMode = viewModel::setThemeMode,
        onAccent = viewModel::setAccent,
        onDynamicColor = viewModel::setDynamicColor,
        onButtonShape = viewModel::setButtonShape,
        onReducedMotion = viewModel::setReducedMotion,
        onWeekStart = viewModel::setWeekStart,
        onQuietHours = viewModel::setQuietHoursEnabled,
        notificationsAllowed = notificationsAllowed,
        exactAlarmsAllowed = viewModel.canScheduleExact(),
        onRequestNotifications = {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        },
        onRequestExactAlarms = {
            // There is no runtime prompt for exact alarms; the user grants it in system settings.
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        "package:${context.packageName}".toUri(),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        onExport = { exportLauncher.launch(viewModel.suggestedFileName()) },
        onImport = { confirmImport = true },
        onDismissTransfer = viewModel::dismissTransfer,
        modifier = modifier,
    )

    if (confirmImport) {
        // Import is the only destructive action in the app, so it is the only one that asks.
        // The dialog states what will actually happen rather than asking "are you sure?".
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_body)) },
            confirmButton = {
                CadenceButton(
                    text = stringResource(R.string.settings_import_confirm_action),
                    tone = ButtonTone.Danger,
                    onClick = {
                        confirmImport = false
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                )
            },
            dismissButton = {
                CadenceButton(
                    text = stringResource(R.string.action_cancel),
                    tone = ButtonTone.Ghost,
                    onClick = { confirmImport = false },
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = Radius.shapeLg,
        )
    }
}

@Composable
private fun SettingsContent(
    preferences: UserPreferences,
    transfer: DataTransferState,
    onDisplayName: (String) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onButtonShape: (ButtonShape) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onWeekStart: (DayOfWeek) -> Unit,
    onQuietHours: (Boolean) -> Unit,
    notificationsAllowed: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = Spacing.screenGutter,
            end = Spacing.screenGutter,
            top = Spacing.xl,
            bottom = Spacing.dockClearance,
        ),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        item(key = "you") {
            SectionHeader(title = stringResource(R.string.settings_you))
            Spacer(Modifier.height(Spacing.xs))
            NameField(value = preferences.displayName, onValueChange = onDisplayName)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.settings_name_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        item(key = "appearance") {
            SectionHeader(title = stringResource(R.string.settings_appearance))
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = ThemeMode.entries,
                selected = preferences.themeMode,
                onSelect = onThemeMode,
                label = { stringResource(it.labelRes()) },
            )
            Spacer(Modifier.height(Spacing.md))
            AccentRow(
                selected = preferences.accentColor,
                enabled = !preferences.useDynamicColor,
                onSelect = onAccent,
            )
            Spacer(Modifier.height(Spacing.sm))
            ToggleRow(
                title = stringResource(R.string.settings_dynamic_color),
                description = stringResource(R.string.settings_dynamic_color_description),
                checked = preferences.useDynamicColor,
                onCheckedChange = onDynamicColor,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.settings_button_shape),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = ButtonShape.entries,
                selected = preferences.buttonShape,
                onSelect = onButtonShape,
                label = { stringResource(it.labelRes()) },
            )
            Spacer(Modifier.height(Spacing.sm))

            ToggleRow(
                title = stringResource(R.string.settings_reduce_motion),
                description = stringResource(R.string.settings_reduce_motion_description),
                checked = preferences.reducedMotion,
                onCheckedChange = onReducedMotion,
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        item(key = "behaviour") {
            SectionHeader(title = stringResource(R.string.settings_behaviour))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_week_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                selected = preferences.weekStartsOn,
                onSelect = onWeekStart,
                label = { it.shortLabel() },
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        item(key = "notifications") {
            SectionHeader(title = stringResource(R.string.settings_notifications))
            Spacer(Modifier.height(Spacing.xs))

            if (!notificationsAllowed) {
                PermissionRow(
                    title = stringResource(R.string.settings_notifications_permission),
                    body = stringResource(R.string.settings_notifications_permission_body),
                    onGrant = onRequestNotifications,
                )
                Spacer(Modifier.height(Spacing.xs))
            } else if (!exactAlarmsAllowed) {
                // Only worth mentioning once notifications work at all; two permission prompts
                // stacked together is how people learn to ignore both.
                PermissionRow(
                    title = stringResource(R.string.settings_exact_alarms),
                    body = stringResource(R.string.settings_exact_alarms_body),
                    onGrant = onRequestExactAlarms,
                )
                Spacer(Modifier.height(Spacing.xs))
            }

            ToggleRow(
                title = stringResource(R.string.settings_quiet_hours),
                description = stringResource(R.string.settings_quiet_hours_body),
                checked = preferences.quietHoursEnabled,
                onCheckedChange = onQuietHours,
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        item(key = "data") {
            SectionHeader(title = stringResource(R.string.settings_data))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_data_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CadenceButton(
                    text = stringResource(R.string.settings_export),
                    onClick = onExport,
                    enabled = transfer !is DataTransferState.Working,
                )
                CadenceButton(
                    text = stringResource(R.string.settings_import),
                    onClick = onImport,
                    enabled = transfer !is DataTransferState.Working,
                )
            }
            AnimatedVisibility(
                visible = transfer is DataTransferState.Success || transfer is DataTransferState.Error,
                enter = fadeIn(tween(CadenceTheme.duration(Motion.QUICK))),
                exit = fadeOut(tween(CadenceTheme.duration(Motion.MICRO))),
            ) {
                val isError = transfer is DataTransferState.Error
                val message = when (transfer) {
                    is DataTransferState.Success -> transfer.message
                    is DataTransferState.Error -> transfer.message
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth()
                        .clip(Radius.shapeSm)
                        .clickable(onClick = onDismissTransfer)
                        .padding(vertical = Spacing.xs),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            CadenceTheme.colors.danger
                        } else {
                            CadenceTheme.colors.success
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        item(key = "about") {
            SectionHeader(title = stringResource(R.string.settings_about))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(Radius.shapeMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeMd)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_name_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(UserPreferences.MAX_NAME_LENGTH)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Accent swatches.
 *
 * Selection is a ring that grows around the swatch rather than a tick drawn on top of it, the
 * colour is the content here, and covering it up to say "this one" defeats the purpose.
 */
@Composable
private fun AccentRow(
    selected: AccentColor,
    enabled: Boolean,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ten swatches do not fit one row on a phone, and a horizontally scrolling colour picker
    // hides half the options behind a gesture nobody knows is there. Two rows of five shows
    // everything at once.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        AccentColor.entries.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { accent ->
                    AccentSwatch(
                        accent = accent,
                        selected = accent == selected,
                        enabled = enabled,
                        onClick = { onSelect(accent) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val accentLabel = stringResource(accent.labelRes)

    // The ring is drawn, not bordered. `Modifier.border` animated to 0.dp still strokes a
    // hairline, which put a faint outline around every *unselected* swatch.
    val ringColor = MaterialTheme.colorScheme.onSurface
    val ringProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(CadenceTheme.duration(Motion.QUICK)),
        label = "accentRing",
    )
    val fill = if (enabled) accent.swatch else accent.swatch.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .size(TouchTarget.min)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                this.selected = selected
                this.role = Role.RadioButton
                contentDescription = accentLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        // The ring is drawn *outside* a constant-size swatch rather than inside it. Insetting the
        // fill to make room made the selected colour visibly smaller than its neighbours, a
        // bullseye, when the swatch is the one thing that should not change.
        Canvas(modifier = Modifier.size(34.dp)) {
            val centre = size.minDimension / 2f
            val swatchRadius = 13.dp.toPx()
            val ringStroke = 1.5.dp.toPx()
            drawCircle(color = fill, radius = swatchRadius)
            if (ringProgress > 0f) {
                drawCircle(
                    color = ringColor.copy(alpha = ringProgress),
                    radius = swatchRadius + 3.dp.toPx() * ringProgress,
                    style = Stroke(width = ringStroke),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.shapeSm)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        // The whole row is already clickable and carries the Switch role, so the switch itself is
        // removed from the accessibility tree to avoid announcing the same control twice.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = CadenceTheme.colors.border,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** A permission the app cannot grant itself, stated plainly with one way to fix it. */
@Composable
private fun PermissionRow(title: String, body: String, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.shapeMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeMd)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(Spacing.xs))
        CadenceButton(
            text = stringResource(R.string.action_grant),
            tone = ButtonTone.Primary,
            onClick = onGrant,
        )
    }
}

private fun ButtonShape.labelRes(): Int = when (this) {
    ButtonShape.ROUNDED -> R.string.button_shape_rounded
    ButtonShape.PILL -> R.string.button_shape_pill
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Preview(name = "Settings, light", showBackground = true)
@Composable
private fun SettingsPreviewLight() = CadencePreviewTheme {
    // Named arguments throughout: a preview that breaks every time the screen gains a callback
    // is a preview nobody keeps working.
    SettingsContent(
        preferences = UserPreferences.Default.copy(displayName = "Achyutem"),
        transfer = DataTransferState.Idle,
        onDisplayName = {},
        onThemeMode = {},
        onAccent = {},
        onDynamicColor = {},
        onButtonShape = {},
        onReducedMotion = {},
        onWeekStart = {},
        onQuietHours = {},
        notificationsAllowed = true,
        exactAlarmsAllowed = true,
        onRequestNotifications = {},
        onRequestExactAlarms = {},
        onExport = {},
        onImport = {},
        onDismissTransfer = {},
    )
}

@Preview(name = "Settings, dark", showBackground = true)
@Composable
private fun SettingsPreviewDark() = CadencePreviewTheme(dark = true, accent = AccentColor.EMERALD) {
    SettingsContent(
        preferences = UserPreferences.Default.copy(
            accentColor = AccentColor.EMERALD,
            themeMode = ThemeMode.DARK,
        ),
        transfer = DataTransferState.Idle,
        onDisplayName = {},
        onThemeMode = {},
        onAccent = {},
        onDynamicColor = {},
        onButtonShape = {},
        onReducedMotion = {},
        onWeekStart = {},
        onQuietHours = {},
        notificationsAllowed = false,
        exactAlarmsAllowed = false,
        onRequestNotifications = {},
        onRequestExactAlarms = {},
        onExport = {},
        onImport = {},
        onDismissTransfer = {},
    )
}
