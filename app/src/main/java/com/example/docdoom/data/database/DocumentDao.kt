
package com.example.docdoom.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentDocument(recentDocument: RecentDocumentEntity)

    @Query("DELETE FROM recent_documents WHERE documentId = :documentId")
    suspend fun removeFromRecents(documentId: Long)

    @Transaction
    suspend fun insertDocumentAndMarkRecent(
        document: DocumentEntity
    ): Long {
        val documentId = insertDocument(document)

        upsertRecentDocument(
            RecentDocumentEntity(
                documentId = documentId,
                lastOpenedAt = System.currentTimeMillis()
            )
        )

        return documentId
    }

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE isFavorite = 1
        ORDER BY createdAt DESC
        """
    )
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT documents.*
        FROM documents
        INNER JOIN recent_documents
        ON documents.id = recent_documents.documentId
        ORDER BY recent_documents.lastOpenedAt DESC
        LIMIT 50
        """
    )
    fun getRecentDocuments(): Flow<List<DocumentEntity>>
}
