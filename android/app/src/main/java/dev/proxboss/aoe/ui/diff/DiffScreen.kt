package dev.proxboss.aoe.ui.diff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.data.DiffBase
import dev.proxboss.aoe.data.DiffFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    viewModel: DiffViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selected = ui.selected
    var menuOpen by remember { mutableStateOf(false) }
    var showBaseDialog by remember { mutableStateOf(false) }

    val currentBase = ui.bases.firstOrNull { base ->
        selected == null || base.repoName == selected.repoName
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text(
                            when (ui.mode) {
                                DiffMode.FULL -> "Diff complet"
                                DiffMode.FILE -> selected?.path ?: "Diff"
                                DiffMode.LIST -> "Diff de la session"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                ui.session?.title?.let { append(it) }
                                currentBase?.baseBranch?.let { base ->
                                    if (isNotEmpty()) append(" · ")
                                    append("base ").append(base)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (ui.mode == DiffMode.LIST) onBack() else viewModel.showList()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (ui.mode == DiffMode.FILE && selected != null) {
                        IconButton(onClick = { viewModel.openRelative(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Fichier précédent")
                        }
                        IconButton(onClick = { viewModel.openRelative(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Fichier suivant")
                        }
                        OutlinedButton(onClick = {
                            if (ui.showFileContent) viewModel.showPatch() else viewModel.showFullFile()
                        }) {
                            Text(if (ui.showFileContent) "Patch" else "Fichier")
                        }
                    }
                    if (ui.mode == DiffMode.LIST) {
                        IconButton(onClick = { viewModel.loadFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus d'actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Diff complet") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.loadFullDiff()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Base de diff…") },
                                onClick = {
                                    menuOpen = false
                                    showBaseDialog = true
                                },
                                enabled = ui.bases.any { !it.outsideRepository },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ui.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.dismissError() }
                        .padding(10.dp),
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall)
                }
            }
            when {
                ui.mode == DiffMode.FULL -> FullDiffPane(
                    loading = ui.fullLoading,
                    progress = ui.fullProgress,
                    patch = ui.fullPatch,
                )
                ui.mode == DiffMode.FILE && selected != null -> {
                    if (ui.showFileContent) {
                        SessionFilePane(
                            loading = ui.sessionFileLoading,
                            content = ui.sessionFile?.content.orEmpty(),
                            isBinary = ui.sessionFile?.isBinary == true,
                            truncated = ui.sessionFile?.truncated == true,
                        )
                    } else {
                        DiffFilePane(
                            loading = ui.fileLoading,
                            patch = ui.content?.patch.orEmpty(),
                            isBinary = ui.content?.isBinary == true,
                            truncated = ui.content?.truncated == true,
                        )
                    }
                }
                ui.loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
                else -> DiffFileList(
                    files = ui.files,
                    warning = ui.warning,
                    onOpen = viewModel::openFile,
                )
            }
        }
    }

    if (showBaseDialog) {
        DiffBaseDialog(
            bases = ui.bases,
            onDismiss = { showBaseDialog = false },
            onApply = { repo, base ->
                viewModel.setDiffBase(repo, base)
                showBaseDialog = false
            },
        )
    }
}

@Composable
private fun DiffFileList(
    files: List<DiffFile>,
    warning: String?,
    onOpen: (DiffFile) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!warning.isNullOrBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        warning,
                        modifier = Modifier.padding(9.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (files.isEmpty()) {
            item {
                Text(
                    "Aucun changement détecté.",
                    modifier = Modifier.padding(top = 30.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(files, key = { "${it.repoName ?: ""}:${it.path}" }) { file ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(file) },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.small,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            file.path,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            file.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!file.repoName.isNullOrBlank()) {
                            Text(
                                file.repoName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "+${file.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5FBF83),
                        )
                        Text(
                            "−${file.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffFilePane(
    loading: Boolean,
    patch: String,
    isBinary: Boolean,
    truncated: Boolean,
) {
    if (loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }
    when {
        isBinary -> MessagePane("Fichier binaire : aperçu indisponible.")
        truncated -> MessagePane("Fichier trop volumineux pour l'aperçu.")
        patch.isBlank() -> MessagePane("Aucun contenu textuel à afficher.")
        else -> PatchLines(patch)
    }
}

@Composable
private fun MessagePane(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun FullDiffPane(loading: Boolean, progress: String, patch: String) {
    if (loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            if (progress.isNotBlank()) {
                Text(
                    progress,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    if (patch.isBlank()) {
        MessagePane("Aucun changement à afficher.")
        return
    }
    PatchLines(patch)
}

@Composable
private fun PatchLines(patch: String) {
    val lines = patch.lines()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            val color = when {
                line.startsWith("+++") || line.startsWith("---") -> MaterialTheme.colorScheme.onSurfaceVariant
                line.startsWith("+") -> Color(0xFF5FBF83)
                line.startsWith("-") -> MaterialTheme.colorScheme.error
                line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                line.startsWith("diff ") || line.startsWith("════") -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                line.ifEmpty { " " },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SessionFilePane(
    loading: Boolean,
    content: String,
    isBinary: Boolean,
    truncated: Boolean,
) {
    when {
        loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }
        isBinary -> MessagePane("Fichier binaire : aperçu indisponible.")
        truncated -> MessagePane("Fichier trop volumineux pour l'aperçu.")
        else -> {
            SelectionContainer {
                Text(
                    content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DiffBaseDialog(
    bases: List<DiffBase>,
    onDismiss: () -> Unit,
    onApply: (String?, String?) -> Unit,
) {
    val usable = bases.filter { !it.outsideRepository }
    var repoIndex by remember { mutableStateOf(0) }
    val selected = usable.getOrNull(repoIndex)
    var value by remember(selected?.repoName) {
        mutableStateOf(selected?.baseOverride ?: selected?.baseBranch.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Base de diff") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (usable.isEmpty()) {
                    Text("Ce dossier n'est pas un dépôt git.")
                } else {
                    if (usable.size > 1) {
                        Text(
                            "Dépôt",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        usable.forEachIndexed { index, base ->
                            TextButton(
                                onClick = { repoIndex = index },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    (base.repoName ?: base.repoPath) +
                                        if (index == repoIndex) "  ✓" else "",
                                )
                            }
                        }
                    }
                    Text(
                        "Branche ou commit de référence. Laisser vide pour la base automatique.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base") },
                        placeholder = { Text(selected?.baseBranch ?: "main") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selected?.repoName, value.trim().ifBlank { null })
                },
                enabled = usable.isNotEmpty(),
            ) { Text("Appliquer") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onApply(selected?.repoName, null) },
                    enabled = usable.isNotEmpty(),
                ) { Text("Réinitialiser") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
