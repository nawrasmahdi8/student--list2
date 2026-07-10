package com.example.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyConnectionsManager(private val context: Context) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.quickshare"

    fun startAdvertising(onEndpointFound: (String) -> Unit) {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            "DeviceName", SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        )
    }

    fun startDiscovery(onEndpointFound: (String) -> Unit) {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback(onEndpointFound), discoveryOptions
        )
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {}
        override fun onDisconnected(endpointId: String) {}
    }

    private fun endpointDiscoveryCallback(onEndpointFound: (String) -> Unit) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onEndpointFound(endpointId)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // معالجة البيانات المستلمة هنا
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // تحديث شريط التقدم هنا
        }
    }
}
