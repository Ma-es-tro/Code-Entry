// SmartHomeManagerExtensions.kt
package com.example.aBmealApp

import android.util.Log
import com.google.android.gms.home.matter.commissioning.CommissioningResult

// Extension functions for SmartHomeManager to handle manager callbacks
fun SmartHomeManager.onPermissionsGranted() {
    Log.d("SmartHomeManager", "Permissions granted - enabling smart home features")
    // Trigger device discovery now that permissions are available
    discoverDevices { devices ->
        Log.d("SmartHomeManager", "Auto-discovered ${devices.size} devices after permission grant")
    }
}

fun SmartHomeManager.onDeviceCommissioned(result: CommissioningResult) {
    Log.d("SmartHomeManager", "New device commissioned - refreshing device list")
    // Refresh the device list to include newly commissioned device
    discoverDevices { devices ->
        Log.d("SmartHomeManager", "Device list refreshed after commissioning: ${devices.size} devices")
    }
}