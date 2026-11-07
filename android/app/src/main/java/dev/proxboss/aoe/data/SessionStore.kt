package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionStore {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _models = MutableStateFlow<Map<String, String>>(emptyMap())
    val models: StateFlow<Map<String, String>> = _models.asStateFlow()

    private val _workspaceOrdering = MutableStateFlow<List<String>>(emptyList())
    val workspaceOrdering: StateFlow<List<String>> = _workspaceOrdering.asStateFlow()

    fun setWorkspaceOrdering(order: List<String>) {
        _workspaceOrdering.value = order
    }

    fun replaceAll(sessions: List<Session>) {
        val models = _models.value
        _sessions.value = sessions.map { session ->
            if (session.model == null && models.containsKey(session.id)) {
                session.copy(model = models[session.id])
            } else {
                session
            }
        }
    }

    fun setModels(models: Map<String, String>) {
        _models.value = models
        _sessions.value = _sessions.value.map { session ->
            session.copy(model = models[session.id] ?: session.model)
        }
    }

    fun setModel(sessionId: String, model: String) {
        if (sessionId.isBlank() || model.isBlank()) return
        _models.value = _models.value + (sessionId to model)
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) it.copy(model = model) else it
        }
    }

    fun applyStatus(sessionId: String, status: SessionStatus) {
        var changed = false
        val next = _sessions.value.map { session ->
            if (session.id == sessionId && session.status != status) {
                changed = true
                session.copy(status = status)
            } else {
                session
            }
        }
        if (changed) _sessions.value = next
    }

    fun upsert(session: Session) {
        val current = _sessions.value
        val model = session.model ?: _models.value[session.id]
        val merged = if (model != null) session.copy(model = model) else session
        val index = current.indexOfFirst { it.id == session.id }
        _sessions.value = if (index >= 0) {
            current.toMutableList().apply { this[index] = merged }
        } else {
            current + merged
        }
    }

    fun session(id: String): Session? = _sessions.value.firstOrNull { it.id == id }

    fun updateSession(id: String, transform: (Session) -> Session) {
        _sessions.value = _sessions.value.map { if (it.id == id) transform(it) else it }
    }
}
