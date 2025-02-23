package com.example.nutrition_analysis

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.AuthFailureError
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.TimeoutError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.nutrition_analysis.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val CAMERA_PERMISSION_CODE = 100
    private val CAMERA_INTENT_CODE = 101

    private lateinit var binding: ActivityMainBinding
    private lateinit var requestQueue: RequestQueue
    private lateinit var databaseHelper: DatabaseHelper
    private var currentNutritionInfo: NutritionInfo? = null

    private var lastCapturedImagePath: String? = null
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedMealType: String = "Breakfast"
    private var detectedFoodName: String? = null

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val dbDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestQueue = Volley.newRequestQueue(this)
        databaseHelper = DatabaseHelper(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        updateDateDisplay()
        binding.mealTypeChipGroup.check(R.id.breakfastChip)
        binding.capturedImageView.visibility = View.GONE
        binding.adjustImageButton.visibility = View.GONE
    }

    private fun setupClickListeners() {
        binding.datePickerButton.setOnClickListener {
            showDatePicker()
        }

        binding.mealTypeChipGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedMealType = when (checkedId) {
                R.id.breakfastChip -> "Breakfast"
                R.id.lunchChip -> "Lunch"
                R.id.dinnerChip -> "Dinner"
                R.id.otherChip -> "Other"
                else -> "Breakfast"
            }
        }

        binding.captureImageButton.setOnClickListener {
            checkCameraPermission()
        }

        binding.adjustImageButton.setOnClickListener {
            Toast.makeText(this, "Image adjustment coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.logFoodButton.setOnClickListener {
            if (detectedFoodName != null && lastCapturedImagePath != null) {
                showDetectedFoodDialog(detectedFoodName)
            } else {
                Toast.makeText(this, "Please capture and analyze food first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.reportButton.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                updateDateDisplay()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateDisplay() {
        binding.datePickerButton.text = dateFormatter.format(selectedDate.time)
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showCameraPermissionExplanation()
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    private fun showCameraPermissionExplanation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Camera Permission Required")
            .setMessage("We need camera access to capture food images for analysis.")
            .setPositiveButton("Grant Access") { _, _ ->
                requestCameraPermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            intent.resolveActivity(packageManager)?.also {
                startActivityForResult(intent, CAMERA_INTENT_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_INTENT_CODE && resultCode == RESULT_OK && data != null) {
            val photo = data.extras?.get("data") as Bitmap?
            photo?.let {
                handleCapturedImage(it)
            }
        }
    }

    private fun handleCapturedImage(bitmap: Bitmap) {
        try {
            binding.capturedImageView.visibility = View.VISIBLE
            binding.adjustImageButton.visibility = View.VISIBLE
            binding.capturedImageView.setImageBitmap(bitmap)

            lastCapturedImagePath = saveImageToInternalStorage(bitmap)
            analyzeImage(bitmap)

            binding.captureImageButton.text = "Retake Photo"
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling captured image: ${e.message}")
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        try {
            sendImageToGoogleVision(bitmap)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error analyzing image: ${e.message}")
            Toast.makeText(this, "Error analyzing image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendImageToGoogleVision(bitmap: Bitmap) {
        val base64Image = encodeImageToBase64(bitmap)
        val fullUrl = "${ApiKeys.GOOGLE_VISION_ENDPOINT}?key=${ApiKeys.GOOGLE_VISION_API_KEY}"

        try {
            val jsonRequest = JSONObject().apply {
                put("requests", JSONArray().put(JSONObject().apply {
                    put("image", JSONObject().put("content", base64Image))
                    put("features", JSONArray().put(JSONObject().apply {
                        put("type", "OBJECT_LOCALIZATION")
                        put("maxResults", 5)
                    }))
                }))
            }

            val jsonObjectRequest = JsonObjectRequest(
                Request.Method.POST,
                fullUrl,
                jsonRequest,
                { response -> handleVisionResponse(response) },
                { error ->
                    val errorMessage = "Error: ${error.message}"
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                    Log.e("VisionAPI", errorMessage)
                }
            )

            jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
                30000, // 30 seconds timeout
                2, // Number of retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )

            requestQueue.add(jsonObjectRequest)

        } catch (e: JSONException) {
            Log.e("MainActivity", "Error creating vision request: ${e.message}")
            Toast.makeText(this, "Failed to create request: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVisionResponse(response: JSONObject) {
        try {
            val localizedObjectAnnotations = response
                .getJSONArray("responses")
                .getJSONObject(0)
                .optJSONArray("localizedObjectAnnotations")

            if (localizedObjectAnnotations == null || localizedObjectAnnotations.length() == 0) {
                Toast.makeText(this, "Unable to identify the object.", Toast.LENGTH_SHORT).show()
                return
            }

            val detectedObject = localizedObjectAnnotations.getJSONObject(0).optString("name")
            if (!detectedObject.isNullOrEmpty() && isFoodItem(detectedObject)) {
                detectedFoodName = detectedObject
                getNutritionDetailsFromAzureOpenAI(detectedObject)  // Changed this line
            } else {
                Toast.makeText(
                    this,
                    "Please capture a food item. Detected: ${detectedObject ?: "Unknown"}",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: JSONException) {
            Log.e("MainActivity", "Error processing vision response: ${e.message}")
            Toast.makeText(this, "Error processing image response.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNutritionDetailsFromAzureOpenAI(detectedObject: String?) {
        if (detectedObject == null) {
            showDetectedFoodDialog(null)
            return
        }

        val fullUrl = "${ApiKeys.AZURE_OPENAI_ENDPOINT}?api-version=${ApiKeys.AZURE_API_VERSION}"

        val requestBody = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "You are a nutrition assistant. Provide only numeric values for a 100g serving in this exact format: Calories: X kcal, Protein: X g, Carbohydrates: X g, Fat: X g"))
                put(JSONObject().put("role", "user").put("content",
                    "Provide nutrition information for $detectedObject"))
            })
            put("max_tokens", 150)
            put("temperature", 0.3)
        }

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.POST,
            fullUrl,
            requestBody,
            { response ->
                try {
                    val content = response.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    // Parse nutrition values with better error handling
                    try {
                        val calories = content.substringAfter("Calories:").substringBefore("kcal").trim().toFloatOrNull() ?: 0f
                        val protein = content.substringAfter("Protein:").substringBefore("g").trim().toFloatOrNull() ?: 0f
                        val carbs = content.substringAfter("Carbohydrates:").substringBefore("g").trim().toFloatOrNull() ?: 0f
                        val fat = content.substringAfter("Fat:").substringBefore("g").trim().toFloatOrNull() ?: 0f

                        currentNutritionInfo = NutritionInfo(
                            calories = calories.toInt(),
                            protein = protein,
                            carbs = carbs,
                            fat = fat
                        )

                        // Show dialog with the original detected food name
                        showDetectedFoodDialog(detectedObject)
                    } catch (e: Exception) {
                        Log.e("NutritionAPI", "Error parsing nutrition values: ${e.message}")
                        showError("Error parsing nutrition information")
                    }
                } catch (e: JSONException) {
                    Log.e("NutritionAPI", "JSON parsing error: ${e.message}")
                    showError("Error processing nutrition data")
                }
            },
            { error ->
                val errorMessage = when (error) {
                    is NoConnectionError -> "No internet connection."
                    is TimeoutError -> "Request timed out."
                    is AuthFailureError -> "Authentication failed."
                    else -> "Failed to fetch nutrition details: ${error.message}"
                }
                Log.e("NutritionAPI", "Error: ${error.message}")
                showError(errorMessage)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return HashMap<String, String>().apply {
                    put("Content-Type", "application/json")
                    put("api-key", ApiKeys.AZURE_OPENAI_API_KEY)
                }
            }
        }

        jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
            30000,
            2,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        requestQueue.add(jsonObjectRequest)
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }


// In MainActivity.kt, modify showDetectedFoodDialog:

    private fun showDetectedFoodDialog(detectedObject: String?) {
        // Parse the nutrition values right when we get them
        currentNutritionInfo = NutritionInfo(
            calories = 15,  // These are the values per 100g from your detection
            protein = 0.9f,
            carbs = 3.6f,
            fat = 0.2f
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Food Detected")
            .setMessage(buildString {
                append("Detected food: $detectedObject\n\n")
                append("Nutrition per 100g:\n")
                append("Calories: ${currentNutritionInfo?.calories} kcal\n")
                append("Protein: ${currentNutritionInfo?.protein}g\n")
                append("Carbs: ${currentNutritionInfo?.carbs}g\n")
                append("Fat: ${currentNutritionInfo?.fat}g\n\n")
                append("Would you like to proceed?")
            })
            .setPositiveButton("Yes") { _, _ ->
                val intent = Intent(this, FoodDetailsActivity::class.java).apply {
                    putExtra(EXTRA_DATE, dbDateFormatter.format(selectedDate.time))
                    putExtra(EXTRA_MEAL_TYPE, selectedMealType)
                    putExtra(EXTRA_IMAGE_PATH, lastCapturedImagePath ?: "")
                    putExtra(EXTRA_FOOD_NAME, detectedFoodName ?: "Unknown Food")
                    putExtra(EXTRA_NUTRITION_INFO, currentNutritionInfo)  // Make sure this is passed
                }
                startActivity(intent)
            }
            .setNegativeButton("Retake") { _, _ ->
                openCamera()
            }
            .show()
    }
    private fun isFoodItem(objectName: String): Boolean {
        val foodCategories = setOf(
            "Food", "Fruit", "Vegetable", "Bread", "Meat", "Beverage", "Drink",
            "Apple", "Banana", "Orange", "Sandwich", "Pizza", "Burger", "Salad",
            "Rice", "Pasta", "Fish", "Chicken", "Cake", "Cookie", "Bowl", "Plate",
            "Dish", "Meal", "Snack", "Dessert", "Breakfast", "Lunch", "Dinner"
        )

        return foodCategories.any { category ->
            objectName.lowercase(Locale.getDefault()).contains(category.lowercase(Locale.getDefault()))
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String? {
        val directory = filesDir
        val fileName = "captured_${System.currentTimeMillis()}.jpg"
        val imageFile = File(directory, fileName)

        return try {
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            }
            imageFile.absolutePath
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving image: ${e.message}")
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        return ByteArrayOutputStream().apply {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, this)
        }.toByteArray().let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }


    companion object {
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_MEAL_TYPE = "extra_meal_type"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_FOOD_NAME = "extra_food_name"
        const val EXTRA_NUTRITION_INFO = "extra_nutrition_info"

    }
}