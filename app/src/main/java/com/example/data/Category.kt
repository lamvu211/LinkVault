package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val logo: String, // Identifier for the default minimalist logo (e.g. "def_work_1")
    val timestamp: Long = System.currentTimeMillis(),
    val displayOrder: Int = 0
)
