package dev.proxboss.aoe.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenManagement: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }
    var connectionSaved by remember { mutableStateOf(false) }
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            item {
                SectionTitle("Connexion")
                ConnectionForm(
                    baseUrl = baseUrl,
                    token = token,
                    testState = testState,
                    onBaseUrlChange = {
                        viewModel.updateBaseUrl(it)
                        connectionSaved = false
                    },
                    onTokenChange = {
                        viewModel.updateToken(it)
                        connectionSaved = false
                    },
                    onTest = viewModel::testConnection,
                    onSave = {
                        viewModel.saveConnection { connectionSaved = true }
                    },
                    saveLabel = "Enregistrer la connexion",
                )
                if (connectionSaved) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Connexion enregistrée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }

            item {
                SectionTitle("Notifications")
                ToggleRow(
                    title = "Notifications de session",
                    subtitle = "Approbations, erreurs et fins de run",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
                ToggleRow(
                    title = "Plage silencieuse",
                    subtitle = "Aucune alerte pendant cette période",
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = {
                        viewModel.setQuietHours(
                            it,
                            settings.quietStartMinute,
                            settings.quietEndMinute,
                        )
                    },
                )
                if (settings.quietHoursEnabled) {
                    TimeRow(
                        label = "Début",
                        minute = settings.quietStartMinute,
                        onChange = { minute ->
                            viewModel.setQuietHours(true, minute, settings.quietEndMinute)
                        },
                    )
                    TimeRow(
                        label = "Fin",
                        minute = settings.quietEndMinute,
                        onChange = { minute ->
                            viewModel.setQuietHours(true, settings.quietStartMinute, minute)
                        },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Autoriser les notifications Android") }
                } else if (!notificationsAllowed) {
                    Text(
                        "Les notifications sont désactivées dans Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ouvrir les réglages Android") }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }

            item {
                SectionTitle("Serveur")
                OutlinedButton(
                    onClick = onOpenManagement,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gestion du serveur (MCP, skills, plugins, profils, sécurité)") }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }

            item {
                SectionTitle("Apparence")
                ThemeChoice(
                    label = "Système",
                    selected = settings.theme == "system",
                    onClick = { viewModel.setTheme("system") },
                )
                ThemeChoice(
                    label = "Clair",
                    selected = settings.theme == "light",
                    onClick = { viewModel.setTheme("light") },
                )
                ThemeChoice(
                    label = "Sombre",
                    selected = settings.theme == "dark",
                    onClick = { viewModel.setTheme("dark") },
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            item {
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Déconnexion")
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "AoE Companion ${appVersion(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Se déconnecter ?") },
            text = { Text("Le jeton et la configuration de connexion seront effacés de cet appareil.") },
            confirmButton = {
                Button(onClick = {
                    confirmLogout = false
                    viewModel.logout(onLoggedOut)
                }) { Text("Se déconnecter") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimeRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showTimePicker(context, minute, onChange) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            TimeFormat.formatClockMinute(minute),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun showTimePicker(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        minuteOfDay / 60,
        minuteOfDay % 60,
        true,
    ).show()
}

private fun appVersion(context: Context): String {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "?" }
}
