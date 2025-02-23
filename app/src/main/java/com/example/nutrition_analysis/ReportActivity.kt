package com.example.nutrition_analysis

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nutrition_analysis.databinding.ActivityReportBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private lateinit var databaseHelper: DatabaseHelper
    private var currentView = VIEW_WEEK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        loadTodayStats()
        updateChartData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Nutrition Report"
        }
    }

    private fun setupUI() {
        databaseHelper = DatabaseHelper(this)

        // Setup time period toggles
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentView = when (checkedId) {
                    R.id.weekButton -> VIEW_WEEK
                    R.id.monthButton -> VIEW_MONTH
                    R.id.yearButton -> VIEW_YEAR
                    else -> VIEW_WEEK
                }
                updateChartData()
            }
        }

        // Setup chart
        setupBarChart()
    }

    private fun setupBarChart() {
        binding.calorieChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setScaleEnabled(false)

            // X-axis setup
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
            }

            // Y-axis setup
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
            }
            axisRight.isEnabled = false

            // Animation
            animateY(1000)
        }
    }

    private fun loadTodayStats() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dailyGoals = databaseHelper.getDailyGoals(today)
        val todayCalories = databaseHelper.getDailyCalories(today)

        // Update progress indicators
        binding.calorieProgress.apply {
            progress = ((todayCalories.toFloat() / (dailyGoals?.calorieGoal ?: 2000)) * 100).toInt()
            max = 100
        }

        binding.calorieText.text = "$todayCalories kcal"
        binding.goalText.text = "Goal: ${dailyGoals?.calorieGoal ?: 2000} kcal"
    }

    private fun updateChartData() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val endDate = dateFormat.format(calendar.time)

        // Set start date based on view period
        calendar.add(when (currentView) {
            VIEW_WEEK -> Calendar.DAY_OF_YEAR
            VIEW_MONTH -> Calendar.MONTH
            else -> Calendar.YEAR
        }, -1)
        val startDate = dateFormat.format(calendar.time)

        // Get data from database
        val entries = databaseHelper.getWeeklyEntries(startDate, endDate)

        // Create chart entries
        val barEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        var index = 0f
        entries.forEach { (date, foodEntries) ->
            val totalCalories = foodEntries.sumOf { it.calories }
            barEntries.add(BarEntry(index, totalCalories.toFloat()))
            labels.add(formatDate(date, currentView))
            index++
        }

        // Create dataset
        val dataSet = BarDataSet(barEntries, "Calories").apply {
            color = ContextCompat.getColor(this@ReportActivity, R.color.md_theme_primary)
            valueTextColor = ContextCompat.getColor(this@ReportActivity, R.color.md_theme_onSurface)
            valueTextSize = 12f
        }

        // Update chart
        binding.calorieChart.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value.toInt() < labels.size) labels[value.toInt()] else ""
                }
            }
            invalidate()
        }
    }

    private fun formatDate(dateString: String, viewPeriod: Int): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateString) ?: return dateString

        return when (viewPeriod) {
            VIEW_WEEK -> SimpleDateFormat("EEE", Locale.getDefault())
            VIEW_MONTH -> SimpleDateFormat("dd", Locale.getDefault())
            VIEW_YEAR -> SimpleDateFormat("MMM", Locale.getDefault())
            else -> SimpleDateFormat("dd", Locale.getDefault())
        }.format(date)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val VIEW_WEEK = 0
        private const val VIEW_MONTH = 1
        private const val VIEW_YEAR = 2
    }
}