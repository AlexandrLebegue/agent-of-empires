package dev.proxboss.aoe.model

data class AgentInfo(
    val name: String,
    val displayName: String,
    val kind: String,
    val installed: Boolean,
    val acpCapable: Boolean,
    val acpInstalled: Boolean,
    val acpCreateReady: Boolean,
    val acpAllowed: Boolean,
    val alwaysYolo: Boolean,
    val acpAlwaysYolo: Boolean,
) {
    val canRunStructured: Boolean
        get() = acpCapable && acpAllowed && (acpInstalled || acpCreateReady)
}

data class DirEntry(
    val name: String,
    val path: String,
    val isGitRepo: Boolean,
)

data class BrowseResult(
    val entries: List<DirEntry>,
    val hasMore: Boolean,
)

data class RecentProject(
    val path: String,
    val displayName: String,
    val tool: String,
)

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
    val remoteOnly: Boolean,
)

data class GroupInfo(
    val path: String,
    val sessionCount: Int,
    val archived: Boolean,
)

data class CreationFingerprint(
    val projectPath: String,
    val baseHooksHash: String,
    val hooksHash: String?,
    val mcpHash: String?,
)

data class HookCommands(
    val onCreate: List<String> = emptyList(),
    val onLaunch: List<String> = emptyList(),
    val onDestroy: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = onCreate.isEmpty() && onLaunch.isEmpty() && onDestroy.isEmpty()
}

data class CreationTrustReview(
    val fingerprint: CreationFingerprint?,
    val mergedHooks: HookCommands,
    val repoHooks: HookCommands,
    val mcpSummaries: List<String>,
    val hooksNeedTrust: Boolean,
    val mcpNeedTrust: Boolean,
) {
    val requiresTrust: Boolean get() = hooksNeedTrust || mcpNeedTrust
}

data class CreationProgress(
    val stage: String,
    val hook: String?,
    val output: List<String>,
) {
    val labelFr: String
        get() = when (stage) {
            "preparing" -> "Préparation"
            "starting_container" -> "Démarrage du conteneur"
            "running_hooks" -> "Exécution des hooks"
            "starting" -> "Démarrage de l'agent"
            else -> "Création"
        }
}

data class CreateSessionRequest(
    val path: String,
    val tool: String,
    val title: String?,
    val group: String?,
    val structured: Boolean,
    val agentModel: String?,
    val agentEffort: String?,
    val yoloMode: Boolean,
    val worktreeEnabled: Boolean,
    val worktreeBranch: String?,
    val createNewBranch: Boolean,
    val baseBranch: String?,
    val scratch: Boolean,
    val trustHooks: Boolean? = null,
    val trustReview: CreationFingerprint? = null,
    val idempotencyKey: String,
)

data class CreateSessionResult(
    val session: Session? = null,
    val hooksRequired: CreationTrustReview? = null,
    val trustChanged: Boolean = false,
    val error: String? = null,
)
