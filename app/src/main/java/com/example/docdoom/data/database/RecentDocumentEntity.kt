
package com.example.docdoom.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recent_documents",
    primaryKeys = ["documentId"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["lastOpenedAt"])
    ]
)
data class RecentDocumentEntity(
    val documentId: Long,
    val lastOpenedAt: Long
)
