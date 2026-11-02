package com.example.docdoom.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DocDoomDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: DocDoomDatabase? = null

        fun getDatabase(context: Context): DocDoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DocDoomDatabase::class.java,
                    "docdoom_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}