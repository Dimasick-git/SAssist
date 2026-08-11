package dev.ryazha.sassist.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class LocalMessage(
    @PrimaryKey val id: String,        // server id, or "local_<clientId>" while pending
    val clientId: String? = null,
    val channel: String,
    val userId: String = "",
    val username: String,
    val handle: String = "",
    val premium: Boolean = false,
    val color: String = "5865F2",
    val text: String,
    val ts: Long,
    val mediaJson: String? = null,     // MediaRef as JSON (incl. durationMs)
    val localMediaUri: String? = null, // Local URI for pending uploads
    val replyTo: String? = null,
    val reactionsJson: String? = null, // Map<emoji, List<userId>> as JSON
    val readByJson: String? = null,    // List<userId> who read this message
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
    val attempts: Int = 0
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

    @Query("DELETE FROM messages WHERE clientId = :clientId AND (isPending = 1 OR isFailed = 1)")
    suspend fun deleteUnsentByClientId(clientId: String)

    /** Server echo arrived: replace the optimistic local row with the server copy. */
    @Transaction
    suspend fun reconcile(clientId: String, serverMsg: LocalMessage) {
        deleteUnsentByClientId(clientId)
        insert(serverMsg)
    }

    @Query("SELECT * FROM messages WHERE isPending = 1 ORDER BY ts ASC")
    suspend fun getPendingMessages(): List<LocalMessage>

    @Query("SELECT * FROM messages WHERE channel = :channel AND isPending = 1 ORDER BY ts ASC")
    suspend fun pendingInChannel(channel: String): List<LocalMessage>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalMessage?

    @Query("SELECT MAX(ts) FROM messages WHERE channel = :channel AND isPending = 0 AND isFailed = 0")
    suspend fun latestServerTs(channel: String): Long?

    @Query("UPDATE messages SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: String)

    @Query("UPDATE messages SET isFailed = 1, isPending = 0 WHERE id = :id")
    suspend fun markFailed(id: String)

    @Query("UPDATE messages SET isFailed = 0, isPending = 1, attempts = 0, ts = :newTs WHERE id = :id")
    suspend fun markRetrying(id: String, newTs: Long)

    @Query("UPDATE messages SET reactionsJson = :reactions WHERE id = :id")
    suspend fun updateReactions(id: String, reactions: String?)

    @Query("UPDATE messages SET readByJson = :json WHERE id = :id")
    suspend fun updateReadBy(id: String, json: String?)

    @Query("SELECT * FROM messages WHERE channel = :channel AND isPending = 0 AND isFailed = 0")
    suspend fun serverRowsIn(channel: String): List<LocalMessage>

    @Query("DELETE FROM messages WHERE channel = :channel")
    suspend fun clearChannel(channel: String)
}

@Database(entities = [LocalMessage::class], version = 3, exportSchema = false)
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
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
