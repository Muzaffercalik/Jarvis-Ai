package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteName: String,
    val username: String,
    val passwordString: String,
    val logoText: String = ""
)

@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandText: String,
    val intentType: String, // "OPEN_YOUTUBE", "SEARCH_GOOGLE", "LOGIN_WEBSITE", "TYPE_TEXT", "CLICK_ELEMENT", "UNKNOWN"
    val status: String,     // "Başarılı", "Simüle Ediliyor", "Başarısız"
    val timestamp: Long = System.currentTimeMillis(),
    val resultMessage: String
)

@Entity(tableName = "voice_shortcuts")
data class VoiceShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phrase: String,
    val targetAction: String,
    val targetUrl: String = ""
)

@Dao
interface JarvisDao {
    // Credentials
    @Query("SELECT * FROM credentials ORDER BY siteName ASC")
    fun getAllCredentials(): Flow<List<Credential>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: Long)

    // Command Logs
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLog)

    @Query("DELETE FROM command_logs")
    suspend fun clearLogs()

    // Voice Shortcuts
    @Query("SELECT * FROM voice_shortcuts ORDER BY name ASC")
    fun getAllShortcuts(): Flow<List<VoiceShortcut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: VoiceShortcut)

    @Query("DELETE FROM voice_shortcuts WHERE id = :id")
    suspend fun deleteShortcutById(id: Long)
}

@Database(entities = [Credential::class, CommandLog::class, VoiceShortcut::class], version = 1, exportSchema = false)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun dao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getDatabase(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class JarvisRepository(private val db: JarvisDatabase) {
    private val dao = db.dao()

    fun getCredentials(): Flow<List<Credential>> = dao.getAllCredentials()
    suspend fun insertCredential(credential: Credential) = dao.insertCredential(credential)
    suspend fun deleteCredential(id: Long) = dao.deleteCredentialById(id)

    fun getLogs(): Flow<List<CommandLog>> = dao.getAllLogs()
    suspend fun insertLog(log: CommandLog) = dao.insertLog(log)
    suspend fun clearLogs() = dao.clearLogs()

    fun getShortcuts(): Flow<List<VoiceShortcut>> = dao.getAllShortcuts()
    suspend fun insertShortcut(shortcut: VoiceShortcut) = dao.insertShortcut(shortcut)
    suspend fun deleteShortcut(id: Long) = dao.deleteShortcutById(id)
}
