package com.dusrel.notes.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val type: String = "NOTE",
    val folderId: Long? = null,
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val isPrivate: Boolean = false,
    val reminderTime: Long? = null,
    val doodleData: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val richTextSpans: String = "",
    val encryptedTitle: String = "",
    val encryptedContent: String = "",
    val encryptedRichTextSpans: String = "",
    val isEncrypted: Boolean = false,
    val reminderExact: Boolean = false,
)

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long = 0,
    val text: String = "",
    val done: Boolean = false,
    val position: Int = 0,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val createdAt: Long = 0,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    suspend fun search(q: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE noteId = :noteId ORDER BY position ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE noteId = :noteId ORDER BY position ASC, id ASC")
    suspend fun getForNote(noteId: Long): List<TodoEntity>

    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity): Long

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todos WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    suspend fun getAll(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

/** v1 -> v2: rich-text spans + encrypted private-note columns. App is unreleased, but a real migration (not destructive) is kept. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN richTextSpans TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN encryptedTitle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN encryptedContent TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
    }
}

/** v2 -> v3: encrypted spans column (spans JSON contains body text, so it must
 *  not stay plaintext for private notes) + persisted reminder-path flag. */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN encryptedRichTextSpans TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN reminderExact INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [NoteEntity::class, TodoEntity::class, FolderEntity::class], version = 3, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun todoDao(): TodoDao
    abstract fun folderDao(): FolderDao
}
