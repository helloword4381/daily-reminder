package com.dailyreminder.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP 广播发现服务
 * 每 10 秒发送一次发现请求，监听 Windows 端的响应
 */
class DiscoveryService(private val context: Context) {

    private val tag = "DiscoveryService"
    private var socket: DatagramSocket? = null
    private var running = false

    fun interface OnDiscoveryListener {
        fun onDiscovered(info: SyncProtocol.DiscoveryInfo)
    }

    /**
     * 启动发现循环
     */
    fun start(listener: OnDiscoveryListener) {
        if (running) return
        running = true

        Thread {
            try {
                socket = DatagramSocket(SyncProtocol.DISCOVERY_PORT).apply {
                    broadcast = true
                    soTimeout = SyncProtocol.TIMEOUT_MS.toInt()
                }

                val buffer = ByteArray(1024)

                while (running) {
                    try {
                        // 发送广播发现请求
                        val broadcastIp = getBroadcastAddress()
                        val requestData = SyncProtocol.DISCOVERY_REQUEST.toByteArray()
                        val packet = DatagramPacket(
                            requestData, requestData.size,
                            broadcastIp, SyncProtocol.DISCOVERY_PORT
                        )
                        socket?.send(packet)

                        // 监听响应
                        while (running) {
                            val receiveBuffer = ByteArray(1024)
                            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                            try {
                                socket?.receive(receivePacket)
                                val response = String(
                                    receivePacket.data, 0, receivePacket.length,
                                    Charsets.UTF_8
                                )
                                val info = SyncProtocol.parseDiscoveryResponse(response)
                                if (info != null) {
                                    Log.i(tag, "Discovered: ${info.hostname} @ ${info.ip}")
                                    listener.onDiscovered(info)
                                }
                            } catch (e: java.net.SocketTimeoutException) {
                                break // 超时，重新发送发现请求
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Discovery error", e)
                    }

                    // 等待下一个周期
                    if (running) {
                        Thread.sleep(SyncProtocol.HEARTBEAT_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Fatal discovery error", e)
            } finally {
                socket?.close()
                socket = null
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
    }

    private fun getBroadcastAddress(): InetAddress {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wifi.dhcpInfo
        val broadcast = (dhcp.ipAddress and dhcp.netmask) or (dhcp.netmask.inv())
        val parts = ByteArray(4)
        for (i in 0 until 4) {
            parts[i] = (broadcast shr (i * 8) and 0xFF).toByte()
        }
        return InetAddress.getByAddress(parts)
    }
}
