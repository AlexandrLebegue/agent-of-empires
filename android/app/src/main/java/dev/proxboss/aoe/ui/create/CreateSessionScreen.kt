package dev.proxboss.aoe.ui.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.model.AgentInfo
import dev.proxboss.aoe.model.ConfigOption
import dev.proxboss.aoe.model.ConfigOptionChoice
import dev.proxboss.aoe.model.CreationTrustReview
import dev.proxboss.aoe.model.DirEntry
import dev.proxboss.aoe.model.RecentProject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    viewModel: CreateSessionViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.createdSessionId) {
        ui.createdSessionId?.let { sessionId ->
            viewModel.consumeCreated()
            onCreated(sessionId)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Nouvelle session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recharger")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ui.loadError?.let { error ->
                        item { ErrorBanner(error, viewModel::dismissLoadError) }
                    }

                    item {
                        FormSection("Dossier") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        ui.scratch -> "Session scratch (dossier auto)"
                                        ui.path.isBlank() -> "Aucun dossier sélectionné"
                                        else -> ui.path
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                OutlinedButton(
                                    onClick = viewModel::openFolderPicker,
                                    enabled = !ui.scratch && !ui.creating,
                                ) { Text("Choisir") }
                            }
                            ToggleLine(
                                title = "Mode scratch",
                                subtitle = "Le serveur crée un dossier de travail dédié",
                                checked = ui.scratch,
                                onChange = viewModel::setScratch,
                                enabled = !ui.creating,
                            )
                            if (!ui.scratch && ui.pathIsRepo == false) {
                                Text(
                                    "Ce dossier n'est pas un dépôt git; le worktree est indisponible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (ui.recentProjects.isNotEmpty()) {
                                Text(
                                    "Projets récents",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                ui.recentProjects.take(5).forEach { project ->
                                    RecentProjectRow(project, enabled = !ui.creating) {
                                        viewModel.selectRecentProject(project)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        FormSection("Agent") {
                            AgentPicker(
                                agents = ui.agents,
                                selected = ui.selectedAgent,
                                enabled = !ui.creating,
                                onSelect = viewModel::selectAgent,
                            )
                            val agent = ui.selectedAgentInfo
                            if (agent != null && !agent.installed) {
                                Text(
                                    "Cet agent n'est pas installé sur le serveur.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (agent != null && ui.structured && !agent.canRunStructured) {
                                Text(
                                    "Adaptateur ACP indisponible; la session démarrera en terminal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ToggleLine(
                                title = "Vue structurée (ACP)",
                                subtitle = "Transcript, approbations et modèles dans l'app",
                                checked = ui.structured,
                                onChange = viewModel::setStructured,
                                enabled = !ui.creating && agent?.canRunStructured == true,
                            )
                            if (ui.structured && agent?.canRunStructured == true) {
                                if (ui.modelOption != null) {
                                    ChoicePicker(
                                        label = "Modèle",
                                        value = ui.model,
                                        choices = ui.modelOption?.choices.orEmpty(),
                                        includeDefault = true,
                                        defaultLabel = "Défaut de l'agent",
                                        enabled = !ui.creating,
                                        onSelect = viewModel::setModel,
                                    )
                                }
                                if (ui.effortOption != null) {
                                    ChoicePicker(
                                        label = "Effort",
                                        value = ui.effort,
                                        choices = ui.effortOption?.choices.orEmpty(),
                                        includeDefault = true,
                                        defaultLabel = "Défaut de l'agent",
                                        enabled = !ui.creating,
                                        onSelect = viewModel::setEffort,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        FormSection("Session") {
                            OutlinedTextField(
                                value = ui.title,
                                onValueChange = viewModel::setTitle,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !ui.creating,
                                label = { Text("Titre (optionnel)") },
                                placeholder = { Text("Le serveur peut le générer") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = ui.group,
                                onValueChange = viewModel::setGroup,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !ui.creating,
                                label = { Text("Groupe (optionnel)") },
                                singleLine = true,
                            )
                            ToggleLine(
                                title = "YOLO",
                                subtitle = "L'agent n'attend pas les approbations",
                                checked = ui.yoloMode,
                                onChange = viewModel::setYolo,
                                enabled = !ui.creating &&
                                    (ui.selectedAgentInfo?.let { !it.alwaysYolo && !it.acpAlwaysYolo } ?: true),
                            )
                        }
                    }

                    if (!ui.scratch && ui.pathIsRepo == true) {
                        item {
                            FormSection("Worktree") {
                                ToggleLine(
                                    title = "Créer un worktree",
                                    subtitle = "Isole la session dans sa propre branche",
                                    checked = ui.worktree,
                                    onChange = viewModel::setWorktree,
                                    enabled = !ui.creating,
                                )
                                if (ui.worktree) {
                                    ToggleLine(
                                        title = "Nouvelle branche",
                                        subtitle = if (ui.createNewBranch) {
                                            "La branche est créée depuis la base choisie"
                                        } else {
                                            "La session utilise une branche existante"
                                        },
                                        checked = ui.createNewBranch,
                                        onChange = viewModel::setCreateNewBranch,
                                        enabled = !ui.creating,
                                    )
                                    OutlinedTextField(
                                        value = ui.worktreeBranch,
                                        onValueChange = viewModel::setWorktreeBranch,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !ui.creating,
                                        label = {
                                            Text(
                                                if (ui.createNewBranch) {
                                                    "Nom de la branche (optionnel)"
                                                } else {
                                                    "Branche existante"
                                                },
                                            )
                                        },
                                        singleLine = true,
                                    )
                                    if (ui.createNewBranch) {
                                        if (ui.branches.isNotEmpty()) {
                                            BranchPicker(
                                                label = "Branche de base",
                                                branches = ui.branches,
                                                selected = ui.baseBranch,
                                                enabled = !ui.creating,
                                                onSelect = viewModel::setBaseBranch,
                                            )
                                        }
                                    } else if (ui.branches.isNotEmpty()) {
                                        BranchPicker(
                                            label = "Branche existante",
                                            branches = ui.branches,
                                            selected = ui.worktreeBranch,
                                            enabled = !ui.creating,
                                            onSelect = viewModel::setWorktreeBranch,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (ui.creating) {
                        item {
                            FormSection("Création") {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                val progress = ui.progress
                                Text(
                                    progress?.labelFr ?: "Création en cours…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (!progress?.hook.isNullOrBlank()) {
                                    Text(
                                        progress?.hook.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                progress?.output?.takeLast(8)?.forEach { line ->
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    ui.createError?.let { error ->
                        item { ErrorBanner(error, viewModel::dismissError) }
                    }

                    item {
                        Button(
                            onClick = viewModel::startCreate,
                            enabled = ui.canCreate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (ui.creating) "Création…" else "Créer la session")
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (ui.showFolderPicker) {
        FolderPickerDialog(
            ui = ui,
            onBrowse = viewModel::browse,
            onUp = viewModel::browseUp,
            onHome = viewModel::browseHome,
            onToggleHidden = viewModel::setBrowseHidden,
            onChoose = viewModel::chooseCurrentFolder,
            onDismiss = viewModel::closeFolderPicker,
        )
    }

    ui.trust?.let { trust ->
        TrustDialog(
            review = trust,
            applying = ui.trustApplying,
            onApprove = viewModel::approveTrust,
            onSkip = viewModel::skipTrust,
            onCancel = viewModel::cancelTrust,
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun ToggleLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun RecentProjectRow(project: RecentProject, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            project.displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            project.tool,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgentPicker(
    agents: List<AgentInfo>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = agents.firstOrNull { it.name == selected }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled && agents.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text("Agent", style = MaterialTheme.typography.labelSmall)
                Text(
                    current?.displayName ?: "Choisir un agent",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(agent.displayName + if (agent.canRunStructured) "  · ACP" else "")
                            if (!agent.installed) {
                                Text(
                                    "non installé",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    enabled = agent.installed,
                    onClick = {
                        open = false
                        onSelect(agent.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoicePicker(
    label: String,
    value: String?,
    choices: List<ConfigOptionChoice>,
    includeDefault: Boolean,
    defaultLabel: String,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val currentName = choices.firstOrNull { it.value == value }?.name
        ?: value?.takeIf { it.isNotBlank() }
        ?: defaultLabel
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(currentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            if (includeDefault) {
                DropdownMenuItem(
                    text = { Text(defaultLabel) },
                    onClick = {
                        open = false
                        onSelect(null)
                    },
                )
            }
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.name,
                            fontWeight = if (choice.value == value) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(choice.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun BranchPicker(
    label: String,
    branches: List<dev.proxboss.aoe.model.BranchInfo>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    selected.ifBlank { "Choisir une branche" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Text(
                            branch.name + if (branch.isCurrent) "  (actuelle)" else "",
                            fontWeight = if (branch.name == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(branch.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerDialog(
    ui: CreateUiState,
    onBrowse: (String) -> Unit,
    onUp: () -> Unit,
    onHome: () -> Unit,
    onToggleHidden: (Boolean) -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Choisir un dossier")
                                Text(
                                    ui.browsePath,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer")
                            }
                        },
                        actions = {
                            IconButton(onClick = onUp) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Dossier parent")
                            }
                            IconButton(onClick = onHome) {
                                Icon(Icons.Default.Home, contentDescription = "Dossier personnel")
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Cachés", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = ui.browseShowHidden,
                                onCheckedChange = onToggleHidden,
                                enabled = !ui.browseLoading,
                            )
                            Spacer(Modifier.weight(1f))
                            Button(onClick = onChoose, enabled = ui.browsePath.isNotBlank()) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Choisir ce dossier")
                            }
                        }
                    }
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    ui.browseError?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (ui.browseLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(ui.browseEntries, key = { it.path }) { entry ->
                            DirectoryRow(entry, onClick = { onBrowse(entry.path) })
                        }
                        if (ui.browseHasMore) {
                            item {
                                Text(
                                    "Liste tronquée, utilisez le filtre du dossier courant ou descendez dans un sous-dossier.",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(entry: DirEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            entry.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.isGitRepo) {
            Text(
                "git",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TrustDialog(
    review: CreationTrustReview,
    applying: Boolean,
    onApprove: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    val groups = buildList {
        add("on_create" to review.mergedHooks.onCreate)
        add("on_launch" to review.mergedHooks.onLaunch)
        add("on_destroy" to review.mergedHooks.onDestroy)
        add("mcp_servers" to review.mcpSummaries)
    }.filter { it.second.isNotEmpty() }

    AlertDialog(
        onDismissRequest = { if (!applying) onCancel() },
        title = {
            Text(
                if (review.mcpNeedTrust) {
                    "Approuver les hooks et serveurs MCP ?"
                } else {
                    "Approuver les hooks du dépôt ?"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Ce dépôt définit des commandes qui s'exécutent sur la machine du serveur. " +
                        "Approuver les autorise pour toutes les sessions futures de ce dépôt.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                groups.forEach { (name, commands) ->
                    Text(
                        "$name:",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    commands.forEach { command ->
                        Text(
                            command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = onApprove, enabled = !applying) {
                if (applying) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Approuver et créer")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip, enabled = !applying) { Text("Ignorer") }
                TextButton(onClick = onCancel, enabled = !applying) { Text("Annuler") }
            }
        },
    )
}
