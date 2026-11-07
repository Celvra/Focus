package fan.x0.focus.feature.assistant.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.overlay.FocusOverlay
import kotlinx.coroutines.launch

@Composable
internal fun SettingsOverlayPage(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val roleIntent = remember(context) { assistantRoleIntent(context) }
    val colorOs = remember { isColorOsDevice() }
    var canDraw by remember { mutableStateOf(FocusOverlay.canDraw(context)) }
    var isDefault by remember { mutableStateOf(isDefaultAssistant(context)) }
    var powerHijack by remember { mutableStateOf(hijackLoaded(context, HIJACK_SYSTEM)) }
    var uiHijack by remember { mutableStateOf(hijackLoaded(context, HIJACK_SYSTEM_UI)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultAssistant(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDraw = FocusOverlay.canDraw(context)
                isDefault = isDefaultAssistant(context)
                powerHijack = hijackLoaded(context, HIJACK_SYSTEM)
                uiHijack = hijackLoaded(context, HIJACK_SYSTEM_UI)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val pinned = stringResource(R.string.settings_overlay_shortcut_pinned)
    val unsupported = stringResource(R.string.settings_overlay_shortcut_unsupported)
    val startFailed = stringResource(R.string.settings_overlay_start_failed)
    val unavailable = stringResource(R.string.settings_assistant_unavailable)
    val requestDefault: () -> Unit = {
        val intent = roleIntent ?: assistantSettingsIntent()
        runCatching { roleLauncher.launch(intent) }
            .onFailure { scope.launch { snackbar.showSnackbar(unavailable) } }
    }
    SettingsPageScaffold(
        title = stringResource(R.string.settings_overlay_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_overlay_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "permission") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_overlay_permission_title)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.settings_overlay_permission)) },
                        supportingContent = {
                            Text(
                                if (canDraw) {
                                    stringResource(R.string.settings_overlay_permission_granted)
                                } else {
                                    stringResource(R.string.settings_overlay_permission_needed)
                                },
                            )
                        },
                        leadingContent = {
                            FocusCardGroupIcon(icon = FocusIcons.Visibility)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = if (canDraw) FocusIcons.Check else FocusIcons.Chevron,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { FocusOverlay.openPermission(context) },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.settings_overlay_shortcut)) },
                        supportingContent = { Text(stringResource(R.string.settings_overlay_shortcut_body)) },
                        leadingContent = {
                            FocusCardGroupIcon(icon = FocusIcons.Add)
                        },
                        onClick = {
                            if (FocusOverlay.pinShortcut(context)) {
                                scope.launch { snackbar.showSnackbar(pinned) }
                            } else {
                                scope.launch { snackbar.showSnackbar(unsupported) }
                            }
                        },
                    )
                }
            }
        }
        item(key = "try") {
            FocusReveal(index = 2) {
                Button(
                    onClick = {
                        if (!FocusOverlay.canDraw(context)) {
                            FocusOverlay.openPermission(context)
                            return@Button
                        }
                        if (!FocusOverlay.show(context)) {
                            scope.launch { snackbar.showSnackbar(startFailed) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        imageVector = FocusIcons.Play,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_overlay_try))
                }
            }
        }
        item(key = "try_body") {
            FocusReveal(index = 3) {
                Text(
                    text = stringResource(R.string.settings_overlay_try_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "assistant") {
            FocusReveal(index = 4) {
                FocusCardGroup(title = stringResource(R.string.settings_assistant_title)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.settings_assistant_status)) },
                        supportingContent = {
                            Text(
                                if (isDefault) {
                                    stringResource(R.string.settings_assistant_status_on)
                                } else {
                                    stringResource(R.string.settings_assistant_status_off)
                                },
                            )
                        },
                        leadingContent = {
                            FocusCardGroupIcon(icon = FocusIcons.Assistant)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = if (isDefault) FocusIcons.Check else FocusIcons.Chevron,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = if (isDefault) null else requestDefault,
                    )
                }
            }
        }
        if (!isDefault) {
            item(key = "assistant_action") {
                FocusReveal(index = 5) {
                    Button(
                        onClick = requestDefault,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(
                            imageVector = FocusIcons.Assistant,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (roleIntent != null) {
                                    R.string.settings_assistant_set
                                } else {
                                    R.string.settings_assistant_open_settings
                                },
                            ),
                        )
                    }
                }
            }
        }
        item(key = "assistant_hint") {
            FocusReveal(index = 6) {
                Text(
                    text = stringResource(R.string.settings_assistant_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (colorOs) {
            item(key = "hijack") {
                FocusReveal(index = 7) {
                    FocusCardGroup(title = stringResource(R.string.settings_assistant_hijack_title)) {
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_assistant_hijack_power)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (powerHijack) {
                                            R.string.settings_assistant_hijack_loaded
                                        } else {
                                            R.string.settings_assistant_hijack_missing
                                        },
                                    ),
                                )
                            },
                            leadingContent = {
                                FocusCardGroupIcon(icon = FocusIcons.Bolt)
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = if (powerHijack) FocusIcons.Check else FocusIcons.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_assistant_hijack_system_ui)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (uiHijack) {
                                            R.string.settings_assistant_hijack_loaded
                                        } else {
                                            R.string.settings_assistant_hijack_missing
                                        },
                                    ),
                                )
                            },
                            leadingContent = {
                                FocusCardGroupIcon(icon = FocusIcons.Assistant)
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = if (uiHijack) FocusIcons.Check else FocusIcons.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
            item(key = "hijack_hint") {
                FocusReveal(index = 8) {
                    Text(
                        text = stringResource(R.string.settings_assistant_hijack_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

private fun isDefaultAssistant(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val manager = context.getSystemService(RoleManager::class.java) ?: return false
    return manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
}

private fun assistantRoleIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val manager = context.getSystemService(RoleManager::class.java) ?: return null
    if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
    return runCatching { manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT) }.getOrNull()
}

private fun assistantSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

private fun isColorOsDevice(): Boolean {
    val names = setOf("oppo", "oneplus", "realme", "oplus")
    return Build.MANUFACTURER.lowercase() in names || Build.BRAND.lowercase() in names
}

private fun hijackLoaded(context: Context, key: String): Boolean = runCatching {
    val raw = Settings.Secure.getString(context.contentResolver, key) ?: return@runCatching false
    val parts = raw.split(':', limit = 2)
    if (parts.size != 2) return@runCatching false
    val bootCount = parts[0].toIntOrNull() ?: return@runCatching false
    val writtenAt = parts[1].toLongOrNull() ?: return@runCatching false
    val currentBoot = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        -1,
    )
    bootCount == currentBoot && writtenAt <= SystemClock.elapsedRealtime()
}.getOrDefault(false)

private const val HIJACK_SYSTEM = "focus_hijack_system"
private const val HIJACK_SYSTEM_UI = "focus_hijack_systemui"
