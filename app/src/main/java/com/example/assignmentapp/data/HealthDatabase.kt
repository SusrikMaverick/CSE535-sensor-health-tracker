package com.example.assignmentapp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface HealthRecordDao {
    @Insert
    suspend fun insert(record: HealthRecordEntity): Long

    @Query("SELECT COUNT(*) FROM health_records")
    suspend fun count(): Int

    @Query("SELECT * FROM health_records ORDER BY id ASC")
    suspend fun getAll(): List<HealthRecordEntity>

    @Query("DELETE FROM health_records")
    suspend fun deleteAll(): Int
}

@Database(
    entities = [HealthRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthRecordDao(): HealthRecordDao

    companion object {
        @Volatile
        private var instance: HealthDatabase? = null

        fun getInstance(context: Context): HealthDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "health_database"
                ).build().also { instance = it }
            }
    }
}
