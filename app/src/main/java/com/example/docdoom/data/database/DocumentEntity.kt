package com.example.docdoom.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val displayName: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val isFavorite: Boolean = false
)