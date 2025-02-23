package com.example.nutrition_analysis

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.nutrition_analysis.databinding.ItemRecordBinding
import java.io.File

class RecordAdapter(private var records: List<NutritionRecord>) :
    RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]

        with(holder.binding) {
            // Set the nutrition details
            nutritionDetailsTextView.text = record.nutritionDetails

            // Set the image (if the path is valid)
            val imgFile = File(record.imagePath)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<NutritionRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    fun getCurrentRecords(): List<NutritionRecord> = records
}