package com.example.nutrition_analysis

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // Create food entries table
        val createFoodEntriesTable = """
            CREATE TABLE $TABLE_FOOD_ENTRIES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_MEAL_TYPE TEXT NOT NULL,
                $COLUMN_IMAGE_PATH TEXT,
                $COLUMN_FOOD_NAME TEXT NOT NULL,
                $COLUMN_PORTION_SIZE REAL NOT NULL,
                $COLUMN_CALORIES INTEGER NOT NULL,
                $COLUMN_PROTEIN REAL,
                $COLUMN_CARBS REAL,
                $COLUMN_FAT REAL,
                $COLUMN_NUTRITION_DETAILS TEXT
            )
        """.trimIndent()

        // Create daily goals table
        val createDailyGoalsTable = """
            CREATE TABLE $TABLE_DAILY_GOALS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DATE TEXT UNIQUE NOT NULL,
                $COLUMN_CALORIE_GOAL INTEGER NOT NULL,
                $COLUMN_PROTEIN_GOAL REAL,
                $COLUMN_CARBS_GOAL REAL,
                $COLUMN_FAT_GOAL REAL
            )
        """.trimIndent()

        db.execSQL(createFoodEntriesTable)
        db.execSQL(createDailyGoalsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FOOD_ENTRIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DAILY_GOALS")
        onCreate(db)
    }

    fun insertFoodEntry(
        date: String,
        mealType: String,
        imagePath: String?,
        foodName: String,
        portionSize: Float,
        calories: Int,
        protein: Float?,
        carbs: Float?,
        fat: Float?,
        nutritionDetails: String?
    ): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, date)
            put(COLUMN_MEAL_TYPE, mealType)
            put(COLUMN_IMAGE_PATH, imagePath)
            put(COLUMN_FOOD_NAME, foodName)
            put(COLUMN_PORTION_SIZE, portionSize)
            put(COLUMN_CALORIES, calories)
            put(COLUMN_PROTEIN, protein)
            put(COLUMN_CARBS, carbs)
            put(COLUMN_FAT, fat)
            put(COLUMN_NUTRITION_DETAILS, nutritionDetails)
        }
        return db.insert(TABLE_FOOD_ENTRIES, null, values)
    }

    fun getAllRecords(): List<NutritionRecord> {
        val records = mutableListOf<NutritionRecord>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_FOOD_ENTRIES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_ID DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                records.add(NutritionRecord(
                    id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    nutritionDetails = buildNutritionDetails(
                        foodName = it.getString(it.getColumnIndexOrThrow(COLUMN_FOOD_NAME)),
                        calories = it.getInt(it.getColumnIndexOrThrow(COLUMN_CALORIES)),
                        mealType = it.getString(it.getColumnIndexOrThrow(COLUMN_MEAL_TYPE)),
                        date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                    )
                ))
            }
        }

        return records
    }

    private fun buildNutritionDetails(
        foodName: String,
        calories: Int,
        mealType: String,
        date: String
    ): String {
        return """
            $foodName
            Calories: $calories
            Meal: $mealType
            Date: $date
        """.trimIndent()
    }

    fun clearAllRecords() {
        val db = this.writableDatabase
        db.delete(TABLE_FOOD_ENTRIES, null, null)
        db.close()
    }

    fun getDailyEntries(date: String): List<FoodEntry> {
        val entries = mutableListOf<FoodEntry>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_FOOD_ENTRIES,
            null,
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null,
            null,
            "$COLUMN_ID DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                entries.add(FoodEntry(
                    id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE)),
                    mealType = it.getString(it.getColumnIndexOrThrow(COLUMN_MEAL_TYPE)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    foodName = it.getString(it.getColumnIndexOrThrow(COLUMN_FOOD_NAME)),
                    portionSize = it.getFloat(it.getColumnIndexOrThrow(COLUMN_PORTION_SIZE)),
                    calories = it.getInt(it.getColumnIndexOrThrow(COLUMN_CALORIES)),
                    protein = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_PROTEIN)),
                    carbs = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_CARBS)),
                    fat = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_FAT)),
                    nutritionDetails = it.getString(it.getColumnIndexOrThrow(COLUMN_NUTRITION_DETAILS))
                ))
            }
        }
        return entries
    }

    fun getDailyCalories(date: String): Int {
        val db = this.readableDatabase
        var totalCalories = 0

        val cursor = db.query(
            TABLE_FOOD_ENTRIES,
            arrayOf("SUM($COLUMN_CALORIES) as total"),
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                totalCalories = it.getInt(0)
            }
        }
        return totalCalories
    }

    fun getWeeklyEntries(startDate: String, endDate: String): Map<String, List<FoodEntry>> {
        val entriesByDate = mutableMapOf<String, MutableList<FoodEntry>>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_FOOD_ENTRIES,
            null,
            "$COLUMN_DATE BETWEEN ? AND ?",
            arrayOf(startDate, endDate),
            null,
            null,
            "$COLUMN_DATE ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE))
                val entry = FoodEntry(
                    id = it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
                    date = date,
                    mealType = it.getString(it.getColumnIndexOrThrow(COLUMN_MEAL_TYPE)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    foodName = it.getString(it.getColumnIndexOrThrow(COLUMN_FOOD_NAME)),
                    portionSize = it.getFloat(it.getColumnIndexOrThrow(COLUMN_PORTION_SIZE)),
                    calories = it.getInt(it.getColumnIndexOrThrow(COLUMN_CALORIES)),
                    protein = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_PROTEIN)),
                    carbs = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_CARBS)),
                    fat = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_FAT)),
                    nutritionDetails = it.getString(it.getColumnIndexOrThrow(COLUMN_NUTRITION_DETAILS))
                )

                if (!entriesByDate.containsKey(date)) {
                    entriesByDate[date] = mutableListOf()
                }
                entriesByDate[date]?.add(entry)
            }
        }
        return entriesByDate
    }

    fun updateDailyGoals(
        date: String,
        calorieGoal: Int,
        proteinGoal: Float? = null,
        carbsGoal: Float? = null,
        fatGoal: Float? = null
    ): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, date)
            put(COLUMN_CALORIE_GOAL, calorieGoal)
            proteinGoal?.let { put(COLUMN_PROTEIN_GOAL, it) }
            carbsGoal?.let { put(COLUMN_CARBS_GOAL, it) }
            fatGoal?.let { put(COLUMN_FAT_GOAL, it) }
        }

        val result = db.insertWithOnConflict(
            TABLE_DAILY_GOALS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return result != -1L
    }

    fun getDailyGoals(date: String): DailyGoals? {
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_DAILY_GOALS,
            null,
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                DailyGoals(
                    date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE)),
                    calorieGoal = it.getInt(it.getColumnIndexOrThrow(COLUMN_CALORIE_GOAL)),
                    proteinGoal = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_PROTEIN_GOAL)),
                    carbsGoal = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_CARBS_GOAL)),
                    fatGoal = it.getFloatOrNull(it.getColumnIndexOrThrow(COLUMN_FAT_GOAL))
                )
            } else null
        }
    }

    // Helper extension function to safely get nullable Float from cursor
    private fun Cursor.getFloatOrNull(columnIndex: Int): Float? {
        return if (isNull(columnIndex)) null else getFloat(columnIndex)
    }

    companion object {
        private const val DATABASE_NAME = "NutritionDB"
        private const val DATABASE_VERSION = 2

        private const val TABLE_FOOD_ENTRIES = "food_entries"
        private const val TABLE_DAILY_GOALS = "daily_goals"

        private const val COLUMN_ID = "id"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_MEAL_TYPE = "meal_type"
        private const val COLUMN_IMAGE_PATH = "image_path"
        private const val COLUMN_FOOD_NAME = "food_name"
        private const val COLUMN_PORTION_SIZE = "portion_size"
        private const val COLUMN_CALORIES = "calories"
        private const val COLUMN_PROTEIN = "protein"
        private const val COLUMN_CARBS = "carbs"
        private const val COLUMN_FAT = "fat"
        private const val COLUMN_NUTRITION_DETAILS = "nutrition_details"
        private const val COLUMN_CALORIE_GOAL = "calorie_goal"
        private const val COLUMN_PROTEIN_GOAL = "protein_goal"
        private const val COLUMN_CARBS_GOAL = "carbs_goal"
        private const val COLUMN_FAT_GOAL = "fat_goal"
    }
}

data class FoodEntry(
    val id: Int,
    val date: String,
    val mealType: String,
    val imagePath: String?,
    val foodName: String,
    val portionSize: Float,
    val calories: Int,
    val protein: Float?,
    val carbs: Float?,
    val fat: Float?,
    val nutritionDetails: String?
)

data class DailyGoals(
    val date: String,
    val calorieGoal: Int,
    val proteinGoal: Float?,
    val carbsGoal: Float?,
    val fatGoal: Float?
)
