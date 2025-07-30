// VirtualSmartHomeTestActivity.kt - Test your smart home integration without physical devices
package com.example.aBmealApp

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class VirtualSmartHomeTestActivity : ComponentActivity() {

    private lateinit var homeApp: HomeApp
    private lateinit var statusText: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var commandInput: EditText
    private lateinit var testButtons: LinearLayout

    companion object {
        private const val TAG = "VirtualSmartHomeTest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize HomeApp
        homeApp = HomeApp.getInstance()
        homeApp.initializeWithActivity(this)

        setupTestUI()
        runInitialTests()
    }

    private fun setupTestUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Status display
        statusText = TextView(this).apply {
            text = "🏠 Smart Home Test Console\n\nInitializing..."
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF2C2C2C.toInt())
            setTextColor(0xFF00FF00.toInt())
        }
        layout.addView(statusText)

        // Device list
        val deviceTitle = TextView(this).apply {
            text = "📱 Virtual Devices:"
            textSize = 16f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(deviceTitle)

        deviceList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layout.addView(deviceList)

        // Voice command input
        val voiceTitle = TextView(this).apply {
            text = "🎤 Voice Command Test:"
            textSize = 16f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(voiceTitle)

        commandInput = EditText(this).apply {
            hint = "Try: 'suggest meals', 'start cooking', 'cooking status'"
            setPadding(16, 16, 16, 16)
        }
        layout.addView(commandInput)

        val voiceButton = Button(this).apply {
            text = "🎙️ Send Voice Command"
            setOnClickListener { testVoiceCommand() }
        }
        layout.addView(voiceButton)

        // Test buttons
        val testTitle = TextView(this).apply {
            text = "🧪 Smart Home Tests:"
            textSize = 16f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(testTitle)

        testButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Add test buttons
        addTestButton("🔍 Discover Devices", ::testDeviceDiscovery)
        addTestButton("🍳 Test Cooking Flow", ::testCookingFlow)
        addTestButton("📢 Test Announcements", ::testAnnouncements)
        addTestButton("🌐 Test Server Connection", ::testServerConnection)
        addTestButton("📊 Check Cooking Status", ::testCookingStatus)

        layout.addView(testButtons)

        val scrollView = ScrollView(this).apply {
            addView(layout)
        }
        setContentView(scrollView)
    }

    private fun addTestButton(text: String, action: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            setPadding(16, 16, 16, 16)
            setOnClickListener { action() }
        }
        testButtons.addView(button)
    }

    private fun runInitialTests() {
        updateStatus("🚀 Starting virtual smart home tests...\n")

        lifecycleScope.launch {
            // Test 1: Device Discovery
            updateStatus("🔍 Testing device discovery...")
            testDeviceDiscovery()

            // Test 2: Server Connection
            updateStatus("🌐 Testing server connection...")
            testServerConnection()

            updateStatus("✅ Initial tests complete! Use buttons above to test specific features.\n")
        }
    }

    private fun testDeviceDiscovery() {
        updateStatus("🔍 Discovering virtual devices...\n")

        homeApp.smartHomeManager.discoverDevices { devices ->
            updateStatus("📱 Found ${devices.size} virtual devices:\n")

            // Clear and update device list UI
            deviceList.removeAllViews()

            devices.forEach { device ->
                val deviceInfo = TextView(this).apply {
                    text = "${getDeviceIcon(device.type)} ${device.name} (${device.type})"
                    setPadding(16, 8, 0, 8)
                    setTextColor(if (device.isOnline) 0xFF00AA00.toInt() else 0xFFAA0000.toInt())
                }
                deviceList.addView(deviceInfo)

                updateStatus("  • ${device.name}: ${if (device.isOnline) "ONLINE" else "OFFLINE"}\n")
            }

            updateStatus("✅ Device discovery complete!\n")
        }
    }

    private fun testCookingFlow() {
        updateStatus("🍳 Testing cooking flow with mock recipe...\n")

        // Create a mock recipe for testing
        val mockRecipe = createMockRecipe()
        val autocookers = homeApp.smartHomeManager.getAutocookers()

        if (autocookers.isEmpty()) {
            updateStatus("❌ No autocookers found for cooking test\n")
            return
        }

        val targetDevice = autocookers.first()
        updateStatus("🎯 Using device: ${targetDevice.name}\n")

        // Test cooking conversion and start
        homeApp.autocookerManager.startCooking(mockRecipe, targetDevice.id) { success, message ->
            if (success) {
                updateStatus("✅ Cooking started successfully: $message\n")
                updateStatus("🔥 Virtual cooking in progress...\n")

                // Show active sessions
                val sessions = homeApp.autocookerManager.getActiveSessions()
                updateStatus("📊 Active sessions: ${sessions.size}\n")
                sessions.forEach { session ->
                    updateStatus("  • ${session.recipeName} - Status: ${session.status}\n")
                }
            } else {
                updateStatus("❌ Cooking failed: $message\n")
            }
        }
    }

    private fun testAnnouncements() {
        updateStatus("📢 Testing smart speaker announcements...\n")

        val speakers = homeApp.smartHomeManager.getSpeakers()
        if (speakers.isEmpty()) {
            updateStatus("❌ No speakers found for announcement test\n")
            return
        }

        updateStatus("🔊 Found ${speakers.size} virtual speakers\n")

        val testMessage = "Hello! This is a test announcement from your smart kitchen app!"

        speakers.forEach { speaker ->
            val command = SmartHomeCommand("SPEAK", mapOf("text" to testMessage))
            val success = homeApp.smartHomeManager.sendCommand(speaker.id, command)

            updateStatus("${if (success) "✅" else "❌"} Speaker ${speaker.name}: ${if (success) "Announced successfully" else "Failed"}\n")
        }
    }

    private fun testVoiceCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) {
            updateStatus("❌ Please enter a voice command\n")
            return
        }

        updateStatus("🎤 Processing voice command: '$command'\n")

        val result = homeApp.voiceCommandManager.processVoiceCommand(command)
        updateStatus("🤖 Recognized action: ${result.action}\n")
        updateStatus("💬 Response: ${result.response}\n")

        // Test voice response
        homeApp.voiceCommandManager.speakText(result.response)
        updateStatus("🔊 Sent response to speakers\n\n")

        commandInput.text.clear()
    }

    private fun testServerConnection() {
        updateStatus("🌐 Testing server connection...\n")

        homeApp.smartKitchenIntegration.testConnection { success, message ->
            updateStatus("${if (success) "✅" else "❌"} Server: $message\n")
        }
    }

    private fun testCookingStatus() {
        updateStatus("📊 Checking cooking status...\n")

        val sessions = homeApp.autocookerManager.getActiveSessions()
        if (sessions.isEmpty()) {
            updateStatus("ℹ️ No active cooking sessions\n")
            return
        }

        updateStatus("🔥 Active cooking sessions:\n")
        sessions.forEach { session ->
            updateStatus("  • Recipe: ${session.recipeName}\n")
            updateStatus("    Status: ${session.status}\n")
            updateStatus("    Device: ${session.deviceId}\n")
            updateStatus("    Method: ${session.instructions.method}\n")
            updateStatus("    Time: ${session.instructions.timeMinutes} min\n\n")
        }
    }

    private fun createMockRecipe(): RecipeDetails {
        return RecipeDetails(
            id = 12345,
            title = "Virtual Pressure Cooker Rice",
            readyInMinutes = 25,
            servings = 4,
            instructions = "Add rice and water to pressure cooker. Cook on high pressure for 18 minutes with natural release.",
            extendedIngredients = listOf(
                ExtendedIngredient(
                    id = 1,
                    name = "white rice",
                    amount = 2.0,
                    unit = "cups",
                    original = "2 cups white rice"
                ),
                ExtendedIngredient(
                    id = 2,
                    name = "water",
                    amount = 3.0,
                    unit = "cups",
                    original = "3 cups water"
                )
            ),
            image = "https://example.com/rice.jpg",
            summary = "Simple pressure cooker rice recipe for testing",
            pricePerServing = 5.99,
        )
    }

    private fun getDeviceIcon(type: String): String {
        return when (type) {
            "SPEAKER" -> "🔊"
            "DISPLAY" -> "📺"
            "AUTOCOOKER" -> "🍲"
            "OVEN" -> "🔥"
            "THERMOSTAT" -> "🌡️"
            else -> "📱"
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            val currentText = statusText.text.toString()
            statusText.text = currentText + message

            // Auto-scroll to bottom
            val scrollView = statusText.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }

        // Also log to console
        Log.d(TAG, message.replace("\n", " ").trim())
    }
}



