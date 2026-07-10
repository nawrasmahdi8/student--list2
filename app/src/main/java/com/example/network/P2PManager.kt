package com.example.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.Payload
import java.io.Serializable

class P2PManager(private val context: Context) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.fahrasat.P2P_SERVICE"

    fun startAdvertising(onConnectionInitiated: (String, ConnectionInfo) -> Unit) {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startAdvertising("DeviceName", SERVICE_ID, connectionLifecycleCallback(onConnectionInitiated), options)
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    fun startDiscovery(onEndpointFound: (String, DiscoveredEndpointInfo) -> Unit) {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback(onEndpointFound), options)
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }
    
    fun sendData(endpointId: String, data: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(data))
    }

    private fun connectionLifecycleCallback(onConnectionInitiated: (String, ConnectionInfo) -> Unit) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onConnectionInitiated(endpointId, info)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {}
        override fun onDisconnected(endpointId: String) {}
    }

    private fun endpointDiscoveryCallback(onEndpointFound: (String, DiscoveredEndpointInfo) -> Unit) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onEndpointFound(endpointId, info)
        }
        override fun onEndpointLost(endpointId: String) {}
    }
}
