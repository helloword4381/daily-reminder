package com.dailyreminder.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 同步协议常量与编解码，与 common/sync_protocol.py 保持一致
 */
object SyncProtocol {
    const val DISCOVERY_PORT = 8898
    const val SYNC_PORT = 8899
    const val DISCOVERY_REQUEST = "REMINDER_DISCOVERY"
    const val DISCOVERY_RESPONSE_PREFIX = "REMINDER_RESPONSE"
    const val HEARTBEAT_INTERVAL_MS = 10_000L
    const val TIMEOUT_MS = 30_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class SyncMessage(
        val type: String,           // "sync" or "sync_response"
        val tasks: List<SyncTask> = emptyList(),
        val syncToken: String = ""
    )

    @Serializable
    data class SyncTask(
        val id: String = "",
        val content: String = "",
        val done: Boolean = false,
        val createdAt: String = "",
        val updatedAt: String = ""
    )

    fun encode(msg: SyncMessage): ByteArray {
        val str = json.encodeToString(msg)
        val data = str.toByteArray(Charsets.UTF_8)
        // 4-byte length prefix + data (network byte order)
        val len = data.size
        return byteArrayOf(
            (len shr 24).toByte(),
            (len shr 16).toByte(),
            (len shr 8).toByte(),
            len.toByte()
        ) + data
    }

    fun decode(data: ByteArray): SyncMessage {
        val str = String(data, Charsets.UTF_8)
        return json.decodeFromString(str)
    }

    fun parseDiscoveryResponse(response: String): DiscoveryInfo? {
        if (!response.startsWith(DISCOVERY_RESPONSE_PREFIX)) return null
        val parts = response.removePrefix(DISCOVERY_RESPONSE_PREFIX).trim().split("|")
        if (parts.size < 2) return null
        return DiscoveryInfo(hostname = parts[0], ip = parts[1])
    }

    data class DiscoveryInfo(val hostname: String, val ip: String)
}
