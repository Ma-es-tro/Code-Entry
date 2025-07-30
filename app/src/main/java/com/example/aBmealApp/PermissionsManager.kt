// PermissionsManager.kt
/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.aBmealApp

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.home.HomeClient
import com.google.home.HomeException
import com.google.home.PermissionsResult
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PermissionsManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val activity: ComponentActivity,
    private val client: HomeClient
) {

    var isSignedIn: MutableStateFlow<Boolean>
    private val app: HomeApp = HomeApp.getInstance()

    companion object {
        private const val TAG = "PermissionsManager"
    }

    init {
        // StateFlow to carry the result for successful sign-in:
        isSignedIn = MutableStateFlow(false)
        // Register permission caller callback on HomeClient:
        client.registerActivityResultCallerForPermissions(activity)
        // Check the current permission state:
        checkPermissions()

        Log.d(TAG, "PermissionsManager initialized")
    }

    private fun checkPermissions() {
        scope.launch {
            try {
                // Check and wait until getting the first permission state after initialization:
                val permissionsState: PermissionsState = client.hasPermissions().first { state ->
                    state != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
                }
                // Adjust the sign-in status according to permission state:
                isSignedIn.emit(permissionsState == PermissionsState.GRANTED)
                // Report the permission state:
                reportPermissionState(permissionsState)

                // Notify smart home manager of permission status
                if (permissionsState == PermissionsState.GRANTED) {
                    app.smartHomeManager.onPermissionsGranted()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking permissions", e)
            }
        }
    }

    /**
     * Public wrapper for checkPermissions().
     * This function should be used by external components (e.g., UI layers) to refresh
     * the permission state. Directly calling checkPermissions() is discouraged to maintain
     * encapsulation and allow future flexibility.
     */
    fun refreshPermissions() {
        checkPermissions()
    }

    fun requestPermissions() {
        scope.launch {
            try {
                // Request permissions from the Permissions API and record the result:
                val result: PermissionsResult = client.requestPermissions(forceLaunch = true)
                // Adjust the sign-in status according to permission result:
                if (result.status == PermissionsResultStatus.SUCCESS) {
                    isSignedIn.emit(true)
                    // Notify smart home manager that permissions are now available
                    app.smartHomeManager.onPermissionsGranted()
                }
                // Report the permission result:
                reportPermissionResult(result)
            }
            catch (e: HomeException) {
                Log.e(TAG, "Error requesting permissions: ${e.message}", e)
                showError("Permission request failed: ${e.message}")
            }
        }
    }

    private fun reportPermissionState(permissionState: PermissionsState) {
        val message = "Permissions State: ${permissionState.name}"
        Log.d(TAG, message)

        // Report the permission state:
        when (permissionState) {
            PermissionsState.GRANTED -> {
                showDebug(message)
            }
            PermissionsState.NOT_GRANTED -> {
                showWarning(message)
            }
            PermissionsState.PERMISSIONS_STATE_UNAVAILABLE -> {
                showWarning(message)
            }
            PermissionsState.PERMISSIONS_STATE_UNINITIALIZED -> {
                showError(message)
            }
            else -> showError(message)
        }
    }

    private fun reportPermissionResult(permissionResult: PermissionsResult) {
        var message = "Permissions Result: ${permissionResult.status.name}"
        // Include any error messages in the permission result:
        if (permissionResult.errorMessage != null) {
            message += " | ${permissionResult.errorMessage}"
        }

        Log.d(TAG, message)

        // Report the permission result:
        when (permissionResult.status) {
            PermissionsResultStatus.SUCCESS -> {
                showDebug(message)
            }
            PermissionsResultStatus.CANCELLED -> {
                showWarning(message)
            }
            PermissionsResultStatus.ERROR -> {
                showError(message)
            }
            else -> showError(message)
        }
    }

    // Helper methods for consistent logging/UI feedback
    private fun showDebug(message: String) {
        Log.d(TAG, message)
        // You can add UI notification here if needed
    }

    private fun showWarning(message: String) {
        Log.w(TAG, message)
        // You can add UI notification here if needed
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        // You can add UI notification here if needed
    }
}

