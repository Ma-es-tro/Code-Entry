// CommissioningManager.kt
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
import android.content.IntentSender
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CommissioningManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val activity: ComponentActivity
) {

    val commissioningResult: MutableStateFlow<CommissioningResult?>
    val launcher: ActivityResultLauncher<IntentSenderRequest>
    private val app: HomeApp = HomeApp.getInstance()

    companion object {
        private const val TAG = "CommissioningManager"
    }

    init {
        // StateFlow to carry the result from the latest device commissioning:
        commissioningResult = MutableStateFlow(null)

        // Activity launcher to call commissioning callback and deliver the result:
        launcher = activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
            scope.launch { commissioningCallback(result) }
        }

        Log.d(TAG, "CommissioningManager initialized")
    }

    private suspend fun commissioningCallback(activityResult: ActivityResult) {
        try {
            // Try to convert ActivityResult into CommissioningResult:
            val result: CommissioningResult = CommissioningResult.fromIntentSenderResult(
                activityResult.resultCode, activityResult.data)

            // Store the CommissioningResult in the StateFlow:
            commissioningResult.emit(result)

            // Record the commissioning success status:
            Log.d(TAG, "Commissioning Success!")
            showDebug("Device commissioned successfully!")

            // Notify smart home manager of new device
            app.smartHomeManager.onDeviceCommissioned(result)

        } catch (exception: ApiException) {
            // Record the exception for commissioning failure:
            val errorMessage = "Commissioning failed: ${exception.status}"
            Log.e(TAG, errorMessage, exception)
            showError(errorMessage)
        }
    }

    fun requestCommissioning(onboardingPayload: String? = null) {
        // Use provided payload or retrieve from intent
        val payload = onboardingPayload ?: activity.intent?.getStringExtra(Matter.EXTRA_ONBOARDING_PAYLOAD)

        if (payload.isNullOrEmpty()) {
            val errorMessage = "No onboarding payload available for commissioning"
            Log.e(TAG, errorMessage)
            showError(errorMessage)
            return
        }

        scope.launch {
            try {
                // Create a commissioning request to store the device in Google's Fabric:
                val request = CommissioningRequest.builder()
                    .setStoreToGoogleFabric(true)
                    .setOnboardingPayload(payload)
                    .build()

                // Initialize client and sender for commissioning intent:
                val client: CommissioningClient = Matter.getCommissioningClient(context)
                val sender: IntentSender = client.commissionDevice(request).await()

                // Launch the commissioning intent on the launcher:
                launcher.launch(IntentSenderRequest.Builder(sender).build())

                Log.d(TAG, "Commissioning request launched")

            } catch (e: Exception) {
                val errorMessage = "Failed to start commissioning: ${e.message}"
                Log.e(TAG, errorMessage, e)
                showError(errorMessage)
            }
        }
    }

    fun isCommissioningInProgress(): Boolean {
        return commissioningResult.value == null
    }

    // Helper methods for consistent logging/UI feedback
    private fun showDebug(message: String) {
        Log.d(TAG, message)
        // You can add UI notification here if needed
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        // You can add UI notification here if needed
    }
}