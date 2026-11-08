package dev.proxboss.aoe.model

import java.time.Instant

enum class SessionStatus(val wire: String, val labelFr: String) {
    RUNNING("Running", "En cours"),
    WAITING("Waiting", "En attente"),
    IDLE("Idle", "Au repos"),
    ERROR("Error", "Erreur"),
    STARTING("Starting", "Démarrage"),
    STOPPED("Stopped", "Arrêtée"),
    DELETING("Deleting", "Suppression"),
    CREATING("Creating", "Création"),
    UNKNOWN("Unknown", "Inconnu");

    val needsAttention: Boolean
        get() = this == WAITING || this == ERROR

    companion object {
        fun fromWire(value: String?): SessionStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class Session(
    val id: String,
    val title: String,
    val projectPath: String,
    val groupPath: String,
    val tool: String,
    val agent: String?,
    val status: SessionStatus,
    val dormant: Boolean,
    val favorited: Boolean,
    val pinnedAt: String?,
    val archivedAt: String?,
    val snoozedUntil: String?,
    val trashedAt: String?,
    val unread: Boolean,
    val notifyOnWaiting: Boolean?,
    val notifyOnIdle: Boolean?,
    val notifyOnError: Boolean?,
    val view: String?,
    val acpCapable: Boolean,
    val acpWorkerState: String?,
    val hasTerminal: Boolean,
    val scratch: Boolean = false,
    val lastAccessedAt: Instant?,
    val idleEnteredAt: Instant?,
    val createdAt: Instant?,
    val lastError: String?,
    val branch: String?,
    val mainRepoPath: String? = null,
    val color: String? = null,
    val profile: String = "",
    val parentSessionId: String? = null,
    val model: String? = null,
) {
    val isPinned: Boolean get() = pinnedAt != null
    val isArchived: Boolean get() = archivedAt != null
    val isSnoozed: Boolean get() = snoozedUntil != null
    val isTrashed: Boolean get() = trashedAt != null
    val isStructured: Boolean get() = view != "terminal" && acpCapable
    val displayAgent: String get() = agent?.takeIf { it.isNotBlank() } ?: tool
    val activityAt: Instant? get() = lastAccessedAt ?: createdAt

    fun isMutedFor(event: NotifyEvent): Boolean = when (event) {
        NotifyEvent.WAITING -> notifyOnWaiting == false
        NotifyEvent.IDLE -> notifyOnIdle == false
        NotifyEvent.ERROR -> notifyOnError == false
    }
}

enum class NotifyEvent(val wire: String, val labelFr: String) {
    WAITING("waiting", "approbation requise"),
    IDLE("idle", "run terminé"),
    ERROR("error", "erreur");

    companion object {
        fun fromWire(value: String?): NotifyEvent? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

data class AboutInfo(
    val version: String,
    val authRequired: Boolean,
    val authMode: String,
    val readOnly: Boolean,
    val profile: String,
)

data class SearchHit(
    val sessionId: String,
    val seq: Long,
    val snippet: String,
    val kind: String,
    val matchCount: Int,
)

enum class SessionFilter(val labelFr: String) {
    ACTIVE("Actives"),
    FAVORITES("Favoris"),
    ALL("Toutes"),
    ARCHIVED("Archivées"),
    TRASHED("Corbeille"),
}
