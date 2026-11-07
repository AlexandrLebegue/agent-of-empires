package dev.proxboss.aoe.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionFilter
import dev.proxboss.aoe.model.SessionStatus
import dev.proxboss.aoe.ui.common.StatusDot
import dev.proxboss.aoe.ui.common.StatusPill
import dev.proxboss.aoe.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateSession: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSession) {
                Icon(Icons.Default.Add, contentDescription = "Nouvelle session")
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sessions")
                        Text(
                            when {
                                ui.live -> "Temps réel connecté"
                                ui.offline -> "Serveur injoignable"
                                else -> "Rafraîchissement périodique"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(showSpinner = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Réglages")
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
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                singleLine = true,
                placeholder = { Text("Rechercher une session ou une conversation…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = ui.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.labelFr) },
                    )
                }
            }

            if (ui.offline) {
                OfflineBanner(message = ui.error, onRetry = { viewModel.refresh(showSpinner = true) })
            }

            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (ui.loading && ui.sessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 70.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    if (ui.query.isNotBlank()) {
                        item {
                            Text(
                                "Recherche dans les conversations",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        if (ui.searchHits.isEmpty()) {
                            item {
                                Text(
                                    "Aucun résultat dans les conversations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(ui.searchHits, key = { "hit-${it.sessionId}-${it.seq}" }) { hit ->
                                SearchHitRow(
                                    hit = hit,
                                    title = ui.titles[hit.sessionId] ?: "Session ${hit.sessionId.take(8)}",
                                    onClick = {
                                        ui.sessions.firstOrNull { it.id == hit.sessionId }?.let(viewModel::markRead)
                                        onOpenSession(hit.sessionId)
                                    },
                                )
                            }
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
                    }

                    if (ui.groups.isEmpty() && !ui.loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 60.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (ui.query.isNotBlank()) "Aucune session ne correspond." else "Aucune session.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    ui.groups.forEach { group ->
                        item(key = "group-${group.path}") {
                            GroupHeader(group)
                        }
                        items(group.sessions, key = { it.id }) { session ->
                            SessionRow(
                                session = session,
                                onClick = {
                                    viewModel.markRead(session)
                                    onOpenSession(session.id)
                                },
                                onTogglePin = { viewModel.togglePin(session) },
                                onToggleFavorite = { viewModel.toggleFavorite(session) },
                                onToggleMuted = { viewModel.toggleMuted(session) },
                                onToggleRead = { viewModel.toggleRead(session) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner(message: String?, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Serveur injoignable",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    message ?: "Les sessions affichées peuvent être périmées.",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

@Composable
private fun GroupHeader(group: SessionGroupUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            group.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            group.sessions.size.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${hit.matchCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                hit.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMuted: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val muted = session.notifyOnWaiting == false &&
        session.notifyOnIdle == false &&
        session.notifyOnError == false
    val highlight = when (session.status) {
        SessionStatus.WAITING -> Color(0xFFF0A43A).copy(alpha = 0.13f)
        SessionStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface
    }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (session.isArchived) 0.7f else 1f),
        colors = CardDefaults.cardColors(containerColor = highlight),
        elevation = CardDefaults.cardElevation(defaultElevation = if (session.status.needsAttention) 2.dp else 0.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 9.dp, end = 4.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(session.status, size = if (session.unread) 11 else 9)
                Spacer(Modifier.width(8.dp))
                Text(
                    session.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (session.unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Épinglée",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (session.favorited) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFF2C46D),
                    )
                }
                if (session.unread) {
                    Box(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (session.isPinned) "Désépingler" else "Épingler") },
                            onClick = {
                                menuOpen = false
                                onTogglePin()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (session.favorited) "Retirer des favoris" else "Ajouter aux favoris")
                            },
                            onClick = {
                                menuOpen = false
                                onToggleFavorite()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (muted) "Réactiver les notifications" else "Muter les notifications")
                            },
                            onClick = {
                                menuOpen = false
                                onToggleMuted()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (session.unread) "Marquer comme lu" else "Marquer comme non lu") },
                            onClick = {
                                menuOpen = false
                                onToggleRead()
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 17.dp, end = 8.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val model = session.model?.trim().orEmpty()
                Text(
                    if (model.isBlank()) session.displayAgent else "${session.displayAgent} · $model",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    TimeFormat.formatAge(session.activityAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 17.dp, top = 5.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(session.status)
                if (session.status == SessionStatus.WAITING) {
                    Text(
                        "Approbation requise",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF0A43A),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (session.dormant) {
                    Text(
                        "dormant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.isArchived) {
                    Text(
                        "archivée",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.isSnoozed) {
                    Text(
                        "en pause",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (muted) {
                    Text(
                        "muette",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
