package com.example.nutrition_analysis

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nutrition_analysis.databinding.ActivityFoodDetailsBinding
import java.text.SimpleDateFormat
import java.util.*

class FoodDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFoodDetailsBinding
    private lateinit var apiService: ApiService
    private lateinit var databaseHelper: DatabaseHelper

    private var imagePath: String? = null
    private var foodName: String? = null
    private var mealType: String? = null
    private var date: String? = null

    private var currentNutritionInfo: NutritionInfo? = null
    private var currentPortionSize: Float = 1.0f
    private var basePortionSize: Float = 100f // Base nutrition values are per 100g

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInitialData()
        setupUI()
        setupListeners()
    }

    private fun setupInitialData() {
        try {
            apiService = ApiService.getInstance(this)
            databaseHelper = DatabaseHelper(this)

            intent.extras?.let { extras ->
                imagePath = extras.getString(MainActivity.EXTRA_IMAGE_PATH)
                foodName = extras.getString(MainActivity.EXTRA_FOOD_NAME)
                mealType = extras.getString(MainActivity.EXTRA_MEAL_TYPE)
                date = extras.getString(MainActivity.EXTRA_DATE)
                currentNutritionInfo = extras.getParcelable(MainActivity.EXTRA_NUTRITION_INFO)
            }

            if (currentNutritionInfo != null) {
                showLoadingState(false)
                updateNutritionDisplay()
            } else if (!foodName.isNullOrEmpty()) {
                showLoadingState(true)
                fetchNutritionInfo()
            } else {
                showError("Food name not provided")
                showLoadingState(false)
            }
        } catch (e: Exception) {
            Log.e("FoodDetails", "Error in setupInitialData: ${e.message}")
            showError("Error initializing data")
        }
    }
    private fun fetchNutritionInfo() {
        foodName?.let { name ->
            apiService.getNutritionInfo(
                foodName = name,
                onSuccess = { info ->
                    currentNutritionInfo = info
                    runOnUiThread {
                        showLoadingState(false)
                        updateNutritionDisplay()
                        setupPortionControls()
                    }
                },
                onError = { error ->
                    Log.e("FoodDetails", "Nutrition API Error: $error")
                    runOnUiThread {
                        showLoadingState(false)
                        showError("Unable to fetch nutrition info. Please try again.")
                    }
                }
            )
        }
    }

    private fun setupPortionControls() {
        binding.portionSizeSlider.apply {
            value = 100f  // Start at 100g
            valueFrom = 0f
            valueTo = 1000f  // Max 1000g
            stepSize = 5f    // 5g steps

            addOnChangeListener { _, value, _ ->
                currentPortionSize = value
                updateNutritionDisplay()
                binding.portionEditText.setText(value.toInt().toString())
            }
        }

        binding.portionEditText.setText("100")
        binding.portionEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val value = s.toString().toFloatOrNull() ?: 100f
                    if (value in 0f..1000f && s.toString() != currentPortionSize.toInt().toString()) {
                        binding.portionSizeSlider.value = value
                        currentPortionSize = value
                        updateNutritionDisplay()
                    }
                } catch (e: Exception) {
                    Log.e("FoodDetails", "Error updating portion: ${e.message}")
                }
            }
        })
    }
    private fun setupUI() {

        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = foodName ?: "Food Details"
            }

            // Load and display the captured image
            imagePath?.let { path ->
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    binding.foodImageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("FoodDetails", "Error loading image: ${e.message}")
                    binding.foodImageView.setImageResource(R.drawable.ic_empty_state)
                }
            }

            // Set food name
            binding.foodNameTextView.text = foodName ?: "Unknown Food"

            // Setup portion size controls
            binding.portionSizeSlider.apply {
                value = currentPortionSize
                addOnChangeListener { _, value, _ ->
                    currentPortionSize = value
                    updateNutritionDisplay()
                }
            }
        } catch (e: Exception) {
            Log.e("FoodDetails", "Error in setupUI: ${e.message}")
            showError("Error setting up UI")
        }
    }

    private fun setupListeners() {
        binding.saveButton.setOnClickListener {
            if (currentNutritionInfo != null) {
                saveFoodEntry()
            } else {
                showError("Please wait for nutrition information to load")
            }
        }

        binding.deleteButton.setOnClickListener {
            finish()
        }

        binding.portionEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val value = s.toString().toFloatOrNull()
                    if (value != null && value in 0.1f..10.0f) {
                        binding.portionSizeSlider.value = value
                        currentPortionSize = value
                        updateNutritionDisplay()
                    }
                } catch (e: Exception) {
                    Log.e("FoodDetails", "Error updating portion: ${e.message}")
                }
            }
        })
    }

    private fun updateNutritionDisplay() {
        try {
            currentNutritionInfo?.let { info ->
                val multiplier = currentPortionSize / 100f // Base values are per 100g

                // Update portion size text
                binding.portionEditText.setText(String.format("%.1f", currentPortionSize))

                // Calculate scaled values
                val scaledInfo = info.scaleByPortion(multiplier)

                // Update display
                binding.caloriesTextView.text = "${(info.calories * multiplier).toInt()} kcal"
                binding.proteinTextView.text = String.format("%.1fg", info.protein * multiplier)
                binding.carbsTextView.text = String.format("%.1fg", info.carbs * multiplier)
                binding.fatTextView.text = String.format("%.1fg", info.fat * multiplier)

                // Update progress bar (assuming 2000 kcal daily goal)
                binding.caloriesProgress.progress = ((info.calories * multiplier) / 2000 * 100).toInt()

                // Make nutrition card visible
                binding.nutritionCard.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("FoodDetails", "Error updating display: ${e.message}")
            showError("Error updating nutrition display")
        }
    }


    private fun saveFoodEntry() {
        try {
            currentNutritionInfo?.let { info ->
                val multiplier = currentPortionSize / basePortionSize

                val success = databaseHelper.insertFoodEntry(
                    date = date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    mealType = mealType ?: "Other",
                    imagePath = imagePath,
                    foodName = foodName ?: "Unknown Food",
                    portionSize = currentPortionSize,
                    calories = (info.calories * multiplier).toInt(),
                    protein = info.protein * multiplier,
                    carbs = info.carbs * multiplier,
                    fat = info.fat * multiplier,
                    nutritionDetails = "Per ${currentPortionSize}g serving"
                )

                if (success != -1L) {
                    Toast.makeText(this, "Food logged successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    showError("Error saving food entry")
                }
            } ?: showError("Nutrition information not available")
        } catch (e: Exception) {
            Log.e("FoodDetails", "Error saving entry: ${e.message}")
            showError("Error saving food entry")
        }
    }

    private fun showLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.nutritionCard.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}