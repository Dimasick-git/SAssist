package dev.ryazha.sassist.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class LocalMessage(
    @PrimaryKey val id: String,
    val channel: String,
    val username: String,
    val text: String,
    val ts: Long,
    val isPending: Boolean = false,
    val isFailed: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY ts ASC")
    fun getMessages(channel: String): Flow<List<LocalMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: LocalMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<LocalMessage>)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM messages WHERE isPending = 1 ORDER BY ts ASC")
    suspend fun getPendingMessages(): List<LocalMessage>
}

@Database(entities = [LocalMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sassist_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
