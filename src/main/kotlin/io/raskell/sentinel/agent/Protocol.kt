package io.raskell.sentinel.agent

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Agent protocol version.
 */
const val PROTOCOL_VERSION: Int = 1

/**
 * Maximum message size (10MB).
 */
const val MAX_MESSAGE_SIZE: Int = 10 * 1024 * 1024

/**
 * Agent event type.
 */
@Serializable
enum class EventType {
    @SerialName("configure") CONFIGURE,
    @SerialName("request_headers") REQUEST_HEADERS,
    @SerialName("request_body_chunk") REQUEST_BODY_CHUNK,
    @SerialName("response_headers") RESPONSE_HEADERS,
    @SerialName("response_body_chunk") RESPONSE_BODY_CHUNK,
    @SerialName("request_complete") REQUEST_COMPLETE,
    @SerialName("websocket_frame") WEBSOCKET_FRAME
}

/**
 * Request metadata sent to agents.
 */
@Serializable
data class RequestMetadata(
    @SerialName("correlation_id") val correlationId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("client_ip") val clientIp: String,
    @SerialName("client_port") val clientPort: Int,
    @SerialName("server_name") val serverName: String? = null,
    val protocol: String = "HTTP/1.1",
    @SerialName("tls_version") val tlsVersion: String? = null,
    @SerialName("tls_cipher") val tlsCipher: String? = null,
    @SerialName("route_id") val routeId: String? = null,
    @SerialName("upstream_id") val upstreamId: String? = null,
    val timestamp: String = ""
)

/**
 * Configure event.
 */
@Serializable
data class ConfigureEvent(
    @SerialName("agent_id") val agentId: String,
    val config: JsonObject
)

/**
 * Request headers event.
 */
@Serializable
data class RequestHeadersEvent(
    val metadata: RequestMetadata,
    val method: String,
    val uri: String,
    val headers: Map<String, List<String>> = emptyMap()
)

/**
 * Request body chunk event.
 */
@Serializable
data class RequestBodyChunkEvent(
    @SerialName("correlation_id") val correlationId: String,
    val data: String,
    @SerialName("is_last") val isLast: Boolean = false,
    @SerialName("total_size") val totalSize: Long? = null,
    @SerialName("chunk_index") val chunkIndex: Int = 0,
    @SerialName("bytes_received") val bytesReceived: Long = 0
)

/**
 * Response headers event.
 */
@Serializable
data class ResponseHeadersEvent(
    @SerialName("correlation_id") val correlationId: String,
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap()
)

/**
 * Response body chunk event.
 */
@Serializable
data class ResponseBodyChunkEvent(
    @SerialName("correlation_id") val correlationId: String,
    val data: String,
    @SerialName("is_last") val isLast: Boolean = false,
    @SerialName("total_size") val totalSize: Long? = null,
    @SerialName("chunk_index") val chunkIndex: Int = 0,
    @SerialName("bytes_sent") val bytesSent: Long = 0
)

/**
 * Request complete event.
 */
@Serializable
data class RequestCompleteEvent(
    @SerialName("correlation_id") val correlationId: String,
    val status: Int,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("request_body_size") val requestBodySize: Long = 0,
    @SerialName("response_body_size") val responseBodySize: Long = 0,
    @SerialName("upstream_attempts") val upstreamAttempts: Int = 1,
    val error: String? = null
)

/**
 * Header modification operation.
 */
@Serializable
sealed class HeaderOp {
    @Serializable
    @SerialName("set")
    data class Set(val name: String, val value: String) : HeaderOp()

    @Serializable
    @SerialName("add")
    data class Add(val name: String, val value: String) : HeaderOp()

    @Serializable
    @SerialName("remove")
    data class Remove(val name: String) : HeaderOp()
}

/**
 * Body mutation from agent.
 */
@Serializable
data class BodyMutation(
    val data: String? = null,
    @SerialName("chunk_index") val chunkIndex: Int = 0
) {
    companion object {
        fun passThrough(chunkIndex: Int) = BodyMutation(data = null, chunkIndex = chunkIndex)
        fun dropChunk(chunkIndex: Int) = BodyMutation(data = "", chunkIndex = chunkIndex)
        fun replace(chunkIndex: Int, data: String) = BodyMutation(data = data, chunkIndex = chunkIndex)
    }

    fun isPassThrough(): Boolean = data == null
    fun isDrop(): Boolean = data == ""
}

/**
 * Protocol decision types.
 */
@Serializable
sealed class ProtocolDecision {
    @Serializable
    @SerialName("allow")
    data object Allow : ProtocolDecision()

    @Serializable
    @SerialName("block")
    data class Block(
        val status: Int,
        val body: String? = null,
        val headers: Map<String, String>? = null
    ) : ProtocolDecision()

    @Serializable
    @SerialName("redirect")
    data class Redirect(
        val url: String,
        val status: Int = 302
    ) : ProtocolDecision()

    @Serializable
    @SerialName("challenge")
    data class Challenge(
        @SerialName("challenge_type") val challengeType: String,
        val params: Map<String, String> = emptyMap()
    ) : ProtocolDecision()
}

/**
 * Audit metadata.
 */
@Serializable
data class AuditMetadata(
    val tags: List<String> = emptyList(),
    @SerialName("rule_ids") val ruleIds: List<String> = emptyList(),
    val confidence: Float? = null,
    @SerialName("reason_codes") val reasonCodes: List<String> = emptyList(),
    val custom: Map<String, JsonElement> = emptyMap()
)

/**
 * Agent request message.
 */
@Serializable
data class AgentRequest(
    val version: Int,
    @SerialName("event_type") val eventType: EventType,
    val payload: JsonElement
)

/**
 * Agent response message.
 */
@Serializable
data class AgentResponse(
    val version: Int = PROTOCOL_VERSION,
    val decision: ProtocolDecision = ProtocolDecision.Allow,
    @SerialName("request_headers") val requestHeaders: List<HeaderOp> = emptyList(),
    @SerialName("response_headers") val responseHeaders: List<HeaderOp> = emptyList(),
    @SerialName("routing_metadata") val routingMetadata: Map<String, String> = emptyMap(),
    val audit: AuditMetadata = AuditMetadata(),
    @SerialName("needs_more") val needsMore: Boolean = false,
    @SerialName("request_body_mutation") val requestBodyMutation: BodyMutation? = null,
    @SerialName("response_body_mutation") val responseBodyMutation: BodyMutation? = null,
    @SerialName("websocket_decision") val websocketDecision: String? = null
) {
    companion object {
        fun defaultAllow() = AgentResponse()

        fun block(status: Int, body: String? = null) = AgentResponse(
            decision = ProtocolDecision.Block(status = status, body = body)
        )

        fun redirect(url: String, status: Int = 302) = AgentResponse(
            decision = ProtocolDecision.Redirect(url = url, status = status)
        )
    }
}

/**
 * JSON serializer for the protocol.
 */
val protocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
