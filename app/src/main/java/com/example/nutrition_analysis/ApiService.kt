package com.example.nutrition_analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

import com.example.nutrition_analysis.ApiKeys.GOOGLE_VISION_API_KEY
import com.example.nutrition_analysis.ApiKeys.AZURE_OPENAI_API_KEY


class ApiService private constructor(context: Context) {
    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)

    fun analyzeImage(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val base64Image = encodeImageToBase64(bitmap)

        val requestJson = JSONObject().apply {
            put("requests", JSONArray().put(JSONObject().apply {
                put("image", JSONObject().put("content", base64Image))
                put("features", JSONArray().put(JSONObject().apply {
                    put("type", "OBJECT_LOCALIZATION")
                    put("maxResults", 5)
                }))
            }))
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            GOOGLE_VISION_API_KEY, // Using your existing API key/URL
            requestJson,
            { response ->
                try {
                    val annotations = response.getJSONArray("responses")
                        .getJSONObject(0)
                        .getJSONArray("localizedObjectAnnotations")

                    if (annotations.length() > 0) {
                        val detectedObject = annotations.getJSONObject(0).getString("name")
                        onSuccess(detectedObject)
                    } else {
                        onError("No objects detected")
                    }
                } catch (e: Exception) {
                    onError("Error processing response: ${e.message}")
                }
            },
            { error -> onError("Network error: ${error.message}") }
        )

        requestQueue.add(request)
    }

    fun getNutritionInfo(
        foodName: String,
        onSuccess: (NutritionInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = """
        Provide only JSON format nutrition information for $foodName with this exact structure:
        {
            "calories": 100,
            "protein": 2.0,
            "carbs": 25.0,
            "fat": 0.3
        }
        Base values on a 100g serving. Use realistic values.
    """.trimIndent()

        val requestBody = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a nutrition database. Respond only with valid JSON.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.3)
            put("max_tokens", 150)
        }

        val request = object : JsonObjectRequest(
            Method.POST,
            AZURE_OPENAI_API_KEY,
            requestBody,
            { response ->
                try {
                    val contentString = response.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val nutritionJson = JSONObject(contentString)

                    val nutritionInfo = NutritionInfo(
                        calories = nutritionJson.getInt("calories"),
                        protein = nutritionJson.getDouble("protein").toFloat(),
                        carbs = nutritionJson.getDouble("carbs").toFloat(),
                        fat = nutritionJson.getDouble("fat").toFloat()
                    )
                    onSuccess(nutritionInfo)
                } catch (e: Exception) {
                    Log.e("ApiService", "Error parsing nutrition response: ${e.message}")
                    onError("Error parsing nutrition data: ${e.message}")
                }
            },
            { error ->
                Log.e("ApiService", "Network error: ${error.message}")
                onError("Network error: ${error.message}")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf(
                    "Content-Type" to "application/json",
                    "api-key" to AZURE_OPENAI_API_KEY
                )
            }
        }

        requestQueue.add(request)
    }

    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    companion object {
        @Volatile
        private var instance: ApiService? = null

        fun getInstance(context: Context): ApiService {
            return instance ?: synchronized(this) {
                instance ?: ApiService(context).also { instance = it }
            }
        }
    }
}
