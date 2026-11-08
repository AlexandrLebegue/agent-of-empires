package dev.proxboss.aoe.ui.sessions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.proxboss.aoe.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionFilter
import dev.proxboss.aoe.model.SessionStatus
import dev.proxboss.aoe.ui.common.StatusDot
import dev.proxboss.aoe.ui.common.StatusPill
import dev.proxboss.aoe.ui.theme.AoEPalette
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
    var renameTarget by remember { mutableStateOf<Session?>(null) }
    var snoozeTarget by remember { mutableStateOf<Session?>(null) }
    var colorTarget by remember { mutableStateOf<Session?>(null) }
    var trashTarget by remember { mutableStateOf<Session?>(null) }
    var moveGroupTarget by remember { mutableStateOf<Session?>(null) }
    var renameGroupTarget by remember { mutableStateOf<String?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<String?>(null) }
    var createGroupOpen by remember { mutableStateOf(false) }
    var workspaceOrderOpen by remember { mutableStateOf(false) }
    var groupsManagerOpen by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(ui.message, ui.undoTrashSession) {
        ui.message?.let { message ->
            val hasUndo = ui.undoTrashSession != null && message.contains("corbeille", ignoreCase = true)
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (hasUndo) "Annuler" else null,
                duration = if (hasUndo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoTrash()
            } else {
                viewModel.dismissUndo()
                viewModel.clearMessage()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
            ) {
                Button(
                    onClick = onCreateSession,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nouvelle session", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        topBar = {
            SessionsHeader(
                query = ui.query,
                onQueryChange = viewModel::setQuery,
                sortLabel = ui.sortMode.labelFr,
                onSettings = onOpenSettings,
                onRefresh = { viewModel.refresh(showSpinner = true) },
                onToggleSort = viewModel::toggleSortMode,
                onCreateGroup = { createGroupOpen = true },
                onManageGroups = { groupsManagerOpen = true },
                onWorkspaceOrder = { workspaceOrderOpen = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                        shape = RoundedCornerShape(50),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = ui.filter == filter,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            SummaryStrip(ui)

            if (ui.offline) {
                OfflineBanner(message = ui.error, onRetry = { viewModel.refresh(showSpinner = true) })
            }

            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = { viewModel.refresh(showSpinner = true) },
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
                        items(3, key = { "skeleton-$it" }) { SkeletonBlock() }
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

                    if (ui.workspaces.isEmpty() && !ui.loading) {
                        item {
                            EmptySessions(
                                hasQuery = ui.query.isNotBlank(),
                                onCreateSession = onCreateSession,
                                onClearQuery = { viewModel.setQuery("") },
                            )
                        }
                    }

                    if (ui.workspaces.isNotEmpty()) {
                        item(key = "workspaces-title") {
                            Text(
                                "PROJETS",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                            )
                        }
                    }
                    val favorites = ui.workspaces
                        .flatMap { it.sessions }
                        .filter { it.favorited }
                        .distinctBy { it.id }
                    val favoriteIds = favorites.mapTo(HashSet()) { it.id }
                    val favoriteWorkspace = WorkspaceUi(
                        id = "__favorites__",
                        label = "Favoris",
                        projectPath = "",
                        sessions = favorites,
                        collapsed = "ws:__favorites__" in ui.collapsedGroups,
                    )
                    val projectWorkspaces = ui.workspaces.mapNotNull { workspace ->
                        val sessions = workspace.sessions.filterNot { it.id in favoriteIds }
                        if (sessions.isEmpty()) null else workspace.copy(sessions = sessions)
                    }
                    val actionsFor: (Session) -> SessionRowActions = { session ->
                        SessionRowActions(
                            onOpen = {
                                viewModel.markRead(session)
                                onOpenSession(session.id)
                            },
                            onTogglePin = { viewModel.togglePin(session) },
                            onToggleFavorite = { viewModel.toggleFavorite(session) },
                            onToggleMuted = { viewModel.toggleMuted(session) },
                            onToggleRead = { viewModel.toggleRead(session) },
                            onRename = { renameTarget = session },
                            onSnooze = { snoozeTarget = session },
                            onColor = { colorTarget = session },
                            onArchive = { viewModel.toggleArchive(session) },
                            onTrash = { trashTarget = session },
                            onRestore = { viewModel.restore(session) },
                            onStop = { viewModel.stop(session) },
                            onStart = { viewModel.start(session) },
                            onRestart = { viewModel.restart(session) },
                            onMoveUp = { viewModel.reorder(session, up = true) },
                            onMoveDown = { viewModel.reorder(session, up = false) },
                            onMoveGroup = { moveGroupTarget = session },
                        )
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "workspace-__favorites__") {
                            WorkspaceCard(
                                workspace = favoriteWorkspace,
                                favorites = true,
                                onToggle = { viewModel.toggleWorkspace("__favorites__") },
                                actionsFor = actionsFor,
                            )
                        }
                    }
                    projectWorkspaces.forEach { workspace ->
                        item(key = "workspace-${workspace.id}") {
                            WorkspaceCard(
                                workspace = workspace,
                                favorites = false,
                                onToggle = { viewModel.toggleWorkspace(workspace.id) },
                                actionsFor = actionsFor,
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { session ->
        RenameSessionDialog(
            session = session,
            onDismiss = { renameTarget = null },
            onConfirm = { title -> viewModel.rename(session, title) },
        )
    }
    snoozeTarget?.let { session ->
        SnoozeSessionDialog(
            session = session,
            onDismiss = { snoozeTarget = null },
            onSnooze = { minutes -> viewModel.snooze(session, minutes) },
        )
    }
    colorTarget?.let { session ->
        ColorSessionDialog(
            session = session,
            onDismiss = { colorTarget = null },
            onColor = { color -> viewModel.setColor(session, color) },
        )
    }
    trashTarget?.let { session ->
        TrashSessionDialog(
            session = session,
            onDismiss = { trashTarget = null },
            onConfirm = { viewModel.trash(session) },
        )
    }
    moveGroupTarget?.let { session ->
        MoveSessionGroupDialog(
            session = session,
            groups = ui.groups.map { it.path }.filter { it.isNotBlank() }.distinct(),
            onDismiss = { moveGroupTarget = null },
            onSelect = { group ->
                viewModel.moveSessionToGroup(session, group)
                moveGroupTarget = null
            },
        )
    }
    createGroupOpen.takeIf { it }?.let {
        GroupPathDialog(
            title = "Nouveau groupe",
            initial = "",
            confirmLabel = "Créer",
            onDismiss = { createGroupOpen = false },
            onConfirm = { path ->
                viewModel.createGroup(path)
                createGroupOpen = false
            },
        )
    }
    renameGroupTarget?.let { path ->
        GroupPathDialog(
            title = "Renommer le groupe",
            initial = path,
            confirmLabel = "Renommer",
            onDismiss = { renameGroupTarget = null },
            onConfirm = { next ->
                viewModel.renameGroup(path, next)
                renameGroupTarget = null
            },
        )
    }
    deleteGroupTarget?.let { path ->
        DeleteGroupDialog(
            path = path,
            sessionCount = ui.groupsInfo.firstOrNull { it.path == path }?.sessionCount
                ?: ui.groups.firstOrNull { it.path == path }?.sessions?.size
                ?: 0,
            onDismiss = { deleteGroupTarget = null },
            onConfirm = { mode ->
                viewModel.deleteGroup(path, mode)
                deleteGroupTarget = null
            },
        )
    }
    if (groupsManagerOpen) {
        GroupsManagerDialog(
            groups = ui.groups,
            onCreate = {
                groupsManagerOpen = false
                createGroupOpen = true
            },
            onRename = { path ->
                groupsManagerOpen = false
                renameGroupTarget = path
            },
            onDelete = { path ->
                groupsManagerOpen = false
                deleteGroupTarget = path
            },
            onDismiss = { groupsManagerOpen = false },
        )
    }
    if (workspaceOrderOpen) {
        WorkspaceOrderDialog(
            workspaces = ui.workspaces,
            onMove = viewModel::moveWorkspace,
            onDismiss = { workspaceOrderOpen = false },
        )
    }
}

@Composable
private fun SummaryStrip(ui: SessionsUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryStat(
                label = "ACTIVES",
                value = ui.activeCount.toString(),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryStat(
                label = "ATTENTE",
                value = ui.waitingCount.toString(),
                tint = AoEPalette.Amber,
                modifier = Modifier.weight(1f),
            )
            SummaryStat(
                label = "NON LUES",
                value = ui.unreadCount.toString(),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            val health = ui.health
            val healthTint = when (health?.status) {
                "ok" -> AoEPalette.Green
                "warn" -> AoEPalette.Amber
                "critical" -> AoEPalette.Rose
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            SummaryStat(
                label = "SANTÉ",
                value = health?.status?.uppercase() ?: "—",
                tint = healthTint,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun projectAccent(id: String): Color {
    val palette = listOf(
        AoEPalette.Cyan,
        AoEPalette.Violet,
        AoEPalette.Green,
        AoEPalette.Amber,
        Color(0xFF60A5FA),
        Color(0xFFF472B6),
    )
    val hash = id.fold(0) { acc, ch -> (acc * 31 + ch.code) and 0x7fffffff }
    return palette[hash % palette.size]
}

@Composable
private fun SkeletonBlock() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .height(14.dp)
                    .fillMaxWidth(0.45f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(6.dp)),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(18.dp)),
        )
    }
}


@Composable
private fun SessionsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    sortLabel: String,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSort: () -> Unit,
    onCreateGroup: () -> Unit,
    onManageGroups: () -> Unit,
    onWorkspaceOrder: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                            ),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_stat_aoe),
                        contentDescription = "Réglages",
                        modifier = Modifier.size(21.dp),
                        tint = AoEPalette.Amber,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            SearchPill(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(2.dp))
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Plus d'actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rafraîchir") },
                        onClick = {
                            menuOpen = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Tri : $sortLabel") },
                        onClick = {
                            menuOpen = false
                            onToggleSort()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Nouveau groupe") },
                        onClick = {
                            menuOpen = false
                            onCreateGroup()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gérer les groupes") },
                        onClick = {
                            menuOpen = false
                            onManageGroups()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Ordre des workspaces") },
                        onClick = {
                            menuOpen = false
                            onWorkspaceOrder()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Réglages") },
                        onClick = {
                            menuOpen = false
                            onSettings()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Rechercher",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Effacer la recherche",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner(message: String?, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
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
private fun WorkspaceCard(
    workspace: WorkspaceUi,
    favorites: Boolean,
    onToggle: () -> Unit,
    actionsFor: (Session) -> SessionRowActions,
) {
    val accent = if (favorites) AoEPalette.Amber else projectAccent(workspace.id)
    val expanded = !workspace.collapsed
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "workspace-chevron",
    )
    val aggregate = aggregateState(workspace.sessions)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1728),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.12f)),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorites) Icons.Default.Star else Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = accent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            workspace.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (workspace.sessions.size > 1) {
                            Spacer(Modifier.width(7.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    workspace.sessions.size.toString(),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Text(
                        aggregate.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = aggregate.color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Replier le projet" else "Déplier le projet",
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    workspace.sessions.forEachIndexed { index, session ->
                        SessionRow(
                            session = session,
                            actions = actionsFor(session),
                            embedded = true,
                        )
                        if (index != workspace.sessions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 54.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AggregateState(val label: String, val color: Color)

private fun aggregateState(sessions: List<Session>): AggregateState = when {
    sessions.any { it.status == SessionStatus.WAITING } ->
        AggregateState("en attente d'approbation", AoEPalette.Amber)
    sessions.any { it.status == SessionStatus.ERROR } ->
        AggregateState("erreur", AoEPalette.Rose)
    sessions.any {
        it.status == SessionStatus.RUNNING ||
            it.status == SessionStatus.STARTING ||
            it.status == SessionStatus.CREATING
    } -> AggregateState("en cours", AoEPalette.Cyan)
    else -> AggregateState("au repos", AoEPalette.Green)
}

@Composable
private fun GroupsManagerDialog(
    groups: List<SessionGroupUi>,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val paths = groups.map { it.path }.filter { it.isNotBlank() }.distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Groupes AoE") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Les groupes servent à classer les sessions. L'accueil est organisé par projet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                paths.forEach { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            path,
                            modifier = Modifier.weight(1f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onRename(path) }) { Text("Renommer") }
                        TextButton(onClick = { onDelete(path) }) {
                            Text("Suppr.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (paths.isEmpty()) {
                    Text("Aucun groupe défini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = onCreate) { Text("Nouveau groupe") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun SearchHitRow(hit: SearchHit, title: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
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

private class SessionRowActions(
    val onOpen: () -> Unit,
    val onTogglePin: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onToggleMuted: () -> Unit,
    val onToggleRead: () -> Unit,
    val onRename: () -> Unit,
    val onSnooze: () -> Unit,
    val onColor: () -> Unit,
    val onArchive: () -> Unit,
    val onTrash: () -> Unit,
    val onRestore: () -> Unit,
    val onStop: () -> Unit,
    val onStart: () -> Unit,
    val onRestart: () -> Unit,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onMoveGroup: () -> Unit,
)


@Composable
private fun SessionRow(session: Session, actions: SessionRowActions, embedded: Boolean = false) {
    val muted = session.notifyOnWaiting == false &&
        session.notifyOnIdle == false &&
        session.notifyOnError == false
    val highlight = if (embedded) {
        Color.Transparent
    } else when {
        session.isTrashed -> Color(0xFF121A2A)
        session.status == SessionStatus.WAITING -> Color(0xFF271F10)
        session.status == SessionStatus.ERROR -> Color(0xFF27131A)
        else -> Color(0xFF0E182A)
    }
    val borderColor = when {
        session.status == SessionStatus.WAITING -> AoEPalette.Amber.copy(alpha = 0.55f)
        session.status == SessionStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        session.unread -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    var menuOpen by remember { mutableStateOf(false) }
    val hasDetailRow = !session.branch.isNullOrBlank() ||
        session.groupPath.isNotBlank() ||
        session.status == SessionStatus.WAITING ||
        session.dormant ||
        session.isArchived ||
        session.isSnoozed ||
        session.isTrashed ||
        muted
    val colorDot = when (session.color) {
        "red" -> Color(0xFFE05A5A)
        "amber" -> Color(0xFFF0A43A)
        "green" -> Color(0xFF5FBF83)
        else -> null
    }

    Card(
        onClick = actions.onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (session.isArchived) 0.7f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = highlight,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = if (embedded) null else BorderStroke(1.dp, borderColor),
        shape = if (embedded) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(
                start = if (embedded) 14.dp else 12.dp,
                top = 8.dp,
                end = 4.dp,
                bottom = 8.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(session.status, size = if (session.unread) 11 else 9)
                Spacer(Modifier.width(8.dp))
                if (colorDot != null) {
                    Box(
                        Modifier
                            .padding(end = 5.dp)
                            .size(7.dp)
                            .background(colorDot, CircleShape),
                    )
                }
                Text(
                    session.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (session.unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    TimeFormat.formatAge(session.activityAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
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
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions de la session", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (session.isTrashed) {
                            DropdownMenuItem(
                                text = { Text("Restaurer") },
                                onClick = {
                                    menuOpen = false
                                    actions.onRestore()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Renommer") },
                                onClick = {
                                    menuOpen = false
                                    actions.onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Mettre en pause") },
                                onClick = {
                                    menuOpen = false
                                    actions.onSnooze()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Couleur") },
                                onClick = {
                                    menuOpen = false
                                    actions.onColor()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Déplacer vers un groupe") },
                                onClick = {
                                    menuOpen = false
                                    actions.onMoveGroup()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (session.isPinned) "Désépingler" else "Épingler") },
                                onClick = {
                                    menuOpen = false
                                    actions.onTogglePin()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (session.favorited) "Retirer des favoris" else "Ajouter aux favoris")
                                },
                                onClick = {
                                    menuOpen = false
                                    actions.onToggleFavorite()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (muted) "Réactiver les notifications" else "Muter les notifications")
                                },
                                onClick = {
                                    menuOpen = false
                                    actions.onToggleMuted()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (session.unread) "Marquer comme lu" else "Marquer comme non lu") },
                                onClick = {
                                    menuOpen = false
                                    actions.onToggleRead()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (session.isArchived) "Désarchiver" else "Archiver") },
                                onClick = {
                                    menuOpen = false
                                    actions.onArchive()
                                },
                            )
                            if (session.status == SessionStatus.STOPPED || session.status == SessionStatus.ERROR) {
                                DropdownMenuItem(
                                    text = { Text("Démarrer") },
                                    onClick = {
                                        menuOpen = false
                                        actions.onStart()
                                    },
                                )
                            }
                            if (session.status != SessionStatus.STOPPED) {
                                DropdownMenuItem(
                                    text = { Text("Arrêter") },
                                    onClick = {
                                        menuOpen = false
                                        actions.onStop()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Redémarrer") },
                                onClick = {
                                    menuOpen = false
                                    actions.onRestart()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Monter") },
                                onClick = {
                                    menuOpen = false
                                    actions.onMoveUp()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Descendre") },
                                onClick = {
                                    menuOpen = false
                                    actions.onMoveDown()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Mettre à la corbeille", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    actions.onTrash()
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 17.dp, end = 8.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val model = session.model?.trim().orEmpty().substringAfterLast('/')
                Text(
                    if (model.isBlank()) session.displayAgent else "${session.displayAgent} · $model",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(session.status)
            }

            if (hasDetailRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 17.dp, top = 5.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!session.branch.isNullOrBlank()) {
                    BadgeText(session.branch, projectAccent(session.mainRepoPath ?: session.projectPath))
                }
                if (session.groupPath.isNotBlank()) {
                    BadgeText("#${session.groupPath}", MaterialTheme.colorScheme.secondary)
                }
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
                if (session.isTrashed) {
                    Text(
                        "corbeille",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
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
}

@Composable
private fun BadgeText(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RenameSessionDialog(session: Session, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(session.id) { mutableStateOf(session.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer la session") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Titre") },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(value)
                    onDismiss()
                },
                enabled = value.isNotBlank(),
            ) { Text("Renommer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SnoozeSessionDialog(session: Session, onDismiss: () -> Unit, onSnooze: (Int?) -> Unit) {
    val presets = listOf(
        60 to "1 heure",
        120 to "2 heures",
        180 to "3 heures",
        240 to "4 heures",
        360 to "6 heures",
        1_440 to "1 jour",
        10_080 to "1 semaine",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mettre en pause") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (session.isSnoozed) {
                    TextButton(
                        onClick = {
                            onSnooze(null)
                            onDismiss()
                        },
                    ) { Text("Réveiller maintenant") }
                    HorizontalDivider()
                }
                presets.forEach { (minutes, label) ->
                    TextButton(
                        onClick = {
                            onSnooze(minutes)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ColorSessionDialog(session: Session, onDismiss: () -> Unit, onColor: (String?) -> Unit) {
    val colors = listOf(
        "red" to "Rouge",
        "amber" to "Ambre",
        "green" to "Vert",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur de la session") },
        text = {
            Column {
                colors.forEach { (value, label) ->
                    TextButton(
                        onClick = {
                            onColor(value)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label + if (session.color == value) "  ✓" else "") }
                }
                TextButton(
                    onClick = {
                        onColor(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Aucune") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun TrashSessionDialog(session: Session, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mettre à la corbeille ?") },
        text = {
            Text(
                "La session « ${session.title} » sera arrêtée et masquée. " +
                    "Sa transcription et ses fichiers sont conservés et elle pourra être restaurée.",
            )
        },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("Mettre à la corbeille") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}


@Composable
private fun GroupPathDialog(
    title: String,
    initial: String,
    confirmLabel: String,
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
                singleLine = true,
                label = { Text("Chemin du groupe") },
                placeholder = { Text("equipe/projet") },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(value)
                },
                enabled = value.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MoveSessionGroupDialog(
    session: Session,
    groups: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Déplacer la session") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Groupe actuel : ${session.groupPath.ifBlank { "Sans groupe" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onSelect("") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sans groupe") }
                groups.forEach { group ->
                    TextButton(
                        onClick = { onSelect(group) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(group) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun DeleteGroupDialog(
    path: String,
    sessionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var mode by remember { mutableStateOf(if (sessionCount == 0) "keep_sessions" else "move_to_parent") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer le groupe") },
        text = {
            Column {
                Text(path, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(
                    "Sessions concernées : $sessionCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val options = listOf(
                    "move_to_parent" to "Déplacer les sessions vers le parent",
                    "keep_sessions" to "Conserver les sessions, sans groupe",
                    "delete_sessions" to "Supprimer définitivement les sessions",
                )
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = value }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == value, onClick = { mode = value })
                        Text(
                            label,
                            color = if (value == "delete_sessions") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                if (mode == "delete_sessions") {
                    Text(
                        "Cette action est destructive et supprime les fichiers gérés par AoE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(mode) }) {
                Text(if (mode == "delete_sessions") "Supprimer tout" else "Supprimer le groupe")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun WorkspaceOrderDialog(
    workspaces: List<WorkspaceUi>,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordre des workspaces") },
        text = {
            if (workspaces.isEmpty()) {
                Text("Aucun workspace.")
            } else {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    workspaces.forEachIndexed { index, workspace ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${workspace.label} · ${workspace.sessions.size} session(s)",
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(
                                onClick = { onMove(index, -1) },
                                enabled = index > 0,
                            ) { Text("Monter") }
                            TextButton(
                                onClick = { onMove(index, 1) },
                                enabled = index < workspaces.lastIndex,
                            ) { Text("Descendre") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        dismissButton = {},
    )
}


@Composable
private fun EmptySessions(
    hasQuery: Boolean,
    onCreateSession: () -> Unit,
    onClearQuery: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (hasQuery) "Aucune session ne correspond" else "Aucune session pour le moment",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasQuery) {
                "Essayez un autre terme, ou effacez la recherche pour retrouver vos sessions."
            } else {
                "Créez votre première session Agent of Empires depuis le téléphone."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = if (hasQuery) onClearQuery else onCreateSession) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (hasQuery) "Effacer la recherche" else "Nouvelle session")
        }
    }
}
