package com.example.aBmealApp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Headers
import androidx.appcompat.app.AlertDialog
import android.text.Html
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Button
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    // Constants
    companion object {
        const val WEATHER_API_KEY = "" //API key deactivated
        const val BASE_WEATHER_URL = "https://api.openweathermap.org/data/2.5/"
        const val SPOONACULAR_API_KEY = "" //API key deactivated
        const val BASE_SPOONACULAR_URL = "https://api.spoonacular.com/"
        const val GEMINI_API_KEY = "" //API key deactivated
        const val BASE_GEMINI_URL = "https://generativelanguage.googleapis.com/"
        const val SMART_KITCHEN_BASE_URL = "http://192.168.56.1/"
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001

        fun showError(context: Context, message: String) {
            Log.e("MainActivity", message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        fun showWarning(context: Context, message: String) {
            Log.w("MainActivity", message)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        fun showDebug(context: Context, message: String) {
            Log.d("MainActivity", message)
        }
    }

    // Properties
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var webSocketClient: WebSocketClient? = null

    private val weatherService = WeatherService()
    private val spoonacularService = SpoonacularService()
    private val geminiService = GeminiService()

    private lateinit var weatherIcon: TextView
    private lateinit var weatherText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var countryInput: EditText
    private lateinit var ingredientsInput: EditText
    private lateinit var othersInput: EditText
    private lateinit var locationTextView: TextView

    // Activity Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSimpleLayout()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            checkWeatherAndRecommendMeals()
        }

        initializeHomeApp()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient?.close()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkWeatherAndRecommendMeals()
            } else {
                showWarning(this, "Location permission is required for weather recommendations.")
                locationTextView.text = "📍 Location: Permission denied"
                val testCondition = WeatherCondition("SUNNY", 20.0, "Test weather condition")
                showMealRecommendations(testCondition)
            }
        }
    }

    // UI Creation
    private fun createSimpleLayout() {
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isSmoothScrollingEnabled = true
            setPadding(0, 0, 0, 60)
        }

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 60, 40, 60)
            setBackgroundResource(R.drawable.gradient_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val testButton = Button(this).apply {
            text = "🏠 Test Smart Home Features"
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                val intent = Intent(this@MainActivity, VirtualSmartHomeTestActivity::class.java)
                startActivity(intent)
            }
        }

        val headerLayout = createHeaderLayout()
        val subtitleText = createSubtitleText()
        val weatherCard = createWeatherCard()
        val preferencesCard = createPreferencesCard()
        val startButton = createStartButton()
        val locationTextView = createLocationTextView()
        val footerText = createFooterText()

        mainContainer.addView(headerLayout)
        mainContainer.addView(subtitleText)
        mainContainer.addView(weatherCard)
        mainContainer.addView(startButton)
        mainContainer.addView(preferencesCard)
        mainContainer.addView(locationTextView)
        mainContainer.addView(footerText)
        mainContainer.addView(testButton)

        scrollView.addView(mainContainer)
        setContentView(scrollView)
    }

    private fun createHeaderLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)

            val iconView = TextView(this@MainActivity).apply {
                text = "🍽️"
                textSize = 32f
                setPadding(0, 0, 20, 0)
            }

            val titleText = TextView(this@MainActivity).apply {
                text = "AI Weather Chef"
                textSize = 32f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(Color.parseColor("#2C3E50"))
                setShadowLayer(2f, 0f, 2f, Color.parseColor("#BDC3C7"))
                gravity = Gravity.CENTER
            }

            addView(iconView)
            addView(titleText)
        }
    }

    private fun createSubtitleText(): TextView {
        return TextView(this).apply {
            text = "Smart meal suggestions based on your weather"
            textSize = 18f
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            setTextColor(Color.parseColor("#7F8C8D"))
            gravity = Gravity.CENTER
            setPadding(20, 0, 20, 48)
        }
    }

    private fun createWeatherCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundResource(R.drawable.card_background)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 32)
            layoutParams = params

            val headerText = TextView(this@MainActivity).apply {
                text = "🌍 Current Weather"
                textSize = 16f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(Color.parseColor("#7F8C8D"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
            }

            weatherIcon = TextView(this@MainActivity).apply {
                text = "🌤️"
                textSize = 56f
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 16)
            }

            temperatureText = TextView(this@MainActivity).apply {
                text = "🌡️ Loading temperature..."
                textSize = 20f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(Color.parseColor("#2C3E50"))
                gravity = Gravity.CENTER
                setPadding(16, 0, 16, 12)
            }

            weatherText = TextView(this@MainActivity).apply {
                text = "Getting weather information..."
                textSize = 16f
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
                setTextColor(Color.parseColor("#34495E"))
                gravity = Gravity.CENTER
                setPadding(16, 0, 16, 8)
            }

            descriptionText = TextView(this@MainActivity).apply {
                text = "🤖 Preparing personalized recommendations..."
                textSize = 14f
                setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC))
                setTextColor(Color.parseColor("#95A5A6"))
                gravity = Gravity.CENTER
                setPadding(16, 0, 16, 0)
            }

            addView(headerText)
            addView(weatherIcon)
            addView(temperatureText)
            addView(weatherText)
            addView(descriptionText)
        }
    }

    private fun createPreferencesCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundResource(R.drawable.card_background)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params

            val preferencesHeader = TextView(this@MainActivity).apply {
                text = "🍽️ Customize Your Preferences (Leave empty if you want weather based meals)"
                textSize = 18f
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                setTextColor(Color.parseColor("#2C3E50"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 20)
            }

            val countryLabel = createLabel("🌍 Preferred Cuisine/Country")
            countryInput = createEditText("e.g., Italian, Mexican, Asian...")

            val ingredientsLabel = createLabel("🥗 Preferred Ingredients")
            ingredientsInput = createEditText("e.g., chicken, vegetables, rice...")

            val othersLabel = createLabel("⚡ Other Preferences")
            othersInput = createEditText("e.g., vegetarian, spicy, low-carb...")

            addView(preferencesHeader)
            addView(countryLabel)
            addView(countryInput)
            addView(ingredientsLabel)
            addView(ingredientsInput)
            addView(othersLabel)
            addView(othersInput)
        }
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(Color.parseColor("#34495E"))
            setPadding(8, 0, 0, 8)
        }
    }

    private fun createEditText(hint: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setPadding(16, 12, 16, 12)
            setBackgroundResource(android.R.drawable.edit_text)
            setTextColor(Color.parseColor("#2C3E50"))
            setHintTextColor(Color.parseColor("#95A5A6"))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isLongClickable = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextIsSelectable(true)

            val inputParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputParams.setMargins(0, 0, 0, 16)
            layoutParams = inputParams
        }
    }

    private fun createStartButton(): Button {
        return Button(this).apply {
            text = "🧠 Get AI Recommendations"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setPadding(48, 32, 48, 32)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.button_background)
            elevation = 12f

            setOnClickListener {
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100)
                }

                val hasPreferences = countryInput.text.toString().trim().isNotEmpty() ||
                        ingredientsInput.text.toString().trim().isNotEmpty() ||
                        othersInput.text.toString().trim().isNotEmpty()

                if (hasPreferences) {
                    checkWeatherAndRecommendMealsPreference()
                } else {
                    checkWeatherAndRecommendMeals()
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(32, 16, 32, 0)
            params.gravity = Gravity.CENTER
            layoutParams = params
        }
    }

    private fun createLocationTextView(): TextView {
        return TextView(this).apply {
            text = "📍 Getting your location..."
            textSize = 16f
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            setPadding(24, 24, 24, 24)
            setTextColor(Color.parseColor("#34495E"))
            setBackgroundResource(R.drawable.card_background)
            gravity = Gravity.CENTER

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 16, 0, 32)
            layoutParams = params
            alpha = 0.9f
        }.also { locationTextView = it }
    }

    private fun createFooterText(): TextView {
        return TextView(this).apply {
            text = "Powered by AI • Weather-Smart Dining"
            textSize = 14f
            setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC))
            setTextColor(Color.parseColor("#95A5A6"))
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 0)
        }
    }

    // Weather and Location Logic
    private fun checkWeatherAndRecommendMeals() {
        getCurrentLocation { lat, lon ->
            updateLocationDisplay(lat, lon)
            fetchWeatherData(lat, lon)
        }
    }

    private fun checkWeatherAndRecommendMealsPreference() {
        getCurrentLocation { lat, lon ->
            updateLocationDisplay(lat, lon)
            fetchWeatherDataWithPreferences(lat, lon)
        }
    }

    private fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        callback(location.latitude, location.longitude)
                    } else {
                        showWarning(this, "Could not get location. Using test data.")
                        locationTextView.text = "📍 Location: Test coordinates (New York)"
                        callback(40.7128, -74.0060)
                    }
                }
                .addOnFailureListener {
                    showError(this, "Failed to get location. Using test data.")
                    locationTextView.text = "📍 Location: Failed to get location"
                    callback(40.7128, -74.0060)
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun updateLocationDisplay(lat: Double, lon: Double) {
        val locationText = "📍 Location: ${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}"
        locationTextView.text = locationText
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        weatherService.getWeatherData(lat, lon, object : WeatherCallback {
            override fun onWeatherAnalyzed(condition: WeatherCondition) {
                runOnUiThread {
                    Log.d("Weather", "Condition: $condition")
                    updateWeatherDisplay(condition)
                    showMealRecommendations(condition)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    updateWeatherError(error)
                }
            }
        })
    }

    private fun fetchWeatherDataWithPreferences(lat: Double, lon: Double) {
        weatherService.getWeatherData(lat, lon, object : WeatherCallback {
            override fun onWeatherAnalyzed(condition: WeatherCondition) {
                runOnUiThread {
                    Log.d("Weather", "Condition: $condition")
                    updateWeatherDisplay(condition)
                    showMealRecommendationsWithPreferences(condition)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    updateWeatherError(error)
                }
            }
        })
    }

    // Weather Display Updates
    private fun updateWeatherDisplay(condition: WeatherCondition) {
        weatherIcon.text = getWeatherEmoji(condition)
        weatherIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).withEndAction {
            weatherIcon.animate().scaleX(1f).scaleY(1f).setDuration(200)
        }

        temperatureText.text = formatTemperature(condition)
        weatherText.text = formatWeatherDescription(condition)
        descriptionText.text = formatAdditionalDetails(condition)
        updateWeatherColors(condition)
    }

    private fun updateWeatherError(error: String) {
        weatherIcon.text = "😔"
        temperatureText.text = "Oops! Weather unavailable"
        weatherText.text = "Don't worry - we can still help you!"
        descriptionText.text = "🍽️ Getting general meal recommendations..."
        weatherText.setTextColor(Color.parseColor("#E67E22"))
    }

    private fun getWeatherEmoji(condition: WeatherCondition): String {
        val conditionStr = condition.description?.lowercase() ?: condition.toString().lowercase()
        return when {
            conditionStr.contains("clear") || conditionStr.contains("sunny") -> "☀️"
            conditionStr.contains("cloud") -> "☁️"
            conditionStr.contains("rain") -> "🌧️"
            conditionStr.contains("drizzle") -> "🌦️"
            conditionStr.contains("thunder") || conditionStr.contains("storm") -> "⛈️"
            conditionStr.contains("snow") -> "❄️"
            conditionStr.contains("mist") || conditionStr.contains("fog") -> "🌫️"
            conditionStr.contains("hot") || conditionStr.contains("warm") -> "🌡️"
            conditionStr.contains("cold") || conditionStr.contains("cool") -> "🧊"
            else -> "🌤️"
        }
    }

    private fun formatTemperature(condition: WeatherCondition): String {
        return when (condition.temperature?.toInt()) {
            null -> "🌡️ Temperature Loading..."
            in 30..Int.MAX_VALUE -> "🔥 ${condition.temperature.toInt()}°C - Hot!"
            in 25..29 -> "☀️ ${condition.temperature.toInt()}°C - Warm"
            in 15..24 -> "🌤️ ${condition.temperature.toInt()}°C - Pleasant"
            in 5..14 -> "🧥 ${condition.temperature.toInt()}°C - Cool"
            else -> "🧊 ${condition.temperature.toInt()}°C - Cold"
        }
    }

    private fun formatWeatherDescription(condition: WeatherCondition): String {
        val desc = condition.description?.lowercase() ?: ""
        return when {
            desc.contains("clear") -> "Perfect weather for outdoor dining! ☀️"
            desc.contains("cloud") -> "Nice cloudy weather today 🌥️"
            desc.contains("rain") -> "Rainy day - perfect for comfort food! 🌧️"
            desc.contains("drizzle") -> "Light drizzle outside 🌦️"
            desc.contains("thunder") -> "Thunderstorm weather ⛈️"
            desc.contains("snow") -> "Snowy weather - time for hot meals! ❄️"
            desc.contains("fog") || desc.contains("mist") -> "Misty weather today 🌫️"
            desc.contains("hot") -> "It's quite hot outside! 🔥"
            desc.contains("cold") -> "Bundle up - it's cold out there! 🧊"
            desc.isNotEmpty() -> "Current weather: ${desc.capitalize()}"
            else -> "Weather data received! 🌤️"
        }
    }

    private fun formatAdditionalDetails(condition: WeatherCondition): String {
        val details = mutableListOf<String>()
        condition.description?.let {
            if (it.contains("humid", true)) details.add("💧 Humid conditions")
            if (it.contains("dry", true)) details.add("🏜️ Dry air")
            if (it.contains("windy", true)) details.add("💨 Windy weather")
        }
        details.add("🍽️ Getting meal suggestions...")
        return if (details.isNotEmpty()) {
            details.joinToString(" • ")
        } else {
            "🤖 AI is analyzing the perfect meals for this weather!"
        }
    }

    private fun updateWeatherColors(condition: WeatherCondition) {
        val desc = condition.description?.lowercase() ?: ""
        val color = when {
            desc.contains("rain") || desc.contains("storm") -> "#3498DB"
            desc.contains("sun") || desc.contains("clear") -> "#F39C12"
            desc.contains("snow") -> "#85C1E9"
            desc.contains("hot") -> "#E74C3C"
            else -> "#34495E"
        }
        weatherText.setTextColor(Color.parseColor(color))
    }

    // Meal Recommendations
    private fun showMealRecommendations(condition: WeatherCondition) {
        Toast.makeText(this, "Getting AI meal recommendations...", Toast.LENGTH_SHORT).show()
        geminiService.getMealRecommendation(condition, object : GeminiCallback {
            override fun onMealRecommended(mealName: String) {
                runOnUiThread {
                    showGeminiRecommendationDialog(mealName, condition)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    showError(this@MainActivity, "Gemini AI error: $error")
                    searchSpoonacularWeatherBasedRecipes(condition)
                }
            }
        })
    }

    private fun showMealRecommendationsWithPreferences(condition: WeatherCondition) {
        Toast.makeText(this, "Getting personalized AI meal recommendations...", Toast.LENGTH_SHORT).show()

        val country = countryInput.text.toString().trim()
        val ingredients = ingredientsInput.text.toString().trim()
        val others = othersInput.text.toString().trim()

        geminiService.getMealRecommendationWithPreferences(
            condition, country, ingredients, others,
            object : GeminiCallback {
                override fun onMealRecommended(mealName: String) {
                    runOnUiThread {
                        showGeminiRecommendationDialogWithPreferences(mealName, condition, country, ingredients, others)
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        showError(this@MainActivity, "Gemini AI error: $error")
                        searchSpoonacularWeatherBasedRecipes(condition)
                    }
                }
            }
        )
    }

    private fun showGeminiRecommendationDialog(mealName: String, condition: WeatherCondition) {
        AlertDialog.Builder(this)
            .setTitle("🤖 AI Recommendation")
            .setMessage("${condition.description}\nTemperature: ${String.format("%.1f", condition.temperature)}°C\n\nGemini AI suggests: $mealName")
            .setPositiveButton("Get Recipe") { _, _ ->
                searchForRecipe(mealName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGeminiRecommendationDialogWithPreferences(
        mealName: String,
        condition: WeatherCondition,
        country: String,
        ingredients: String,
        others: String
    ) {
        val preferencesText = buildString {
            if (country.isNotEmpty()) append("Cuisine: $country\n")
            if (ingredients.isNotEmpty()) append("Ingredients: $ingredients\n")
            if (others.isNotEmpty()) append("Other: $others\n")
        }

        AlertDialog.Builder(this)
            .setTitle("🤖 Personalized AI Recommendation")
            .setMessage("${condition.description}\nTemperature: ${String.format("%.1f", condition.temperature)}°C\n\n$preferencesText\nGemini AI suggests: $mealName")
            .setPositiveButton("Get Recipe") { _, _ ->
                searchForRecipe(mealName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Recipe Search and Display
    private fun searchSpoonacularWeatherBasedRecipes(condition: WeatherCondition) {
        spoonacularService.searchWeatherBasedRecipes(condition, object : SpoonacularCallback {
            override fun onRecipesFound(recipes: List<SpoonacularRecipe>) {
                runOnUiThread {
                    if (recipes.isNotEmpty()) {
                        showWeatherRecipeChoices(recipes, condition)
                    } else {
                        showWarning(this@MainActivity, "No weather-appropriate recipes found")
                        showOriginalMealRecommendations(condition)
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    showError(this@MainActivity, "Recipe search failed: $error")
                    showOriginalMealRecommendations(condition)
                }
            }
        })
    }

    private fun showWeatherRecipeChoices(recipes: List<SpoonacularRecipe>, condition: WeatherCondition) {
        val recipeNames = recipes.map { "${it.title} (${it.readyInMinutes} min)" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Perfect Recipes for This Weather")
            .setMessage("${condition.description}\nTemperature: ${String.format("%.1f", condition.temperature)}°C")
            .setItems(recipeNames) { _, which ->
                val selectedRecipe = recipes[which]
                getRecipeDetails(selectedRecipe.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOriginalMealRecommendations(condition: WeatherCondition) {
        val engine = MealRecommendationEngine()
        val meals = engine.getMealRecommendations(condition)
        val mealNames = meals.map { "${it.name} (${it.cookingTimeMinutes} min)" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Weather-Based Meal Suggestions")
            .setMessage("${condition.description}\nTemperature: ${String.format("%.1f", condition.temperature)}°C")
            .setItems(mealNames) { _, which ->
                val selectedMeal = meals[which]
                onMealSelected(selectedMeal)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun onMealSelected(meal: MealSuggestion) {
        val ingredientList = meal.ingredients.joinToString("\n• ", "• ")

        AlertDialog.Builder(this)
            .setTitle(meal.name)
            .setMessage("Ingredients needed:\n$ingredientList\n\nCooking time: ${meal.cookingTimeMinutes} minutes")
            .setPositiveButton("View Full Recipe") { _, _ ->
                searchForRecipe(meal.name)
            }
            .setNeutralButton("Create Shopping List") { _, _ ->
                createShoppingList(meal)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun searchForRecipe(mealName: String) {
        showDebug(this, "Searching for recipe: $mealName")
        Toast.makeText(this, "Searching for recipe: $mealName", Toast.LENGTH_SHORT).show()

        spoonacularService.searchRecipes(mealName, object : SpoonacularCallback {
            override fun onRecipesFound(recipes: List<SpoonacularRecipe>) {
                runOnUiThread {
                    if (recipes.isNotEmpty()) {
                        val recipe = recipes[0]
                        getRecipeDetails(recipe.id)
                    } else {
                        showWarning(this@MainActivity, "No recipes found for $mealName")
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    showError(this@MainActivity, "Recipe search failed: $error")
                }
            }
        })
    }

    private fun getRecipeDetails(recipeId: Int) {
        Toast.makeText(this, "Getting recipe details...", Toast.LENGTH_SHORT).show()

        spoonacularService.getRecipeDetails(recipeId, object : RecipeDetailsCallback {
            override fun onRecipeDetailsReceived(recipe: RecipeDetails) {
                runOnUiThread {
                    showRecipeDetails(recipe)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    showError(this@MainActivity, "Failed to get recipe details: $error")
                }
            }
        })
    }

    private fun showRecipeDetails(recipe: RecipeDetails) {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val titleView = TextView(this).apply {
            text = recipe.title
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(titleView)

        val infoView = TextView(this).apply {
            val info = buildString {
                if (recipe.readyInMinutes > 0) append("⏱️ Ready in: ${recipe.readyInMinutes} minutes\n")
                if (recipe.servings > 0) append("🍽️ Servings: ${recipe.servings}\n")
                if (recipe.pricePerServing > 0) append("💰 Cost per serving: ${String.format("%.2f", recipe.pricePerServing / 100)}\n\n")
            }
            text = info
            setPadding(0, 0, 0, 8)
        }
        layout.addView(infoView)

        if (recipe.summary.isNotEmpty()) {
            val summaryView = TextView(this).apply {
                text = Html.fromHtml(recipe.summary.replace("<.*?>".toRegex(), ""), Html.FROM_HTML_MODE_COMPACT)
                setPadding(0, 0, 0, 16)
            }
            layout.addView(summaryView)
        }

        val ingredientsTitle = TextView(this).apply {
            text = "Ingredients:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        layout.addView(ingredientsTitle)

        recipe.extendedIngredients.forEach { ingredient ->
            val ingredientView = TextView(this).apply {
                text = "• ${ingredient.original}"
                setPadding(16, 4, 0, 4)
            }
            layout.addView(ingredientView)
        }

        if (recipe.instructions.isNotEmpty()) {
            val instructionsTitle = TextView(this).apply {
                text = "Instructions:"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 8)
            }
            layout.addView(instructionsTitle)

            val instructionsView = TextView(this).apply {
                text = Html.fromHtml(recipe.instructions.replace("<.*?>".toRegex(), ""), Html.FROM_HTML_MODE_COMPACT)
                setPadding(0, 0, 0, 16)
            }
            layout.addView(instructionsView)
        }

        scrollView.addView(layout)

        AlertDialog.Builder(this)
            .setTitle("Full Recipe")
            .setView(scrollView)
            .setPositiveButton("Create Shopping List") { _, _ ->
                createSpoonacularShoppingList(recipe)
            }
            .setNeutralButton("Start Smart Cooking") { _, _ ->
                startSmartCooking(recipe)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun createSpoonacularShoppingList(recipe: RecipeDetails) {
        val ingredients = recipe.extendedIngredients.map { it.name }
        val meal = MealSuggestion(recipe.title, ingredients, "spoonacular", recipe.readyInMinutes)
        createShoppingList(meal)
    }

    private fun createShoppingList(meal: MealSuggestion) {
        val message = "Shopping list created for ${meal.name}!"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.d("ShoppingList", "Created list for: ${meal.name}")
        Log.d("ShoppingList", "Ingredients: ${meal.ingredients.joinToString(", ")}")

        val ingredientList = meal.ingredients.joinToString("\n• ", "• ")
        AlertDialog.Builder(this)
            .setTitle("Shopping List - ${meal.name}")
            .setMessage(ingredientList)
            .setPositiveButton("OK", null)
            .show()
    }

    // Smart Home Integration
    private fun initializeHomeApp() {
        try {
            val homeApp = HomeApp.getInstance()
            homeApp.initializeWithActivity(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "HomeApp not available: ${e.message}")
        }
    }

    class SmartHomeIntegration(private val homeApp: HomeApp) {

        companion object {
            private const val TAG = "SmartHomeIntegration"
        }

        fun getCookingStatus(sessionId: String, callback: SmartCookingStatusCallback) {
            try {
                homeApp.smartKitchenIntegration.getCookingStatus(sessionId, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cooking status: ${e.message}")
                callback.onError("Integration error: ${e.message}")
            }
        }

        fun startCooking(recipe: RecipeDetails, deviceId: String, callback: (Boolean, String) -> Unit) {
            try {
                homeApp.smartKitchenIntegration.startCooking(recipe, deviceId, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start cooking: ${e.message}")
                callback(false, "Integration error: ${e.message}")
            }
        }

        fun discoverDevices(callback: (List<SmartHomeDevice>) -> Unit) {
            try {
                homeApp.smartKitchenIntegration.discoverDevices(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to discover devices: ${e.message}")
                callback(emptyList())
            }
        }
    }

    private fun startSmartCooking(recipe: RecipeDetails) {
        try {
            val homeApp = HomeApp.getInstance()
            val autocookers = homeApp.smartHomeManager.getAutocookers()

            if (autocookers.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("No Smart Cookers Found")
                    .setMessage("No compatible smart cooking devices were found. Would you like to:")
                    .setPositiveButton("Search for Devices") { _, _ ->
                        discoverSmartDevices()
                    }
                    .setNeutralButton("Simulate Cooking") { _, _ ->
                        simulateSmartCooking(recipe)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }

            val cookerNames = autocookers.map { "${it.name} (${it.capabilities["brand"] ?: "Unknown"})" }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Select Smart Cooker")
                .setItems(cookerNames) { _, which ->
                    val selectedCooker = autocookers[which]
                    startCookingOnDevice(recipe, selectedCooker.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.w("MainActivity", "Smart cooking not available: ${e.message}")
            simulateSmartCooking(recipe)
        }
    }

    private fun startCookingOnDevice(recipe: RecipeDetails, deviceId: String) {
        try {
            val homeApp = HomeApp.getInstance()
            homeApp.autocookerManager.startCooking(recipe, deviceId) { success, message ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        monitorCookingProgress("session_${System.currentTimeMillis()}")
                    } else {
                        showError(this, message)
                    }
                }
            }
        } catch (e: Exception) {
            showError(this, "Failed to start smart cooking: ${e.message}")
            simulateSmartCooking(recipe)
        }
    }

    private fun discoverSmartDevices() {
        Toast.makeText(this, "Searching for smart devices...", Toast.LENGTH_SHORT).show()

        try {
            val homeApp = HomeApp.getInstance()
            homeApp.smartHomeManager.discoverDevices { devices ->
                runOnUiThread {
                    val autocookers = devices.filter { it.type == "AUTOCOOKER" }
                    if (autocookers.isNotEmpty()) {
                        Toast.makeText(this, "Found ${autocookers.size} smart cooker(s)!", Toast.LENGTH_LONG).show()
                    } else {
                        showWarning(this, "No smart cookers found. Check device connections.")
                    }
                }
            }
        } catch (e: Exception) {
            showError(this, "Device discovery failed: ${e.message}")
        }
    }

    private fun simulateSmartCooking(recipe: RecipeDetails) {
        Toast.makeText(this, "🧪 Simulating smart cooking for ${recipe.title}", Toast.LENGTH_LONG).show()

        val handler = Handler(Looper.getMainLooper())
        var step = 1

        val simulateStep = object : Runnable {
            override fun run() {
                when (step) {
                    1 -> {
                        Toast.makeText(this@MainActivity, "🔥 Preheating autocooker...", Toast.LENGTH_SHORT).show()
                        step++
                        handler.postDelayed(this, 5000)
                    }
                    2 -> {
                        Toast.makeText(this@MainActivity, "🍳 Cooking ${recipe.title}...", Toast.LENGTH_SHORT).show()
                        step++
                        handler.postDelayed(this, 10000)
                    }
                    3 -> {
                        Toast.makeText(this@MainActivity, "✅ ${recipe.title} is ready!", Toast.LENGTH_LONG).show()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("🍽️ Cooking Complete!")
                            .setMessage("Your ${recipe.title} is ready!\n\n(This was a simulation)")
                            .setPositiveButton("Great!", null)
                            .show()
                    }
                }
            }
        }
        handler.post(simulateStep)
    }

    private fun monitorCookingProgress(sessionId: String) {
        val progressHandler = Handler(Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                checkCookingStatus(sessionId) { isComplete ->
                    if (!isComplete) {
                        progressHandler.postDelayed(this, 30000)
                    }
                }
            }
        }
        progressHandler.post(progressRunnable)
        connectWebSocket()
    }

    private fun checkCookingStatus(sessionId: String, callback: (Boolean) -> Unit) {
        try {
            val homeApp = HomeApp.getInstance()
            val smartHomeIntegration = SmartHomeIntegration(homeApp)

            smartHomeIntegration.getCookingStatus(sessionId, object : SmartCookingStatusCallback {
                override fun onStatusReceived(session: SmartCookingSessionData) {
                    runOnUiThread {
                        updateCookingStatusUI(session)
                        val isComplete = session.status == "completed" || session.status == "done"
                        callback(isComplete)
                        if (isComplete) {
                            onCookingComplete(session)
                        }
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        showError(this@MainActivity, "Failed to get cooking status: $error")
                        callback(true)
                    }
                }
            })
        } catch (e: Exception) {
            Log.w("MainActivity", "Smart home integration not available: ${e.message}")
            callback(true)
        }
    }

    private fun updateCookingStatusUI(session: SmartCookingSessionData) {
        val statusMessage = when (session.status) {
            "cooking" -> {
                val minutes = session.timeRemaining / 60
                "🍳 Cooking ${session.recipeName}\nStep ${session.currentStep}/${session.totalSteps}\n${minutes} minutes remaining"
            }
            "completed", "done" -> "✅ ${session.recipeName} is ready!"
            "preparing" -> "🔄 Preparing to cook ${session.recipeName}..."
            else -> "Status: ${session.status}"
        }
        Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
    }

    private fun onCookingComplete(session: SmartCookingSessionData) {
        try {
            val app = HomeApp.getInstance()
            val speakers = app.smartHomeManager.getSpeakers()

            speakers.forEach { speaker ->
                val announcement = SmartHomeCommand(
                    "SPEAK",
                    mapOf("text" to "Your ${session.recipeName} is ready! Please check your autocooker.")
                )
                app.smartHomeManager.sendCommand(speaker.id, announcement)
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Smart speaker announcement failed: ${e.message}")
        }

        AlertDialog.Builder(this)
            .setTitle("🍽️ Cooking Complete!")
            .setMessage("Your ${session.recipeName} is ready!\n\nTotal cooking time: ${session.totalSteps} steps completed")
            .setPositiveButton("Great!") { _, _ -> }
            .setNeutralButton("Cook Again") { _, _ ->
                searchForRecipe(session.recipeName)
            }
            .show()
    }

    // WebSocket Communication
    private fun connectWebSocket() {
        if (webSocketClient?.isOpen == true) return

        try {
            val uri = URI.create(HomeApp.WEBSOCKET_URL)

            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    runOnUiThread {
                        showDebug(this@MainActivity, "Connected to Smart Kitchen WebSocket")
                    }
                }

                override fun onMessage(message: String?) {
                    message?.let { msg ->
                        runOnUiThread {
                            processCookingUpdate(msg)
                        }
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    runOnUiThread {
                        showDebug(this@MainActivity, "WebSocket disconnected: $reason")
                    }
                }

                override fun onError(ex: Exception?) {
                    runOnUiThread {
                        showError(this@MainActivity, "WebSocket error: ${ex?.message}")
                    }
                }
            }
            webSocketClient?.connect()
        } catch (e: Exception) {
            showError(this, "Failed to connect WebSocket: ${e.message}")
        }
    }

    private fun processCookingUpdate(message: String) {
        try {
            val gson = Gson()
            val updateJson = gson.fromJson(message, JsonObject::class.java)

            val type = updateJson.get("type")?.asString
            val data = updateJson.get("data")?.asJsonObject

            when (type) {
                "cooking_step_complete" -> {
                    val stepNumber = data?.get("step")?.asInt ?: 0
                    val recipeName = data?.get("recipeName")?.asString ?: "Recipe"
                    showDebug(this, "Step $stepNumber completed for $recipeName")
                }
                "cooking_complete" -> {
                    val recipeName = data?.get("recipeName")?.asString ?: "Recipe"
                    Toast.makeText(this, "🍽️ $recipeName is ready!", Toast.LENGTH_LONG).show()
                }
                "timer_update" -> {
                    val timeRemaining = data?.get("timeRemaining")?.asInt ?: 0
                    val minutes = timeRemaining / 60
                    showDebug(this, "Timer: $minutes minutes remaining")
                }
                "device_status" -> {
                    val deviceName = data?.get("deviceName")?.asString ?: "Device"
                    val status = data?.get("status")?.asString ?: "unknown"
                    showDebug(this, "$deviceName status: $status")
                }
            }
        } catch (e: Exception) {
            showError(this, "Failed to process cooking update: ${e.message}")
        }
    }
}

class WeatherService {
    private val weatherApi: WeatherApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(MainActivity.BASE_WEATHER_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        weatherApi = retrofit.create(WeatherApi::class.java)
    }

    fun getWeatherData(lat: Double, lon: Double, callback: WeatherCallback) {
        val call = weatherApi.getCurrentWeather(lat, lon, MainActivity.WEATHER_API_KEY)

        call.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { weather ->
                        val condition = analyzeWeather(weather)
                        callback.onWeatherAnalyzed(condition)
                    }
                } else {
                    callback.onError("Weather API error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                callback.onError(t.message ?: "Unknown error")
            }
        })
    }

    private fun analyzeWeather(weather: WeatherResponse): WeatherCondition {
        val mainWeather = weather.weather[0].main.lowercase()
        val temp = weather.main.temp - 273.15

        return when {
            mainWeather.contains("rain") || mainWeather.contains("storm") -> {
                WeatherCondition("RAINY", temp, "Perfect weather for comfort food!")
            }
            temp > 25 -> {
                WeatherCondition("HOT", temp, "Time for light, refreshing meals!")
            }
            temp < 10 -> {
                WeatherCondition("COLD", temp, "Warm, hearty meals will hit the spot!")
            }
            mainWeather.contains("clear") || mainWeather.contains("sun") -> {
                WeatherCondition("SUNNY", temp, "Great weather for fresh, vibrant cooking!")
            }
            else -> {
                WeatherCondition("CLOUDY", temp, "Moderate weather calls for balanced meals!")
            }
        }
    }
}

class SpoonacularService {
    private val spoonacularApi: SpoonacularApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(MainActivity.BASE_SPOONACULAR_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        spoonacularApi = retrofit.create(SpoonacularApi::class.java)
    }

    fun searchRecipes(query: String, callback: SpoonacularCallback) {
        val call = spoonacularApi.searchRecipes(query, MainActivity.SPOONACULAR_API_KEY)

        call.enqueue(object : Callback<SpoonacularSearchResponse> {
            override fun onResponse(call: Call<SpoonacularSearchResponse>, response: Response<SpoonacularSearchResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { searchResponse ->
                        callback.onRecipesFound(searchResponse.results)
                    }
                } else {
                    callback.onError("Spoonacular API error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<SpoonacularSearchResponse>, t: Throwable) {
                callback.onError(t.message ?: "Unknown error")
            }
        })
    }

    fun getRecipeDetails(recipeId: Int, callback: RecipeDetailsCallback) {
        val call = spoonacularApi.getRecipeInformation(recipeId, MainActivity.SPOONACULAR_API_KEY)

        call.enqueue(object : Callback<RecipeDetails> {
            override fun onResponse(call: Call<RecipeDetails>, response: Response<RecipeDetails>) {
                if (response.isSuccessful) {
                    response.body()?.let { recipe ->
                        callback.onRecipeDetailsReceived(recipe)
                    }
                } else {
                    callback.onError("Recipe details API error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<RecipeDetails>, t: Throwable) {
                callback.onError(t.message ?: "Unknown error")
            }
        })
    }

    fun searchWeatherBasedRecipes(weather: WeatherCondition, callback: SpoonacularCallback) {
        val (tags, type) = when (weather.type) {
            "RAINY" -> Pair("comfort food,warm,soup", "soup,main course")
            "HOT" -> Pair("light,fresh,cold,refreshing", "salad,appetizer")
            "COLD" -> Pair("warm,hearty,comfort food", "main course,soup")
            "SUNNY" -> Pair("fresh,healthy,grilled", "main course,salad")
            else -> Pair("healthy,balanced", "main course")
        }

        val call = spoonacularApi.searchRecipesByTags(
            tags = tags,
            type = type,
            apiKey = MainActivity.SPOONACULAR_API_KEY,
            number = 3
        )

        call.enqueue(object : Callback<SpoonacularSearchResponse> {
            override fun onResponse(call: Call<SpoonacularSearchResponse>, response: Response<SpoonacularSearchResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { searchResponse ->
                        callback.onRecipesFound(searchResponse.results)
                    }
                } else {
                    callback.onError("Spoonacular API error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<SpoonacularSearchResponse>, t: Throwable) {
                callback.onError(t.message ?: "Unknown error")
            }
        })
    }
}

class MealRecommendationEngine {
    fun getMealRecommendations(weather: WeatherCondition): List<MealSuggestion> {
        val suggestions = mutableListOf<MealSuggestion>()

        when (weather.type) {
            "RAINY" -> {
                suggestions.addAll(listOf(
                    MealSuggestion(
                        "Chicken Soup",
                        listOf("chicken breast", "carrots", "celery", "onion", "chicken broth", "noodles"),
                        "comfort",
                        45
                    ),
                    MealSuggestion(
                        "Beef Stew",
                        listOf("beef chunks", "potatoes", "carrots", "beef broth", "tomato paste", "herbs"),
                        "comfort",
                        120
                    )
                ))
            }
            "HOT" -> {
                suggestions.addAll(listOf(
                    MealSuggestion(
                        "Quinoa Salad",
                        listOf("quinoa", "cucumber", "tomatoes", "olives", "feta cheese", "olive oil", "lemon"),
                        "light",
                        20
                    ),
                    MealSuggestion(
                        "Gazpacho",
                        listOf("tomatoes", "cucumber", "bell pepper", "onion", "garlic", "olive oil", "vinegar"),
                        "cold",
                        15
                    )
                ))
            }
            "COLD" -> {
                suggestions.addAll(listOf(
                    MealSuggestion(
                        "Lentil Curry",
                        listOf("red lentils", "coconut milk", "curry powder", "onion", "garlic", "ginger", "rice"),
                        "warming",
                        35
                    ),
                    MealSuggestion(
                        "Mac and Cheese",
                        listOf("pasta", "cheddar cheese", "milk", "butter", "flour", "breadcrumbs"),
                        "comfort",
                        60
                    )
                ))
            }
            else -> {
                suggestions.add(
                    MealSuggestion(
                        "Balanced Bowl",
                        listOf("brown rice", "grilled chicken", "steamed broccoli", "avocado", "sesame oil"),
                        "balanced",
                        30
                    )
                )
            }
        }
        return suggestions
    }
}

// ===============================================
// API INTERFACES
// ===============================================

interface WeatherApi {
    @GET("weather")
    fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String
    ): Call<WeatherResponse>
}

interface SpoonacularApi {
    @GET("recipes/complexSearch")
    fun searchRecipes(
        @Query("query") query: String,
        @Query("apiKey") apiKey: String,
        @Query("number") number: Int = 5,
        @Query("addRecipeInformation") addRecipeInformation: Boolean = true
    ): Call<SpoonacularSearchResponse>

    @GET("recipes/complexSearch")
    fun searchRecipesByTags(
        @Query("tags") tags: String,
        @Query("type") type: String?,
        @Query("apiKey") apiKey: String,
        @Query("number") number: Int = 3,
        @Query("addRecipeInformation") addRecipeInformation: Boolean = true
    ): Call<SpoonacularSearchResponse>

    @GET("recipes/{id}/information")
    fun getRecipeInformation(
        @Path("id") id: Int,
        @Query("apiKey") apiKey: String,
        @Query("includeNutrition") includeNutrition: Boolean = false
    ): Call<RecipeDetails>
}

interface GeminiApi {
    @POST("v1beta/models/gemini-1.5-flash-latest:generateContent")
    @Headers("Content-Type: application/json")
    fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Call<GeminiResponse>
}

// ===============================================
// DATA MODELS
// ===============================================

data class WeatherCondition(
    val type: String,
    val temperature: Double,
    val description: String
)

data class WeatherResponse(
    val weather: List<Weather>,
    val main: Main
)

data class Weather(
    val main: String,
    val description: String
)

data class Main(
    val temp: Double,
    val humidity: Int
)

data class MealSuggestion(
    val name: String,
    val ingredients: List<String>,
    val category: String,
    val cookingTimeMinutes: Int
)

data class SpoonacularSearchResponse(
    val results: List<SpoonacularRecipe>
)

data class SpoonacularRecipe(
    val id: Int,
    val title: String,
    val image: String,
    val readyInMinutes: Int
)

data class RecipeDetails(
    val id: Int,
    val title: String,
    val image: String,
    val readyInMinutes: Int,
    val servings: Int,
    val pricePerServing: Double,
    val summary: String,
    val instructions: String,
    val extendedIngredients: List<ExtendedIngredient>
)

data class ExtendedIngredient(
    val id: Int,
    val name: String,
    val original: String,
    val amount: Double,
    val unit: String
)

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    val content: GeminiContent
)

// ===============================================
// CALLBACK INTERFACES
// ===============================================

interface WeatherCallback {
    fun onWeatherAnalyzed(condition: WeatherCondition)
    fun onError(error: String)
}

interface SpoonacularCallback {
    fun onRecipesFound(recipes: List<SpoonacularRecipe>)
    fun onError(error: String)
}

interface RecipeDetailsCallback {
    fun onRecipeDetailsReceived(recipe: RecipeDetails)
    fun onError(error: String)
}

interface GeminiCallback {
    fun onMealRecommended(mealName: String)
    fun onError(error: String)
}


// ===============================================
// SERVICE CLASSES
// ===============================================

class GeminiService {
    private val geminiApi: GeminiApi

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(MainActivity.BASE_GEMINI_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        geminiApi = retrofit.create(GeminiApi::class.java)
    }

    fun getMealRecommendation(weather: WeatherCondition, callback: GeminiCallback) {
        val prompt = createWeatherPrompt(weather)
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )
        makeGeminiRequest(request, callback)
    }

    fun getMealRecommendationWithPreferences(
        weather: WeatherCondition,
        country: String = "",
        ingredients: String = "",
        others: String = "",
        callback: GeminiCallback
    ) {
        val prompt = createPreferencesPrompt(weather, country, ingredients, others)
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )
        makeGeminiRequest(request, callback)
    }

    private fun makeGeminiRequest(request: GeminiRequest, callback: GeminiCallback) {
        val call = geminiApi.generateContent(MainActivity.GEMINI_API_KEY, request)

        call.enqueue(object : Callback<GeminiResponse> {
            override fun onResponse(call: Call<GeminiResponse>, response: Response<GeminiResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let { geminiResponse ->
                        try {
                            val mealName = geminiResponse.candidates[0].content.parts[0].text.trim()
                            callback.onMealRecommended(mealName)
                        } catch (e: Exception) {
                            callback.onError("Failed to parse Gemini response: ${e.message}")
                        }
                    } ?: callback.onError("Empty response from Gemini")
                } else {
                    callback.onError("Gemini API error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<GeminiResponse>, t: Throwable) {
                callback.onError(t.message ?: "Unknown error")
            }
        })
    }

    private fun createWeatherPrompt(weather: WeatherCondition): String {
        return """
            You are a culinary expert. Based on these weather conditions, recommend ONE specific meal:
            
            Weather Type: ${weather.type}
            Temperature: ${String.format("%.1f", weather.temperature)}°C
            Description: ${weather.description}
            
            Consider:
            - Hot weather (>25°C): light, refreshing meals like salads, cold soups
            - Cold weather (<10°C): warm, hearty meals like stews, casseroles
            - Rainy weather: comfort foods like soups, pasta dishes
            - Moderate weather: balanced meals
            
            Respond with only the meal name (e.g., "Chicken Caesar Salad" or "Beef Stew"). No explanation needed.
        """.trimIndent()
    }

    private fun createPreferencesPrompt(
        weather: WeatherCondition,
        country: String = "",
        ingredients: String = "",
        others: String = ""
    ): String {
        return """
            You are a culinary expert. Based on these weather conditions and user preferences, recommend ONE specific meal:
            
            Weather: ${weather.description ?: "Current weather conditions"}
            Temperature: ${weather.temperature ?: "Unknown"}°C
            
            User Preferences:
            - Preferred Cuisine/Country: ${if (country.isNotEmpty()) country else "No preference"}
            - Preferred Ingredients: ${if (ingredients.isNotEmpty()) ingredients else "No preference"}  
            - Other Preferences: ${if (others.isNotEmpty()) others else "No preference"}
            
            Weather Guidelines:
            - Hot weather (>25°C): light, refreshing meals like salads, cold soups
            - Cold weather (<10°C): warm, hearty meals like stews, casseroles
            - Rainy weather: comfort foods like soups, pasta dishes
            - Moderate weather: balanced meals
            
            Important: Incorporate the user's preferences while considering the weather.
            
            Respond with only the meal name (e.g., "Chicken Caesar Salad" or "Beef Stew"). No explanation needed.
        """.trimIndent()
    }
}
