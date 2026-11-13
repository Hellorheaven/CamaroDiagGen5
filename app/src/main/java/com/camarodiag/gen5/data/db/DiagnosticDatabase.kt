package com.camarodiag.gen5.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diagnostic_sessions")
data class SavedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val vehicleLabel: String,
    val vin: String,
    val adapterName: String,
    val dtcSummary: String,
    val mileageSummary: String,
    val hasMileageDiscrepancy: Boolean,
    val maxDeltaKm: Double
)

@Dao
interface DiagnosticSessionDao {
    @Query("SELECT * FROM diagnostic_sessions ORDER BY timestamp DESC")
    fun observeAllSessions(): Flow<List<SavedSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SavedSessionEntity)

    @Delete
    suspend fun deleteSession(session: SavedSessionEntity)
}

@Database(entities = [SavedSessionEntity::class], version = 1, exportSchema = false)
abstract class DiagnosticDatabase : RoomDatabase() {
    abstract fun sessionDao(): DiagnosticSessionDao

    companion object {
        @Volatile
        private var INSTANCE: DiagnosticDatabase? = null

        fun getInstance(context: Context): DiagnosticDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiagnosticDatabase::class.java,
                    "camarodiag_gen5.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
