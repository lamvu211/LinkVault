package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedLink::class, Category::class], version = 2, exportSchema = false)
abstract class LinkDatabase : RoomDatabase() {
    abstract val savedLinkDao: SavedLinkDao
    abstract val categoryDao: CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: LinkDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val instances = mutableMapOf<String, LinkDatabase>()

        fun getDatabase(context: Context, userEmail: String): LinkDatabase {
            val dbName = "link_vault_${userEmail.hashCode()}_db"
            return instances.getOrPut(dbName) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LinkDatabase::class.java,
                    if (userEmail.isEmpty()) "link_vault_database" else dbName
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(false)
                .build()
            }
        }
    }
}
