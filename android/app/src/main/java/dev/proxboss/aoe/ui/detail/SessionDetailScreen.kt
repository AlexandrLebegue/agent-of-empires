package dev.proxboss.aoe.ui.detail

import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.aoe.model.QueuedPrompt
import dev.proxboss.aoe.util.ImagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.proxboss.aoe.model.AcpControl
import dev.proxboss.aoe.model.Approval
import dev.proxboss.aoe.model.ConfigOption
import dev.proxboss.aoe.model.DiffPreview
import dev.proxboss.aoe.model.DisplayItem
import dev.proxboss.aoe.model.MessageDisplay
import dev.proxboss.aoe.model.NoticeDisplay
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionStatus
import dev.proxboss.aoe.model.ToolDisplay
import dev.proxboss.aoe.model.UserDisplay
import dev.proxboss.aoe.ui.common.MarkdownText
import dev.proxboss.aoe.ui.common.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
    onOpenDiff: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showSwitchView by remember { mutableStateOf(false) }
    var composer by rememberSaveable { mutableStateOf("") }
    var queueEditTarget by remember { mutableStateOf<QueuedPrompt?>(null) }
    val listState = rememberLazyListState()
    val terminalScroll = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = withContext(Dispatchers.IO) {
                    ImagePayload.prepare(context, uri)
                }
                if (payload == null) {
                    viewModel.showError("Image illisible ou trop volumineuse")
                } else {
                    viewModel.uploadImage(payload.mimeType, payload.base64) { path ->
                        composer = if (composer.isBlank()) path else composer + "\n" + path
                    }
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(ui.rows.lastOrNull()?.key) {
        if (ui.rows.isNotEmpty()) {
            listState.animateScrollToItem(ui.rows.lastIndex)
        }
    }
    var previousFirstVisible by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index <= 1 && previousFirstVisible > 1) {
                viewModel.loadOlder()
            }
            previousFirstVisible = index
        }
    }
    LaunchedEffect(ui.terminalText) {
        if (ui.terminalText.isNotEmpty()) {
            terminalScroll.animateScrollTo(terminalScroll.maxValue)
        }
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
                            ui.session?.title ?: "Session",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                ui.session?.let { append(it.displayAgent) }
                                ui.session?.model?.let { if (isNotEmpty()) append(" · "); append(it) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    DetailsMenu(
                        session = ui.session,
                        isTerminal = ui.transport == DetailTransport.TERMINAL,
                        onOpenDiff = { ui.session?.let { onOpenDiff(it.id) } },
                        onOpenSettings = { showSettings = true },
                        onRetry = viewModel::retryConnection,
                        onSwitchView = { showSwitchView = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SessionHeader(ui)

            if (ui.offline) {
                Banner(
                    text = "Serveur injoignable — les données affichées peuvent être périmées.",
                    error = true,
                    onDismiss = null,
                )
            }
            ui.notice?.let { notice ->
                Banner(text = notice, error = false, onDismiss = viewModel::dismissNotice)
            }
            ui.error?.let { error ->
                Banner(text = error, error = true, onDismiss = viewModel::dismissError)
            }
            ui.control.startupError?.let { error ->
                Banner(text = error, error = true, onDismiss = null)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    ui.transport == DetailTransport.TERMINAL -> TerminalPane(ui.terminalText, terminalScroll)
                    ui.rows.isEmpty() && (ui.connecting || ui.transport == DetailTransport.NONE) -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text("Connexion au transcript…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ui.rows.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                if (ui.error != null) {
                                    "Impossible d'afficher l'historique."
                                } else {
                                    "Aucun message dans le transcript."
                                },
                                color = if (ui.error != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            ui.error?.let { error ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            OutlinedButton(onClick = viewModel::retryConnection) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Reconnecter")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (ui.loadingOlder) {
                                item(key = "older-loading") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            } else if (ui.hasOlder) {
                                item(key = "older-more") {
                                    TextButton(
                                        onClick = viewModel::loadOlder,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Charger les messages plus anciens") }
                                }
                            }
                            items(ui.rows, key = { it.key }) { item -> DisplayRow(item) }
                        }
                    }
                }
            }

            if (ui.transport == DetailTransport.ACP && ui.queuedPrompts.isNotEmpty()) {
                QueueStrip(
                    prompts = ui.queuedPrompts,
                    loading = ui.queueLoading,
                    sendingId = ui.queueSendingId,
                    interruptsTurn = ui.control.turnActive,
                    onSendNow = viewModel::sendQueuedPrompt,
                    onEdit = { queueEditTarget = it },
                    onDelete = viewModel::deleteQueuedPrompt,
                    onRefresh = viewModel::loadQueue,
                    onClear = viewModel::clearQueue,
                )
            }

            if (ui.control.pendingApprovals.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.control.pendingApprovals.forEach { approval ->
                        ApprovalCard(
                            approval = approval,
                            submitting = ui.approvalAction == approval.nonce,
                            onResolve = { decision, optionId ->
                                viewModel.resolveApproval(approval, decision, optionId)
                            },
                        )
                    }
                }
            } else if (ui.session?.status == SessionStatus.WAITING && ui.transport != DetailTransport.TERMINAL) {
                Surface(
                    color = Color(0xFFF0A43A).copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF0A43A),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "L'agent attend une réponse.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF0A43A),
                        )
                    }
                }
            }

            ComposerArea(
                ui = ui,
                value = composer,
                onValueChange = { composer = it },
                onSend = {
                    if (composer.isNotBlank()) {
                        val outgoing = composer
                        viewModel.sendPrompt(outgoing) {
                            if (composer == outgoing) composer = ""
                        }
                    }
                },
                onAttachImage = { imagePicker.launch("image/*") },
                viewModel = viewModel,
            )
        }
    }

    queueEditTarget?.let { prompt ->
        QueueEditDialog(
            prompt = prompt,
            onDismiss = { queueEditTarget = null },
            onConfirm = { text ->
                viewModel.editQueuedPrompt(prompt, text)
                queueEditTarget = null
            },
        )
    }

    if (showSwitchView) {
        AlertDialog(
            onDismissRequest = { showSwitchView = false },
            title = {
                Text(if (ui.transport == DetailTransport.TERMINAL) "Passer en vue structurée ?" else "Passer en vue terminal ?")
            },
            text = {
                Text(
                    if (ui.transport == DetailTransport.TERMINAL) {
                        "La session bascule vers le transcript ACP. Selon l'agent, le contexte peut être repris ou une nouvelle conversation démarrée."
                    } else {
                        "La session bascule vers le terminal tmux. Le transcript ACP reste disponible et vous pourrez revenir en vue structurée."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSwitchView = false
                    if (ui.transport == DetailTransport.TERMINAL) {
                        viewModel.switchToStructured()
                    } else {
                        viewModel.switchToTerminal()
                    }
                }) { Text("Basculer") }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchView = false }) { Text("Annuler") }
            },
        )
    }

    if (showSettings) {
        ConfigSheet(
            ui = ui,
            onDismiss = { showSettings = false },
            onSetOption = viewModel::setConfigOption,
            onSetMode = viewModel::setMode,
            onSwitchAgent = viewModel::switchAgent,
            onToggleMuted = viewModel::toggleMuted,
            onOpenDiff = { ui.session?.let { onOpenDiff(it.id) } },
            onRetry = viewModel::retryConnection,
        )
    }
}

@Composable
private fun SessionHeader(ui: SessionDetailUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ui.session?.let { StatusPill(it.status) }
        Text(
            when (ui.transport) {
                DetailTransport.ACP -> if (ui.connected) "transcript ACP" else "transcript ACP hors ligne"
                DetailTransport.TERMINAL -> "terminal"
                DetailTransport.NONE -> "connexion"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (ui.viewSwitching) {
            Text("bascule de vue…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        ui.control.currentModeChoice?.takeIf { it.isNotBlank() }?.let { mode ->
            Text(
                mode,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (ui.control.cancelling) {
            Text("annulation…", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF0A43A))
        }
        if (ui.control.turnActive) {
            Text("tour en cours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            when {
                ui.connecting -> "connexion"
                ui.connected -> "en direct"
                else -> "hors ligne"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (ui.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailsMenu(
    session: Session?,
    isTerminal: Boolean,
    onOpenDiff: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onSwitchView: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Plus d'actions")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Réglages de session") },
                onClick = {
                    open = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = { Text("Voir le diff") },
                onClick = {
                    open = false
                    onOpenDiff()
                },
                enabled = session != null,
            )
            DropdownMenuItem(
                text = { Text(if (isTerminal) "Passer en vue structurée" else "Passer en vue terminal") },
                onClick = {
                    open = false
                    onSwitchView()
                },
            )
            DropdownMenuItem(
                text = { Text("Reconnecter le flux") },
                onClick = {
                    open = false
                    onRetry()
                },
            )
        }
    }
}

@Composable
private fun Banner(text: String, error: Boolean, onDismiss: (() -> Unit)?) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (error) Icons.Default.Error else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TerminalPane(text: String, scrollState: androidx.compose.foundation.ScrollState) {
    Surface(
        color = Color(0xFF0B0E10),
        modifier = Modifier.fillMaxSize(),
    ) {
        SelectionContainer {
            Text(
                text = text.ifBlank { "Terminal vide." },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(8.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFD5E2DF),
            )
        }
    }
}

@Composable
private fun DisplayRow(item: DisplayItem) {
    when (item) {
        is UserDisplay -> UserRow(item)
        is MessageDisplay -> MessageRow(item)
        is ToolDisplay -> ToolRow(item)
        is NoticeDisplay -> NoticeRow(item)
    }
}

@Composable
private fun UserRow(item: UserDisplay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp, 12.dp, 3.dp, 12.dp),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                SelectionContainer {
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (item.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.attachments.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(item: MessageDisplay) {
    if (item.thinking) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    "Réflexion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        SelectionContainer {
            MarkdownText(
                text = item.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun ToolRow(item: ToolDisplay) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "${item.name}, ${if (expanded) "réduire" else "développer"} le détail"
            }
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    item.name.ifBlank { "Outil" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.kind.isNotBlank()) {
                    Text(
                        item.kind,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (item.isError) Icons.Default.Error else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (item.args.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                SelectionContainer {
                    Text(
                        item.args,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 18 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.diffs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                item.diffs.take(if (expanded) item.diffs.size else 2).forEach { diff ->
                    DiffPreviewBlock(diff, expanded)
                }
            }
            if (item.output.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(5.dp),
                ) {
                    SelectionContainer {
                        Text(
                            item.output,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) 40 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffPreviewBlock(diff: DiffPreview, expanded: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            diff.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val oldCount = diff.oldText?.lines()?.size ?: 0
        val newCount = diff.newText?.lines()?.size ?: 0
        Text(
            "−$oldCount / +$newCount lignes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            if (!diff.oldText.isNullOrBlank()) {
                Text(
                    diff.oldText.lines().take(16).joinToString("\n") { "- $it" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!diff.newText.isNullOrBlank()) {
                Text(
                    diff.newText.lines().take(16).joinToString("\n") { "+ $it" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF5FBF83),
                )
            }
        }
    }
}

@Composable
private fun NoticeRow(item: NoticeDisplay) {
    Text(
        item.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ApprovalCard(
    approval: Approval,
    submitting: Boolean,
    onResolve: (String, String?) -> Unit,
) {
    var planOpen by rememberSaveable(approval.nonce) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (approval.destructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (approval.destructive) Icons.Default.Warning else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        approval.choice -> "Question de l'agent"
                        approval.destructive -> "Action destructive — approbation requise"
                        else -> "Approbation requise"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                approval.toolCall.name.ifBlank { "Outil" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (approval.toolCall.argsPreview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    approval.toolCall.argsPreview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (planOpen) 20 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!approval.plan.isNullOrBlank()) {
                TextButton(onClick = { planOpen = !planOpen }) {
                    Text(if (planOpen) "Masquer le plan" else "Voir le plan")
                }
                if (planOpen) {
                    Text(approval.plan, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))

            if (approval.choice && approval.options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    approval.options.forEach { option ->
                        OutlinedButton(
                            onClick = { onResolve("Allow", option.optionId) },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(option.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(
                        onClick = { onResolve("Cancelled", null) },
                        enabled = !submitting,
                    ) { Text("Ignorer") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onResolve("Allow", null) },
                        enabled = !submitting,
                    ) { Text("Accepter") }
                    OutlinedButton(
                        onClick = { onResolve("AllowAlways", null) },
                        enabled = !submitting,
                    ) { Text("Toujours") }
                    OutlinedButton(
                        onClick = { onResolve("Deny", null) },
                        enabled = !submitting,
                    ) { Text("Refuser") }
                }
            }
            if (submitting) {
                Spacer(Modifier.height(5.dp))
                Text("Envoi…", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ComposerArea(
    ui: SessionDetailUiState,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    viewModel: SessionDetailViewModel,
) {
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (ui.transport == DetailTransport.TERMINAL) {
                                "Envoyer au terminal…"
                            } else {
                                "Écrire à l'agent…"
                            },
                        )
                    },
                    maxLines = 5,
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank() && !ui.sending,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (ui.sending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onAttachImage,
                    enabled = !ui.imageUploading,
                ) {
                    if (ui.imageUploading) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Image")
                    }
                }
                if (ui.control.turnActive) {
                    OutlinedButton(
                        onClick = viewModel::cancelTurn,
                        enabled = ui.sessionAction == null,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Annuler le tour")
                    }
                }
                if (ui.session?.status == SessionStatus.STOPPED || ui.session?.status == SessionStatus.ERROR) {
                    OutlinedButton(
                        onClick = viewModel::startSession,
                        enabled = ui.sessionAction == null,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Démarrer")
                    }
                }
                if (ui.session?.status != SessionStatus.STOPPED) {
                    OutlinedButton(
                        onClick = viewModel::stopSession,
                        enabled = ui.sessionAction == null,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Arrêter")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::restartSession,
                    enabled = ui.sessionAction == null,
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Redémarrer")
                }
                OutlinedButton(
                    onClick = viewModel::retryConnection,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reconnecter")
                }
                ui.sessionAction?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigSheet(
    ui: SessionDetailUiState,
    onDismiss: () -> Unit,
    onSetOption: (String, String) -> Unit,
    onSetMode: (String) -> Unit,
    onSwitchAgent: (String) -> Unit,
    onToggleMuted: () -> Unit,
    onOpenDiff: () -> Unit,
    onRetry: () -> Unit,
) {
    val muted = ui.session?.let {
        it.notifyOnWaiting == false && it.notifyOnIdle == false && it.notifyOnError == false
    } ?: false
    var showAgentSwitch by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Text("Réglages de session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ui.session?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${it.displayAgent}${it.model?.let { model -> " · $model" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (ui.transport == DetailTransport.ACP) {
                if (ui.control.modeChoices.isNotEmpty()) {
                    ModePickerRow(
                        current = ui.control.currentModeChoice,
                        choices = ui.control.modeChoices,
                        enabled = ui.configAction == null,
                        onSelect = onSetMode,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                ConfigPicker("Modèle", ui.control.modelOption, ui.control, onSetOption)
                Spacer(Modifier.height(10.dp))
                ConfigPicker("Effort", ui.control.effortOption, ui.control, onSetOption)
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Agent ACP", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            ui.control.agent ?: ui.session?.displayAgent ?: "Inconnu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ui.switchingAgent != null) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(
                            onClick = { showAgentSwitch = true },
                            enabled = ui.acpAgents.size > 1 && ui.sessionAction == null,
                        ) { Text("Changer") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleMuted)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Muter cette session", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Aucune notification pour approbation, erreur ou fin de run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = muted, onCheckedChange = { onToggleMuted() })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = onOpenDiff, modifier = Modifier.fillMaxWidth()) {
                Text("Voir le diff de la session")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Reconnecter le flux")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAgentSwitch) {
        AlertDialog(
            onDismissRequest = { showAgentSwitch = false },
            title = { Text("Changer d'agent ACP") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ui.acpAgents
                        .filter { it.name != ui.control.agent }
                        .forEach { agent ->
                            TextButton(
                                onClick = {
                                    showAgentSwitch = false
                                    onSwitchAgent(agent.name)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(agent.name)
                                    if (agent.description.isNotBlank()) {
                                        Text(
                                            agent.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAgentSwitch = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ModePickerRow(
    current: String?,
    choices: List<dev.proxboss.aoe.model.ConfigOptionChoice>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = choices.firstOrNull { it.value == current }?.name ?: current ?: "Mode"
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text("Mode", style = MaterialTheme.typography.labelSmall)
                Text(currentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.name,
                            fontWeight = if (choice.value == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(choice.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigPicker(
    label: String,
    option: ConfigOption?,
    control: AcpControl,
    onSetOption: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (option == null) {
        Text(
            "$label : non exposé par l'agent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val currentName = option.choices.firstOrNull { it.value == option.currentValue }?.name
        ?: option.currentValue.ifBlank { "—" }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(currentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            option.choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.name,
                            fontWeight = if (choice.value == option.currentValue) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSetOption(option.id, choice.value)
                    },
                    enabled = control.configOptions.isNotEmpty(),
                )
            }
        }
    }
}


@Composable
private fun QueueStrip(
    prompts: List<QueuedPrompt>,
    loading: Boolean,
    sendingId: String?,
    interruptsTurn: Boolean,
    onSendNow: (QueuedPrompt) -> Unit,
    onEdit: (QueuedPrompt) -> Unit,
    onDelete: (QueuedPrompt) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "File d'attente (${prompts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh, enabled = !loading) { Text("Actualiser") }
                TextButton(onClick = onClear, enabled = prompts.isNotEmpty()) { Text("Vider") }
            }
            if (interruptsTurn) {
                Text(
                    "Envoyer interrompt le tour en cours pour délivrer le message.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 170.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                prompts.forEach { prompt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                prompt.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (prompt.attachments.isNotEmpty()) {
                                Text(
                                    prompt.attachments.mapNotNull { it.name ?: it.kind }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onEdit(prompt) }) { Text("Modifier") }
                        TextButton(
                            onClick = { onSendNow(prompt) },
                            enabled = sendingId == null,
                        ) {
                            if (sendingId == prompt.id) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Envoyer")
                            }
                        }
                        TextButton(onClick = { onDelete(prompt) }) {
                            Text("Suppr.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun QueueEditDialog(
    prompt: QueuedPrompt,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(prompt.id) { mutableStateOf(prompt.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le prompt en attente") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.heightIn(min = 120.dp),
                label = { Text("Prompt") },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

