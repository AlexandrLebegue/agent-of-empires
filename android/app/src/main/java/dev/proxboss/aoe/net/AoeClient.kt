package dev.proxboss.aoe.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class HttpReply(
    val status: Int,
    val body: String,
    val errorCode: String?,
) {
    val isSuccessful: Boolean get() = status in 200..299
}

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    data class Err(val message: String, val status: Int? = null) : ApiResult<Nothing>()

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Ok -> runCatching { Ok(transform(value)) }
            .getOrElse { Err("Réponse du serveur illisible") }
        is Err -> this
    }
}

class AoeClient(
    private val http: OkHttpClient = defaultHttpClient(),
) {
    @Volatile
    private var baseUrl: String = ""

    @Volatile
    private var token: String = ""

    /** Invoked on an authenticated 401 so the UI can return to the login screen. */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    fun configure(baseUrl: String, token: String) {
        this.baseUrl = normalizeBaseUrl(baseUrl)
        this.token = token.trim()
    }

    suspend fun get(path: String): ApiResult<String> = request("GET", path, null)

    suspend fun post(path: String, body: String? = null): ApiResult<String> =
        request("POST", path, body)

    suspend fun patch(path: String, body: String): ApiResult<String> =
        request("PATCH", path, body)

    suspend fun delete(path: String, body: String? = null): ApiResult<String> =
        request("DELETE", path, body)

    suspend fun put(path: String, body: String): ApiResult<String> =
        request("PUT", path, body)

    suspend fun postRaw(path: String, body: String? = null): ApiResult<HttpReply> =
        rawRequest("POST", path, body)

    fun openStatusSocket(listener: WebSocketListener): WebSocket? = openSocket("/api/sessions/status/ws", listener)

    fun openAcpSocket(sessionId: String, since: Long, listener: WebSocketListener): WebSocket? =
        openSocket("/sessions/${encodeSegment(sessionId)}/acp/ws?frames=0&since=$since", listener)

    fun openLiveSocket(sessionId: String, listener: WebSocketListener): WebSocket? =
        openSocket("/sessions/${encodeSegment(sessionId)}/live-ws", listener)

    private fun openSocket(path: String, listener: WebSocketListener): WebSocket? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val wsBase = base.toString().trimEnd('/')
        val requestBuilder = try {
            // OkHttp upgrades an http(s) URL to WebSocket; ws/wss are not
            // accepted by HttpUrl.
            Request.Builder().url(wsBase + path)
        } catch (error: Exception) {
            return null
        }
        if (token.isNotBlank()) requestBuilder.header("Authorization", "Bearer $token")
        return runCatching { http.newWebSocket(requestBuilder.build(), listener) }.getOrNull()
    }

    private suspend fun request(method: String, path: String, body: String?): ApiResult<String> =
        when (val result = rawRequest(method, path, body)) {
            is ApiResult.Ok -> {
                val reply = result.value
                if (reply.isSuccessful) {
                    ApiResult.Ok(reply.body)
                } else {
                    ApiResult.Err(describeError(reply.status, reply.body), reply.status)
                }
            }
            is ApiResult.Err -> result
        }

    private suspend fun rawRequest(method: String, path: String, body: String?): ApiResult<HttpReply> =
        withContext(Dispatchers.IO) {
            val base = baseUrl
            if (base.isBlank()) {
                return@withContext ApiResult.Err("Aucune connexion configurée")
            }
            val url = runCatching { base + path }.getOrElse {
                return@withContext ApiResult.Err("Adresse du serveur invalide")
            }
            val requestBuilder = try {
                Request.Builder().url(url)
            } catch (error: Exception) {
                return@withContext ApiResult.Err("Adresse du serveur invalide")
            }
            if (token.isNotBlank()) requestBuilder.header("Authorization", "Bearer $token")
            when (method) {
                "POST" -> requestBuilder.post((body ?: "").toRequestBody(JSON_MEDIA))
                "PATCH" -> requestBuilder.patch((body ?: "").toRequestBody(JSON_MEDIA))
                "PUT" -> requestBuilder.put((body ?: "").toRequestBody(JSON_MEDIA))
                "DELETE" -> requestBuilder.delete((body ?: "").toRequestBody(JSON_MEDIA))
                else -> requestBuilder.get()
            }
            try {
                http.newCall(requestBuilder.build()).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.code == 401) {
                        onUnauthorized?.invoke()
                    }
                    ApiResult.Ok(
                        HttpReply(
                            status = response.code,
                            body = text,
                            errorCode = response.header("aoe-error-code"),
                        ),
                    )
                }
            } catch (error: Exception) {
                ApiResult.Err(friendlyNetworkError(error))
            }
        }

    private fun describeError(status: Int, body: String): String {
        val detail = extractMessage(body)
        val base = when (status) {
            401 -> "Jeton refusé (401)"
            403 -> "Accès refusé (403)"
            404 -> "Introuvable (404)"
            405 -> "Action non supportée par ce serveur (405)"
            409 -> "Conflit (409)"
            422 -> "Requête invalide (422)"
            500, 502, 503, 504 -> "Serveur indisponible ($status)"
            else -> "Erreur HTTP $status"
        }
        return if (detail.isBlank()) base else "$base : $detail"
    }

    private fun extractMessage(body: String): String {
        if (body.isBlank()) return ""
        val parsed = runCatching { JSONObject(body) }.getOrNull()
        val message = parsed?.optString("message").orEmpty().ifBlank {
            parsed?.optString("error").orEmpty()
        }
        return (message.ifBlank { body })
            .replace(Regex("\\s+"), " ")
            .take(180)
    }

    private fun friendlyNetworkError(error: Exception): String {
        val message = error.message.orEmpty()
        return if (message.contains("Failed to connect", true) ||
            message.contains("timeout", true) ||
            message.contains("unreachable", true) ||
            message.contains("Connection refused", true)
        ) {
            "Serveur injoignable"
        } else {
            "Erreur réseau"
        }
    }

    private fun encodeSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun normalizeBaseUrl(input: String): String {
            var value = input.trim()
            if (value.isEmpty()) return ""
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            return value.trimEnd('/')
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
