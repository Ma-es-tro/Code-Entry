// HomeApp.kt - Clean, Fixed & Fully Integrated Smart Home Version
package com.example.aBmealApp

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ===============================================
// MAIN APPLICATION CLASS
// ===============================================

class HomeApp : Application() {

    // Core components
    lateinit var appScope: CoroutineScope
        private set

    lateinit var sharedPreferences: SharedPreferences
        private set

    // Smart home managers
    lateinit var smartHomeManager: SmartHomeManager
        private set

    lateinit var voiceCommandManager: VoiceCommandManager
        private set

    lateinit var autocookerManager: AutocookerManager
        private set

    lateinit var smartKitchenIntegration: SmartKitchenIntegration
        private set

    // Google services
    var googleAccount: GoogleSignInAccount? = null
        private set

    companion object {
        private const val PREFS_NAME = "meal_app_prefs"
        private const val TAG = "HomeApp"

        @Volatile
        private var INSTANCE: HomeApp? = null

        fun getInstance(): HomeApp {
            return INSTANCE ?: throw IllegalStateException("HomeApp not initialized")
        }

        // Network configuration
        const val SERVER_BASE_URL = "http://192.168.0.24:3000/"
        const val WEBSOCKET_URL = "ws://192.168.0.24:8080"
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        Log.d(TAG, "🏠 Initializing HomeApp...")

        // Initialize coroutine scope
        appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Initialize shared preferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize all managers
        initializeManagers()

        // Check for existing Google account
        checkGoogleSignInStatus()

        Log.d(TAG, "✅ HomeApp initialized successfully")
    }

    private fun initializeManagers() {
        try {
            // Initialize smart home manager
            smartHomeManager = SmartHomeManager(this, appScope)

            // Initialize voice command manager
            voiceCommandManager = VoiceCommandManager(this, appScope)

            // Initialize autocooker manager
            autocookerManager = AutocookerManager(this, appScope)

            // Initialize smart kitchen API integration
            smartKitchenIntegration = SmartKitchenIntegration(this)

            Log.d(TAG, "✅ All managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize managers: ${e.message}")
        }
    }

    fun initializeWithActivity(activity: ComponentActivity) {
        Log.d(TAG, "🔗 Connecting activity to HomeApp...")

        try {
            // Initialize activity-dependent features
            smartHomeManager.setActivity(activity)
            voiceCommandManager.setActivity(activity)

            // Test server connection
            testServerConnection()

            Log.d(TAG, "✅ Activity integration complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Activity integration failed: ${e.message}")
        }
    }

    private fun testServerConnection() {
        smartKitchenIntegration.testConnection { success, message ->
            if (success) {
                Log.d(TAG, "🌐 Server connection test passed: $message")
            } else {
                Log.w(TAG, "⚠️ Server connection test failed: $message")
            }
        }
    }

    private fun checkGoogleSignInStatus() {
        googleAccount = GoogleSignIn.getLastSignedInAccount(this)
        googleAccount?.let { account ->
            Log.d(TAG, "📧 Found Google account: ${account.email}")
            onGoogleAccountAvailable(account)
        } ?: Log.d(TAG, "👤 No Google account found")
    }

    fun onGoogleAccountAvailable(account: GoogleSignInAccount) {
        this.googleAccount = account

        // Notify all managers
        smartHomeManager.setGoogleAccount(account)
        autocookerManager.setGoogleAccount(account)

        Log.d(TAG, "✅ Google account configured for all services")
    }

    fun clearGoogleAccount() {
        this.googleAccount = null
        smartHomeManager.clearGoogleAccount()
        autocookerManager.clearGoogleAccount()
        Log.d(TAG, "🧹 Google account cleared from all services")
    }
}

// ===============================================
// SMART HOME MANAGER
// ===============================================

class SmartHomeManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var googleAccount: GoogleSignInAccount? = null
    private var activity: ComponentActivity? = null
    private val connectedDevices = mutableListOf<SmartDevice>()

    companion object {
        private const val TAG = "SmartHomeManager"
    }

    init {
        initializeDefaultDevices()
    }

    private fun initializeDefaultDevices() {
        // Add some default/simulated devices
        connectedDevices.addAll(listOf(
            SmartDevice("speaker_01", "Kitchen Speaker", "SPEAKER", true,
                mapOf("brand" to "Google", "model" to "Nest Mini")),
            SmartDevice("display_01", "Kitchen Display", "DISPLAY", true,
                mapOf("brand" to "Google", "model" to "Nest Hub")),
            SmartDevice("autocooker_01", "Smart Pressure Cooker", "AUTOCOOKER", true,
                mapOf("brand" to "Instant Pot", "model" to "Smart WiFi")),
            SmartDevice("oven_01", "Smart Oven", "OVEN", false,
                mapOf("brand" to "Samsung", "model" to "Smart Range"))
        ))

        Log.d(TAG, "📱 Initialized ${connectedDevices.size} default devices")
    }

    fun setActivity(activity: ComponentActivity) {
        this.activity = activity
        Log.d(TAG, "🔗 Activity set for smart home integration")
    }

    fun setGoogleAccount(account: GoogleSignInAccount) {
        this.googleAccount = account
        Log.d(TAG, "📧 Google account set: ${account.email}")

        // Refresh device discovery with Google Home integration
        discoverDevices { devices ->
            Log.d(TAG, "🔍 Refreshed devices after Google account setup: ${devices.size} found")
        }
    }

    fun clearGoogleAccount() {
        this.googleAccount = null
        // Keep simulated devices but clear Google Home specific ones
        connectedDevices.removeAll { it.capabilities["source"] == "google_home" }
        Log.d(TAG, "🧹 Cleared Google Home devices")
    }

    fun discoverDevices(callback: (List<SmartDevice>) -> Unit) {
        Log.d(TAG, "🔍 Starting device discovery...")

        scope.launch {
            try {
                // Simulate device discovery process
                delay(1000) // Simulate discovery time

                // If Google account is available, add more devices
                if (googleAccount != null) {
                    val googleDevices = listOf(
                        SmartDevice("gh_speaker_01", "Living Room Speaker", "SPEAKER", true,
                            mapOf("brand" to "Google", "model" to "Nest Audio", "source" to "google_home")),
                        SmartDevice("gh_thermostat_01", "Smart Thermostat", "THERMOSTAT", true,
                            mapOf("brand" to "Nest", "model" to "Learning Thermostat", "source" to "google_home"))
                    )

                    // Add Google Home devices if not already present
                    googleDevices.forEach { newDevice ->
                        if (connectedDevices.none { it.id == newDevice.id }) {
                            connectedDevices.add(newDevice)
                        }
                    }
                }

                Log.d(TAG, "✅ Discovery complete: ${connectedDevices.size} devices found")

                // Callback on main thread
                withContext(Dispatchers.Main) {
                    callback(connectedDevices.toList())
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Device discovery failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(connectedDevices.toList()) // Return cached devices
                }
            }
        }
    }

    // Device type getters
    fun getAutocookers(): List<SmartDevice> =
        connectedDevices.filter { it.type == "AUTOCOOKER" && it.isOnline }

    fun getSpeakers(): List<SmartDevice> =
        connectedDevices.filter { it.type == "SPEAKER" && it.isOnline }

    fun getDisplays(): List<SmartDevice> =
        connectedDevices.filter { it.type == "DISPLAY" && it.isOnline }

    fun getAllDevices(): List<SmartDevice> = connectedDevices.toList()

    fun sendCommand(deviceId: String, command: SmartHomeCommand): Boolean {
        val device = connectedDevices.find { it.id == deviceId }

        if (device == null) {
            Log.e(TAG, "❌ Device not found: $deviceId")
            return false
        }

        if (!device.isOnline) {
            Log.w(TAG, "⚠️ Device offline: ${device.name}")
            return false
        }

        Log.d(TAG, "📤 Sending command '${command.action}' to ${device.name}")

        // Handle different command types
        when (command.action) {
            "SPEAK" -> {
                val text = command.parameters["text"] as? String ?: "Hello"
                Log.d(TAG, "🔊 Speaking on ${device.name}: $text")
                return true
            }
            "DISPLAY_RECIPE" -> {
                val recipeName = command.parameters["recipeName"] as? String ?: "Recipe"
                Log.d(TAG, "📺 Displaying $recipeName on ${device.name}")
                return true
            }
            "START_COOKING" -> {
                Log.d(TAG, "🍳 Starting cooking on ${device.name}")
                return true
            }
            "STOP_COOKING" -> {
                Log.d(TAG, "⏹️ Stopping cooking on ${device.name}")
                return true
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown command: ${command.action}")
                return false
            }
        }
    }
}

// ===============================================
// AUTOCOOKER MANAGER
// ===============================================

class AutocookerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var googleAccount: GoogleSignInAccount? = null
    private val activeSessions = mutableMapOf<String, CookingSession>()

    companion object {
        private const val TAG = "AutocookerManager"
    }

    fun setGoogleAccount(account: GoogleSignInAccount) {
        this.googleAccount = account
        Log.d(TAG, "📧 Google account set for autocooker")
    }

    fun clearGoogleAccount() {
        this.googleAccount = null
        Log.d(TAG, "🧹 Google account cleared from autocooker")
    }

    fun startCooking(recipe: RecipeDetails, deviceId: String, callback: (Boolean, String) -> Unit) {
        Log.d(TAG, "🍳 Starting cooking: ${recipe.title} on device: $deviceId")

        // Check if device is available
        val homeApp = HomeApp.getInstance()
        val autocookers = homeApp.smartHomeManager.getAutocookers()
        val targetDevice = autocookers.find { it.id == deviceId }

        if (targetDevice == null) {
            callback(false, "Autocooker not found or offline")
            return
        }

        // Convert recipe to cooking instructions
        val cookingInstructions = convertRecipeToCookingInstructions(recipe)

        if (cookingInstructions == null) {
            callback(false, "Recipe not suitable for autocooker cooking")
            return
        }

        // Create cooking session
        val sessionId = "session_${System.currentTimeMillis()}"
        val session = CookingSession(
            id = sessionId,
            recipeId = recipe.id.toString(),
            recipeName = recipe.title,
            deviceId = deviceId,
            instructions = cookingInstructions,
            startTime = System.currentTimeMillis(),
            status = "STARTING"
        )

        activeSessions[sessionId] = session

        // Start cooking via Smart Kitchen API
        homeApp.smartKitchenIntegration.startCooking(recipe, deviceId) { success, message ->
            if (success) {
                session.status = "COOKING"
                announceStartCooking(recipe, cookingInstructions)
                callback(true, "✅ Started cooking ${recipe.title}")
            } else {
                activeSessions.remove(sessionId)
                callback(false, "❌ Failed to start: $message")
            }
        }
    }


    private fun convertRecipeToCookingInstructions(recipe: RecipeDetails): CookingInstructions? {
        val instructions = recipe.instructions.lowercase()
        val ingredients = recipe.extendedIngredients

        // Check if recipe is suitable for autocooker
        val isSuitable = when {
            instructions.contains("pressure cook") || instructions.contains("instant pot") -> true
            instructions.contains("rice") && ingredients.any { it.name.contains("rice") } -> true
            instructions.contains("stew") || instructions.contains("braise") -> true
            instructions.contains("soup") && recipe.readyInMinutes > 15 -> true
            ingredients.any { it.name.lowercase().contains("meat") } && recipe.readyInMinutes > 20 -> true
            else -> false
        }

        if (!isSuitable) {
            Log.w(TAG, "⚠️ Recipe ${recipe.title} not suitable for autocooker")
            return null
        }

        // Determine cooking parameters
        val method = when {
            instructions.contains("rice") -> "RICE"
            instructions.contains("soup") -> "SOUP"
            instructions.contains("stew") -> "STEW"
            instructions.contains("meat") -> "MEAT"
            else -> "PRESSURE_COOK"
        }

        val cookingTime = calculateCookingTime(recipe, method)
        val pressure = determinePressure(method)
        val temperature = determineTemperature(method)

        return CookingInstructions(
            method = method,
            timeMinutes = cookingTime,
            pressure = pressure,
            temperature = temperature,
            autoRelease = shouldAutoRelease(method),
            ingredients = ingredients.map { "${it.amount} ${it.unit} ${it.name}" }
        )
    }

    private fun calculateCookingTime(recipe: RecipeDetails, method: String): Int {
        return when (method) {
            "RICE" -> 18
            "SOUP" -> minOf(recipe.readyInMinutes / 2, 25)
            "STEW" -> minOf(recipe.readyInMinutes / 3, 35)
            "MEAT" -> minOf(recipe.readyInMinutes / 2, 45)
            else -> minOf(recipe.readyInMinutes / 2, 30)
        }
    }

    private fun determinePressure(method: String): String {
        return when (method) {
            "RICE" -> "LOW"
            "SOUP", "STEW", "MEAT" -> "HIGH"
            else -> "MEDIUM"
        }
    }

    private fun determineTemperature(method: String): Int {
        return when (method) {
            "RICE" -> 212
            "SOUP" -> 240
            "STEW", "MEAT" -> 250
            else -> 240
        }
    }

    private fun shouldAutoRelease(method: String): Boolean {
        return when (method) {
            "RICE" -> true
            "SOUP", "STEW", "MEAT" -> false
            else -> true
        }
    }

    private fun announceStartCooking(recipe: RecipeDetails, instructions: CookingInstructions) {
        val homeApp = HomeApp.getInstance()
        val speakers = homeApp.smartHomeManager.getSpeakers()

        val announcement = "Starting to cook ${recipe.title} using ${instructions.method.lowercase().replace("_", " ")} " +
                "mode for ${instructions.timeMinutes} minutes. I'll let you know when it's ready!"

        speakers.forEach { speaker ->
            val command = SmartHomeCommand("SPEAK", mapOf("text" to announcement))
            homeApp.smartHomeManager.sendCommand(speaker.id, command)
        }

        Log.d(TAG, "📢 Announced cooking start: $announcement")
    }

    fun getActiveSessions(): List<CookingSession> = activeSessions.values.toList()

    fun stopCooking(sessionId: String): Boolean {
        val session = activeSessions[sessionId] ?: return false

        activeSessions.remove(sessionId)
        session.status = "STOPPED"

        Log.d(TAG, "⏹️ Stopped cooking session: ${session.recipeName}")
        return true
    }
}

// ===============================================
// VOICE COMMAND MANAGER
// ===============================================

class VoiceCommandManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var activity: ComponentActivity? = null
    private var isListening = false

    companion object {
        private const val TAG = "VoiceCommandManager"
    }

    fun setActivity(activity: ComponentActivity) {
        this.activity = activity
        Log.d(TAG, "🔗 Activity set for voice commands")
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        Log.d(TAG, "🎤 Started listening for voice commands")
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        Log.d(TAG, "🔇 Stopped listening for voice commands")
    }

    fun processVoiceCommand(command: String): VoiceCommandResult {
        Log.d(TAG, "🎙️ Processing: $command")

        val lowerCommand = command.lowercase()

        return when {
            lowerCommand.contains("suggest") && lowerCommand.contains("meal") ->
                VoiceCommandResult("GET_MEAL_SUGGESTIONS", "Getting weather-based meal suggestions...")

            lowerCommand.contains("start cooking") || lowerCommand.contains("cook this") ->
                VoiceCommandResult("START_COOKING", "Starting to cook the selected recipe...")

            lowerCommand.contains("stop cooking") ->
                VoiceCommandResult("STOP_COOKING", "Stopping the cooking process...")

            lowerCommand.contains("cooking status") || lowerCommand.contains("how long") ->
                VoiceCommandResult("COOKING_STATUS", "Checking cooking status...")

            lowerCommand.contains("shopping list") ->
                VoiceCommandResult("CREATE_SHOPPING_LIST", "Creating shopping list...")

            lowerCommand.contains("timer") ->
                VoiceCommandResult("START_TIMER", "Starting cooking timer...")

            lowerCommand.contains("recipe") ->
                VoiceCommandResult("READ_RECIPE", "Reading recipe instructions...")

            else ->
                VoiceCommandResult("UNKNOWN", "I didn't understand. Try: 'suggest meals', 'start cooking', or 'create shopping list'")
        }
    }

    fun speakText(text: String) {
        Log.d(TAG, "🔊 Speaking: $text")

        // Use connected speakers to announce
        val homeApp = HomeApp.getInstance()
        val speakers = homeApp.smartHomeManager.getSpeakers()

        speakers.forEach { speaker ->
            val command = SmartHomeCommand("SPEAK", mapOf("text" to text))
            homeApp.smartHomeManager.sendCommand(speaker.id, command)
        }
    }
}

// ===============================================
// SMART KITCHEN API INTEGRATION
// ===============================================

class SmartKitchenIntegration(private val homeApp: HomeApp) {

    private val api: SmartKitchenApi

    companion object {
        private const val TAG = "SmartKitchenAPI"
    }

    init {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(HomeApp.SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(SmartKitchenApi::class.java)

        Log.d(TAG, "🔧 Smart Kitchen API initialized")
    }

    fun testConnection(callback: (Boolean, String) -> Unit) {
        api.getHealth().enqueue(object : Callback<HealthResponse> {
            override fun onResponse(call: Call<HealthResponse>, response: Response<HealthResponse>) {
                if (response.isSuccessful) {
                    val health = response.body()
                    callback(true, "Server healthy: ${health?.message ?: "OK"}")
                } else {
                    callback(false, "Server responded with error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<HealthResponse>, t: Throwable) {
                callback(false, "Connection failed: ${t.message}")
            }
        })
    }

    fun startCooking(recipe: RecipeDetails, deviceId: String, callback: (Boolean, String) -> Unit) {
        val cookingRequest = CookingRequest(
            recipeName = recipe.title,
            instructions = recipe.instructions,
            estimatedTime = recipe.readyInMinutes,
            ingredients = recipe.extendedIngredients.map { it.original },
            deviceId = deviceId
        )

        api.startCooking(cookingRequest).enqueue(object : Callback<CookingResponse> {
            override fun onResponse(call: Call<CookingResponse>, response: Response<CookingResponse>) {
                if (response.isSuccessful) {
                    val cookingResponse = response.body()
                    if (cookingResponse?.success == true) {
                        callback(true, cookingResponse.message)
                    } else {
                        callback(false, cookingResponse?.message ?: "Unknown error")
                    }
                } else {
                    callback(false, "Server error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<CookingResponse>, t: Throwable) {
                callback(false, "Network error: ${t.message}")
            }
        })
    }

    fun getCookingStatus(sessionId: String, callback: SmartCookingStatusCallback) {
        api.getCookingStatus(sessionId).enqueue(object : Callback<CookingStatusResponse> {
            override fun onResponse(call: Call<CookingStatusResponse>, response: Response<CookingStatusResponse>) {
                if (response.isSuccessful) {
                    val statusResponse = response.body()
                    if (statusResponse?.success == true) {
                        callback.onStatusReceived(statusResponse.session)
                    } else {
                        callback.onError(statusResponse?.message ?: "Unknown error")
                    }
                } else {
                    callback.onError("Server error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<CookingStatusResponse>, t: Throwable) {
                callback.onError("Network error: ${t.message}")
            }
        })
    }

    fun discoverDevices(callback: (List<SmartHomeDevice>) -> Unit) {
        api.discoverDevices().enqueue(object : Callback<DeviceDiscoveryResponse> {
            override fun onResponse(call: Call<DeviceDiscoveryResponse>, response: Response<DeviceDiscoveryResponse>) {
                if (response.isSuccessful) {
                    val discoveryResponse = response.body()
                    callback(discoveryResponse?.appliances ?: emptyList())
                } else {
                    Log.e(TAG, "Device discovery failed: ${response.code()}")
                    callback(emptyList())
                }
            }

            override fun onFailure(call: Call<DeviceDiscoveryResponse>, t: Throwable) {
                Log.e(TAG, "Device discovery network error: ${t.message}")
                callback(emptyList())
            }
        })
    }
}

// ===============================================
// API INTERFACE
// ===============================================

interface SmartKitchenApi {
    @GET("api/health")
    fun getHealth(): Call<HealthResponse>

    @POST("api/test/simulate-cooking")
    fun startCooking(@Body request: CookingRequest): Call<CookingResponse>

    @GET("api/test/cooking-status/{sessionId}")
    fun getCookingStatus(@Path("sessionId") sessionId: String): Call<CookingStatusResponse>

    @GET("api/appliances/discover")
    fun discoverDevices(): Call<DeviceDiscoveryResponse>

    @POST("api/voice/command")
    fun sendVoiceCommand(@Body command: VoiceCommandRequest): Call<VoiceCommandResponse>
}

// ===============================================
// DATA CLASSES
// ===============================================

// Smart Home Data Classes
data class SmartDevice(
    val id: String,
    val name: String,
    val type: String,
    val isOnline: Boolean,
    val capabilities: Map<String, Any> = emptyMap()
)

data class SmartHomeCommand(
    val action: String,
    val parameters: Map<String, Any> = emptyMap()
)

data class VoiceCommandResult(
    val action: String,
    val response: String
)

// Cooking Data Classes
data class CookingInstructions(
    val method: String,
    val timeMinutes: Int,
    val pressure: String,
    val temperature: Int,
    val autoRelease: Boolean,
    val ingredients: List<String>
)

data class CookingSession(
    val id: String,
    val recipeId: String,
    val recipeName: String,
    val deviceId: String,
    val instructions: CookingInstructions,
    val startTime: Long,
    var status: String = "STARTING"
)

// API Request/Response Classes
data class CookingRequest(
    val recipeName: String,
    val instructions: String,
    val estimatedTime: Int,
    val ingredients: List<String>,
    val deviceId: String
)

data class CookingResponse(
    val success: Boolean,
    val message: String,
    val sessionId: String? = null
)

data class CookingStatusResponse(
    val success: Boolean,
    val message: String? = null,
    val session: SmartCookingSessionData
)

data class SmartCookingSessionData(
    val id: String,
    val recipeName: String,
    val status: String,
    val currentStep: Int,
    val totalSteps: Int,
    val timeRemaining: Int,
    val currentInstruction: String?
)

data class HealthResponse(
    val success: Boolean,
    val message: String,
    val version: String? = null,
    val uptime: Double? = null
)

data class DeviceDiscoveryResponse(
    val success: Boolean,
    val message: String,
    val appliances: List<SmartHomeDevice>
)

data class SmartHomeDevice(
    val id: String,
    val name: String,
    val type: String,
    val brand: String?,
    val status: String,
    val isConnected: Boolean
)

data class VoiceCommandRequest(
    val command: String,
    val parameters: Map<String, Any> = emptyMap()
)

data class VoiceCommandResponse(
    val success: Boolean,
    val message: String,
    val action: String? = null
)

// ===============================================
// CALLBACK INTERFACES
// ===============================================

interface SmartCookingStatusCallback {
    fun onStatusReceived(session: SmartCookingSessionData)
    fun onError(error: String)
}