package com.example.nutrition_analysis

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nutrition_analysis.databinding.ActivityViewRecordsBinding

class ViewRecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewRecordsBinding
    private lateinit var adapter: RecordAdapter
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        loadRecords()
        setupClickListeners()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Saved Records"
        }
    }

    private fun setupRecyclerView() {
        adapter = RecordAdapter(emptyList())
        binding.recordsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ViewRecordsActivity)
            adapter = this@ViewRecordsActivity.adapter
        }
    }

    private fun loadRecords() {
        databaseHelper = DatabaseHelper(this)
        val records = databaseHelper.getAllRecords()
        adapter.updateRecords(records)

        // Show empty state if no records
        binding.emptyStateLayout.visibility = if (records.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.clearRecordsButton.setOnClickListener {
            clearAllRecords()
        }
    }

    private fun clearAllRecords() {
        val currentRecords = adapter.getCurrentRecords()
        if (currentRecords.isEmpty()) {
            Toast.makeText(this, "No records to clear", Toast.LENGTH_SHORT).show()
            return
        }

        databaseHelper.clearAllRecords()
        adapter.updateRecords(emptyList())
        binding.emptyStateLayout.visibility = android.view.View.VISIBLE
        Toast.makeText(this, "All records cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}