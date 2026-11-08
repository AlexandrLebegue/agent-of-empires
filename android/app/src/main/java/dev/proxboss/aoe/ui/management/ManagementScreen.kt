package dev.proxboss.aoe.ui.management

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.model.McpSecretEntry
import dev.proxboss.aoe.model.McpServerInput
import dev.proxboss.aoe.model.McpServerView
import dev.proxboss.aoe.model.PluginInfo
import dev.proxboss.aoe.model.SkillDetail
import dev.proxboss.aoe.model.SkillSummary
import dev.proxboss.aoe.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    viewModel: ManagementViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var mcpDialog by remember { mutableStateOf<McpServerView?>(null) }
    var mcpCreateOpen by remember { mutableStateOf(false) }
    var skillCreateOpen by remember { mutableStateOf(false) }
    var skillDetail by remember { mutableStateOf<SkillDetail?>(null) }
    var profileCreateOpen by remember { mutableStateOf(false) }
    var profileRename by remember { mutableStateOf<String?>(null) }
    var profileDelete by remember { mutableStateOf<String?>(null) }
    var deviceRevoke by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(ui.skillDetail) {
        ui.skillDetail?.let { skillDetail = it }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Gestion du serveur") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshCurrent) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = ui.tab.ordinal,
                edgePadding = 8.dp,
            ) {
                ManagementTab.entries.forEach { tab ->
                    Tab(
                        selected = ui.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (ui.tab) {
                ManagementTab.SERVER -> ServerTab(ui, context)
                ManagementTab.MCP -> McpTab(
                    ui = ui,
                    onCreate = { mcpCreateOpen = true },
                    onEdit = { mcpDialog = it },
                    onDelete = viewModel::deleteMcp,
                    onResolve = viewModel::resolveMcpConflict,
                    onKeep = viewModel::keepMcp,
                    onDrop = viewModel::dropMcp,
                )
                ManagementTab.SKILLS -> SkillsTab(
                    ui = ui,
                    onOpen = { skill ->
                        val source = skill.source.substringAfter(':', "")
                        viewModel.openSkill(source, skill.directory)
                    },
                    onCreate = { skillCreateOpen = true },
                )
                ManagementTab.PLUGINS -> PluginsTab(ui, viewModel::setPluginEnabled)
                ManagementTab.PROFILES -> ProfilesTab(
                    ui = ui,
                    onCreate = { profileCreateOpen = true },
                    onRename = { profileRename = it },
                    onDefault = viewModel::setDefaultProfile,
                    onDelete = { profileDelete = it },
                )
                ManagementTab.SECURITY -> SecurityTab(
                    ui = ui,
                    onRevoke = { deviceRevoke = it },
                    onDeletePasskey = viewModel::deletePasskey,
                )
            }
        }
    }

    if (mcpCreateOpen) {
        McpServerDialog(
            initial = null,
            onDismiss = { mcpCreateOpen = false },
            onSave = { name, input ->
                viewModel.saveMcp(name, input, create = true)
                mcpCreateOpen = false
            },
        )
    }
    mcpDialog?.let { server ->
        McpServerDialog(
            initial = server,
            onDismiss = { mcpDialog = null },
            onSave = { _, input ->
                viewModel.saveMcp(server.name, input, create = false)
                mcpDialog = null
            },
        )
    }
    if (skillCreateOpen) {
        SkillCreateDialog(
            onDismiss = { skillCreateOpen = false },
            onSave = { directory, description ->
                viewModel.createSkill(directory, description)
                skillCreateOpen = false
            },
        )
    }
    skillDetail?.let { detail ->
        SkillDetailDialog(
            detail = detail,
            onDismiss = {
                skillDetail = null
                viewModel.closeSkill()
            },
            onSave = { content -> viewModel.updateSkill(detail.directory, content) },
            onDelete = {
                viewModel.deleteSkill(detail.directory)
                skillDetail = null
            },
            onAdopt = {
                val source = detail.source.substringAfter(':', "")
                viewModel.adoptSkill(source, detail.directory, null)
                skillDetail = null
            },
        )
    }
    if (profileCreateOpen) {
        TextInputDialog(
            title = "Nouveau profil",
            label = "Nom",
            initial = "",
            onDismiss = { profileCreateOpen = false },
            onConfirm = {
                viewModel.createProfile(it)
                profileCreateOpen = false
            },
        )
    }
    profileRename?.let { name ->
        TextInputDialog(
            title = "Renommer le profil",
            label = "Nouveau nom",
            initial = name,
            onDismiss = { profileRename = null },
            onConfirm = {
                viewModel.renameProfile(name, it)
                profileRename = null
            },
        )
    }
    profileDelete?.let { name ->
        ConfirmDialog(
            title = "Supprimer le profil $name ?",
            text = "Les sessions de ce profil restent sur disque mais ne seront plus listées.",
            confirm = "Supprimer",
            onDismiss = { profileDelete = null },
            onConfirm = {
                viewModel.deleteProfile(name)
                profileDelete = null
            },
        )
    }
    deviceRevoke?.let { id ->
        ConfirmDialog(
            title = "Révoquer cette session ?",
            text = "L'appareil devra se reconnecter avec le jeton ou le mot de passe.",
            confirm = "Révoquer",
            onDismiss = { deviceRevoke = null },
            onConfirm = {
                viewModel.revokeDevice(id)
                deviceRevoke = null
            },
        )
    }
}

@Composable
private fun ServerTab(
    ui: ManagementUiState,
    context: android.content.Context,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.about == null && ui.health == null && ui.update == null &&
            ui.logs == null && ui.logsError == null
        ) {
            item { LoadingRow() }
        }
        ui.about?.let { about ->
            item {
                SectionCard("Serveur") {
                    Text("AoE ${about.version}", fontWeight = FontWeight.Bold)
                    Text("Profil : ${about.profile.ifBlank { "?" }}")
                    Text("Authentification : ${about.authMode}")
                    if (about.readOnly) Text("Mode lecture seule", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        ui.health?.let { health ->
            item {
                SectionCard("Santé") {
                    Text("État : ${health.status}", fontWeight = FontWeight.Bold)
                    health.cpuFraction?.let { Text("CPU : ${"%.0f".format(it * 100)} %") }
                    if (health.memoryTotalBytes > 0) {
                        Text(
                            "Mémoire : ${formatBytes(health.memoryUsedBytes)} / ${formatBytes(health.memoryTotalBytes)}",
                        )
                    }
                    if (health.loadAverage.isNotEmpty()) {
                        Text("Charge : ${health.loadAverage.joinToString(" ") { "%.2f".format(it) }}")
                    }
                    Text("Agents : ${health.agentCount} · processus : ${health.procCount}")
                    health.agents.forEach { agent ->
                        Text(
                            "• ${agent.title}  ${agent.cpuFraction?.let { "%.1f%%".format(it * 100) } ?: "?"}" +
                                agent.memoryBytes?.let { " · ${formatBytes(it)}" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        ui.update?.let { update ->
            item {
                SectionCard("Mise à jour du serveur") {
                    Text("Version serveur : ${update.currentVersion}")
                    Text("Dernière version : ${update.latestVersion ?: "inconnue"}")
                    if (update.updateAvailable) {
                        Text("Mise à jour disponible", color = MaterialTheme.colorScheme.primary)
                        update.releaseUrl?.let { url ->
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }) { Text("Ouvrir la release") }
                        }
                    }
                    update.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (update.updateCommand.isNotBlank() && update.updateAvailable) {
                        Text(
                            update.updateCommand,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            SectionCard("Logs du serveur") {
                val logs = ui.logs
                when {
                    ui.logsError != null -> Text(ui.logsError, color = MaterialTheme.colorScheme.error)
                    logs == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    logs.content.isBlank() -> Text("Aucun log disponible (${logs.status}).")
                    else -> {
                        Text(
                            "${logs.lineCount} lignes" + if (logs.truncated) " (tronquées)" else "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        SelectionContainer {
                            Text(
                                logs.content,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                                    .verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun McpTab(
    ui: ManagementUiState,
    onCreate: () -> Unit,
    onEdit: (McpServerView) -> Unit,
    onDelete: (String) -> Unit,
    onResolve: (String, String, String, String) -> Unit,
    onKeep: (String, String) -> Unit,
    onDrop: (String, String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Agent : ${ui.mcp?.agent ?: "—"}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Button(onClick = onCreate) { Text("Ajouter") }
            }
        }
        if (ui.mcp == null && ui.mcpError == null) {
            item { LoadingRow() }
        }
        ui.mcpError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        ui.mcp?.let { mcp ->
            if (mcp.driftPaused) {
                item { Text("Dérive MCP en pause.", color = MaterialTheme.colorScheme.tertiary) }
            }
            items(mcp.effective, key = { "mcp-${it.name}" }) { server ->
                SectionCard(server.name) {
                    Text("Transport : ${server.transport} · ${server.provenance}")
                    server.command?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    server.url?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    if (server.args.isNotEmpty()) {
                        Text(server.args.joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    if (server.envNames.isNotEmpty()) Text("Env : ${server.envNames.joinToString()}")
                    if (server.headerNames.isNotEmpty()) Text("Headers : ${server.headerNames.joinToString()}")
                    if (server.shadowed.isNotEmpty()) {
                        Text("Masque : ${server.shadowed.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onEdit(server) }) { Text("Modifier") }
                        OutlinedButton(onClick = { onDelete(server.name) }) {
                            Text("Supprimer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (mcp.keptOnRemoval.isNotEmpty()) {
                item { Text("Conservés à la suppression", style = MaterialTheme.typography.titleSmall) }
                items(mcp.keptOnRemoval, key = { "kept-${it.name}" }) { server ->
                    SectionCard(server.name) {
                        Text(server.provenance)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onKeep(server.name, mcp.agent) }) { Text("Conserver") }
                            OutlinedButton(onClick = { onDrop(server.name, mcp.agent) }) { Text("Retirer") }
                        }
                    }
                }
            }
            if (mcp.conflicts.isNotEmpty()) {
                item { Text("Conflits", style = MaterialTheme.typography.titleSmall) }
                items(mcp.conflicts, key = { "conflict-${it.name}" }) { conflict ->
                    SectionCard("Conflit : ${conflict.name}") {
                        Text("AoE : ${conflict.previous}")
                        Text("Natif : ${conflict.current}")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    onResolve(conflict.name, conflict.agent, "aoe", conflict.fingerprint)
                                },
                            ) { Text("Garder AoE") }
                            OutlinedButton(
                                onClick = {
                                    onResolve(conflict.name, conflict.agent, "native", conflict.fingerprint)
                                },
                            ) { Text("Garder natif") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SkillsTab(
    ui: ManagementUiState,
    onOpen: (SkillSummary) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Skills (${ui.skills?.skills?.size ?: 0})",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onCreate) { Text("Nouveau") }
            }
        }
        if (ui.skills == null && ui.skillsError == null) {
            item { LoadingRow() }
        }
        ui.skillsError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        ui.skills?.skills?.let { skills ->
            if (skills.isEmpty()) {
                item { Text("Aucun skill découvert.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(skills, key = { "${it.source}-${it.directory}" }) { skill ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(skill) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(skill.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                skill.provenanceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            skill.directory,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            item {
                Text(
                    "Racines : ${ui.skills?.roots?.joinToString { it.label }.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ui.skillDetailLoading) {
            item { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun PluginsTab(ui: ManagementUiState, onToggle: (String, Boolean) -> Unit) {
    val updates = ui.pluginUpdates.associateBy { it.id }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.plugins == null && ui.pluginsError == null) {
            item { LoadingRow() }
        }
        ui.pluginsError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        ui.plugins?.loadErrors?.forEach { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        items(ui.plugins?.plugins.orEmpty(), key = { it.id }) { plugin ->
            PluginCard(plugin, updates[plugin.id], onToggle)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginInfo,
    update: dev.proxboss.aoe.model.PluginUpdateInfo?,
    onToggle: (String, Boolean) -> Unit,
) {
    SectionCard(plugin.name) {
        Text("${plugin.id} · v${plugin.version} · ${plugin.validation}")
        Text(plugin.description, style = MaterialTheme.typography.bodySmall)
        if (plugin.capabilities.isNotEmpty()) {
            Text("Capacités : ${plugin.capabilities.joinToString()}", style = MaterialTheme.typography.labelSmall)
        }
        if (plugin.source != null) {
            Text(plugin.source, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        if (plugin.needsReapproval) {
            Text("Réapprobation requise", color = MaterialTheme.colorScheme.error)
        }
        update?.let {
            if (it.needsUpdate) {
                Text("Mise à jour : ${it.available ?: "disponible"}", color = MaterialTheme.colorScheme.primary)
            }
            it.error?.let { error -> Text(error, color = MaterialTheme.colorScheme.error) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (plugin.enabled) "Activé" else "Désactivé", modifier = Modifier.weight(1f))
            Switch(checked = plugin.enabled, onCheckedChange = { onToggle(plugin.id, it) })
        }
    }
}

@Composable
private fun ProfilesTab(
    ui: ManagementUiState,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onDefault: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Profils",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onCreate) { Text("Créer") }
            }
        }
        if (!ui.profilesLoaded && ui.profilesError == null) {
            item { LoadingRow() }
        }
        ui.profilesError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        items(ui.profiles, key = { it.name }) { profile ->
            SectionCard(profile.name) {
                Text(if (profile.isDefault) "Profil par défaut" else "Profil secondaire")
                if (profile.description.isNotBlank()) {
                    Text(profile.description, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onDefault(profile.name) }, enabled = !profile.isDefault) {
                        Text("Par défaut")
                    }
                    OutlinedButton(onClick = { onRename(profile.name) }) { Text("Renommer") }
                    OutlinedButton(onClick = { onDelete(profile.name) }, enabled = !profile.isDefault) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SecurityTab(
    ui: ManagementUiState,
    onRevoke: (String) -> Unit,
    onDeletePasskey: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!ui.securityLoaded && ui.securityError == null) {
            item { LoadingRow() }
        }
        ui.securityError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        item { Text("Appareils connectés", style = MaterialTheme.typography.titleMedium) }
        if (ui.devices.isEmpty()) {
            item { Text("Aucune session de login (auth par jeton).", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(ui.devices, key = { it.sessionId }) { device ->
            SectionCard(if (device.current) "Cet appareil" else "Appareil") {
                Text(device.userAgent.ifBlank { "Agent inconnu" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("IP : ${device.createdIp}")
                Text("Vu : ${device.lastSeen}", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = { onRevoke(device.sessionId) }) {
                    Text("Révoquer", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { HorizontalDivider() }
        item { Text("Passkeys", style = MaterialTheme.typography.titleMedium) }
        if (ui.passkeys.isEmpty()) {
            item {
                Text(
                    "Aucune passkey ou endpoint indisponible sur ce serveur.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(ui.passkeys, key = { it.credentialId }) { passkey ->
            SectionCard(passkey.label.ifBlank { "Passkey" }) {
                Text(passkey.origin, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Créée : ${TimeFormat.formatAge(java.time.Instant.ofEpochMilli(passkey.createdAtMs))}",
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedButton(onClick = { onDeletePasskey(passkey.credentialId) }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            content()
        }
    }
}

@Composable
private fun McpServerDialog(
    initial: McpServerView?,
    onDismiss: () -> Unit,
    onSave: (String, McpServerInput) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var transport by remember { mutableStateOf(initial?.transport ?: "stdio") }
    var command by remember { mutableStateOf(initial?.command.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var args by remember { mutableStateOf(initial?.args?.joinToString(" ").orEmpty()) }
    var envText by remember {
        mutableStateOf(initial?.envNames?.joinToString("\n") { "$it=" }.orEmpty())
    }
    var headerText by remember {
        mutableStateOf(initial?.headerNames?.joinToString("\n") { "$it=" }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau serveur MCP" else "Modifier ${initial.name}") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    enabled = initial == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("stdio", "http", "sse").forEach { kind ->
                        FilterChip(
                            selected = transport == kind,
                            onClick = { transport = kind },
                            label = { Text(kind) },
                        )
                    }
                }
                if (transport == "stdio") {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Commande") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text("Arguments (séparés par des espaces)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = envText,
                        onValueChange = { envText = it },
                        label = { Text("Env (NAME=valeur par ligne)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = headerText,
                        onValueChange = { headerText = it },
                        label = { Text("Headers (NAME=valeur par ligne)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val input = McpServerInput(
                        transport = transport,
                        command = if (transport == "stdio") command else null,
                        args = if (transport == "stdio") args.split(Regex("\\s+")).filter { it.isNotBlank() } else emptyList(),
                        url = if (transport != "stdio") url else null,
                        env = if (transport == "stdio") parseSecrets(envText) else emptyList(),
                        headers = if (transport != "stdio") parseSecrets(headerText) else emptyList(),
                    )
                    onSave(name, input)
                },
                enabled = name.isNotBlank() &&
                    ((transport == "stdio" && command.isNotBlank()) || (transport != "stdio" && url.isNotBlank())),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun parseSecrets(text: String): List<McpSecretEntry> =
    text.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@mapNotNull null
        val index = trimmed.indexOf('=')
        if (index <= 0) return@mapNotNull null
        McpSecretEntry(
            name = trimmed.substring(0, index).trim(),
            value = trimmed.substring(index + 1).ifBlank { null },
        )
    }

@Composable
private fun SkillCreateDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var directory by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau skill") },
        text = {
            Column {
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = { Text("Répertoire") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(directory.trim(), description.trim().ifBlank { null }) },
                enabled = directory.isNotBlank(),
            ) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SkillDetailDialog(
    detail: SkillDetail,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onAdopt: () -> Unit,
) {
    var content by remember(detail.directory, detail.content) { mutableStateOf(detail.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.name.ifBlank { detail.directory }) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(detail.description, style = MaterialTheme.typography.bodySmall)
                Text(detail.source, style = MaterialTheme.typography.labelSmall)
                if (detail.content.isNotBlank()) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (detail.source.startsWith("external")) {
                    Button(onClick = onAdopt) { Text("Adopter") }
                } else {
                    Button(onClick = { onSave(content) }, enabled = content.isNotBlank()) { Text("Enregistrer") }
                    TextButton(onClick = onDelete) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 o"
    val units = listOf("o", "Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}


@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
