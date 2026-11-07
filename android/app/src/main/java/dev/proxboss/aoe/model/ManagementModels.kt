package dev.proxboss.aoe.model

data class McpServerView(
    val name: String,
    val transport: String,
    val command: String?,
    val args: List<String>,
    val url: String?,
    val envNames: List<String>,
    val headerNames: List<String>,
    val provenance: String,
    val shadowed: List<String>,
)

data class McpConflictView(
    val name: String,
    val agent: String,
    val previous: String,
    val current: String,
    val fingerprint: String,
)

data class McpServersView(
    val agent: String,
    val effective: List<McpServerView>,
    val keptOnRemoval: List<McpServerView>,
    val conflicts: List<McpConflictView>,
    val driftPaused: Boolean,
)

data class McpSecretEntry(val name: String, val value: String?)

data class McpServerInput(
    val transport: String,
    val command: String?,
    val args: List<String>,
    val url: String?,
    val env: List<McpSecretEntry>,
    val headers: List<McpSecretEntry>,
)

data class SkillSummary(
    val directory: String,
    val name: String,
    val description: String,
    val source: String,
    val provenanceLabel: String,
    val writable: Boolean,
)

data class SkillRoot(
    val id: String,
    val label: String,
    val relativePath: String,
    val consumers: List<String>,
    val legacy: Boolean,
)

data class SkillsView(
    val skills: List<SkillSummary>,
    val roots: List<SkillRoot>,
)

data class SkillDetail(
    val directory: String,
    val name: String,
    val description: String,
    val source: String,
    val content: String,
)

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val builtin: Boolean,
    val validation: String,
    val source: String?,
    val capabilities: List<String>,
    val granted: Boolean,
    val needsReapproval: Boolean,
)

data class PluginList(
    val plugins: List<PluginInfo>,
    val loadErrors: List<String>,
)

data class PluginUpdateInfo(
    val id: String,
    val source: String,
    val current: String,
    val available: String?,
    val needsUpdate: Boolean,
    val error: String?,
)

data class ProfileInfo(
    val name: String,
    val isDefault: Boolean,
    val description: String,
)

data class DeviceInfo(
    val sessionId: String,
    val userAgent: String,
    val createdIp: String,
    val createdAt: String,
    val lastSeen: String,
    val current: Boolean,
)

data class PasskeyInfo(
    val credentialId: String,
    val label: String,
    val origin: String,
    val createdAtMs: Long,
    val lastUsedAtMs: Long?,
)

data class HealthAgent(
    val id: String,
    val title: String,
    val cpuFraction: Double?,
    val memoryBytes: Long?,
    val procs: Int?,
    val sandboxed: Boolean,
)

data class SystemHealth(
    val status: String,
    val cpuFraction: Double?,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long,
    val loadAverage: List<Double>,
    val swapUsedBytes: Long,
    val swapTotalBytes: Long,
    val agentCount: Int,
    val procCount: Int,
    val agents: List<HealthAgent>,
)

data class LogsSnapshot(
    val status: String,
    val content: String,
    val lineCount: Int,
    val truncated: Boolean,
)

data class UpdateStatusInfo(
    val updateCheckMode: String,
    val currentVersion: String,
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val releaseUrl: String?,
    val error: String?,
    val installMethod: String,
    val updateCommand: String,
)
