package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "removed_apps")
data class RemovedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val versionName: String,
    val versionCode: Long,
    val removedAt: Long,
    val backupApkDir: String?,
    val apkCount: Int,
    val totalSizeBytes: Long
)

@Dao
interface RemovedAppDao {
    @Query("SELECT * FROM removed_apps ORDER BY removedAt DESC")
    fun observeAll(): Flow<List<RemovedAppEntity>>

    @Query("SELECT * FROM removed_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): RemovedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RemovedAppEntity)

    @Query("DELETE FROM removed_apps WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)
}

@Database(entities = [RemovedAppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun removedAppDao(): RemovedAppDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appforge_db"
                ).build().also { instance = it }
            }
        }
    }
}
