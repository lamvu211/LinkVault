package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_links")
data class SavedLink(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val note: String,
    val tags: String, // Comma-separated list of tags, e.g. "Reading, Work"
    val categoryId: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
