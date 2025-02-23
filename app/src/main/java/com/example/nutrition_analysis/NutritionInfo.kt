package com.example.nutrition_analysis

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NutritionInfo(
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f
) : Parcelable {
    companion object {
        fun fromNutrition(calories: Int, protein: Float, carbs: Float, fat: Float): NutritionInfo {
            return NutritionInfo(
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat
            )
        }
    }

    // Helper function to scale nutrition values based on portion size
    fun scaleByPortion(portionMultiplier: Float): NutritionInfo {
        return NutritionInfo(
            calories = (calories * portionMultiplier).toInt(),
            protein = protein * portionMultiplier,
            carbs = carbs * portionMultiplier,
            fat = fat * portionMultiplier
        )
    }

    // Format nutrition values as strings
    fun getFormattedCalories(): String = "$calories kcal"
    fun getFormattedProtein(): String = String.format("%.1fg", protein)
    fun getFormattedCarbs(): String = String.format("%.1fg", carbs)
    fun getFormattedFat(): String = String.format("%.1fg", fat)
}