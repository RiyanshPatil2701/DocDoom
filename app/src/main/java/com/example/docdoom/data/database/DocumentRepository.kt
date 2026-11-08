
package com.example.docdoom.data.database

import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val documentDao: DocumentDao
) {

    val allDocuments: Flow<List<DocumentEntity>> =
        documentDao.getAllDocuments()

    val favoriteDocuments: Flow<List<DocumentEntity>> =
        documentDao.getFavoriteDocuments()

    val recentDocuments: Flow<List<DocumentEntity>> =
        documentDao.getRecentDocuments()

    suspend fun insertDocument(document: DocumentEntity): Long {
        return documentDao.insertDocumentAndMarkRecent(document)
    }

    suspend fun markAsRecent(documentId: Long) {
        documentDao.upsertRecentDocument(
            RecentDocumentEntity(
                documentId = documentId,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFromRecents(documentId: Long) {
        documentDao.removeFromRecents(documentId)
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }
}
