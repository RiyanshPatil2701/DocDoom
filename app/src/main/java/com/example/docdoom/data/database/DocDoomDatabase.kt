
package com.example.docdoom.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        RecentDocumentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DocDoomDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {

        @Volatile
        private var INSTANCE: DocDoomDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recent_documents (
                        documentId INTEGER NOT NULL,
                        lastOpenedAt INTEGER NOT NULL,
                        PRIMARY KEY(documentId),
                        FOREIGN KEY(documentId)
                            REFERENCES documents(id)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_recent_documents_lastOpenedAt
                    ON recent_documents(lastOpenedAt)
                    """.trimIndent()
                )

                // Preserve existing documents in the old Recent list.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO recent_documents (
                        documentId,
                        lastOpenedAt
                    )
                    SELECT id, createdAt
                    FROM documents
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): DocDoomDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocDoomDatabase::class.java,
                    "docdoom_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { database ->
                        INSTANCE = database
                    }
            }
        }
    }
}
