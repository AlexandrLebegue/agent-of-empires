package dev.proxboss.aoe.ui.diff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.data.DiffFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    viewModel: DiffViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selected = ui.selected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (selected == null) "Diff de la session" else selected.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            ui.session?.title ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selected != null) viewModel.closeFile() else onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
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
                selected != null -> DiffFilePane(
                    loading = ui.fileLoading,
                    patch = ui.content?.patch.orEmpty(),
                    isBinary = ui.content?.isBinary == true,
                    truncated = ui.content?.truncated == true,
                )
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
        else -> {
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
                        line.startsWith("diff ") -> MaterialTheme.colorScheme.onSurfaceVariant
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
