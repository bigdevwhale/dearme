package app.dearme.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A letter written on purpose, or a moment captured when a ritual called. */
enum class Kind { LETTER, MOMENT }

/** Which cameras a shot uses. ANY lets the user pick when shooting. */
enum class PhotoMode { NONE, BACK, FRONT, DUAL, ANY }

enum class TextMode { NONE, LINE, LETTER }

/** Random time inside a window, or the same time every time. */
enum class Timing { RANDOM, FIXED }

/** Which days a ritual calls on. */
enum class Repeat { WEEKLY, MONTHLY, YEARLY }

/**
 * One sealed message to the future self. Until [openAt] passes it can't be read;
 * [openedAt] is set once the seal is broken.
 */
@Entity(tableName = "capsules")
data class Capsule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Kind,
    val createdAt: Long,
    val openAt: Long,
    val openedAt: Long? = null,
    /** "Your capsule opened" was already shown. */
    val notified: Boolean = false,
    val text: String = "",
    /** The question the letter answers, if one was used. */
    val prompt: String = "",
    /** File name inside the media folder. */
    val voice: String? = null,
    val voiceMs: Long = 0,
    val ritualId: Long? = null,
    /** How late a moment was captured after its two minutes ran out; 0 when on time. */
    val lateMs: Long = 0,
    /** The opened capsule this one answers. */
    val replyTo: Long? = null,
) {
    fun isOpen() = openedAt != null
    fun isDue(now: Long) = openedAt == null && now >= openAt
}

/** A shot inside a capsule. A dual shot has both files; [role] 1 is the "now" shot taken after opening. */
@Entity(tableName = "photos", indices = [Index("capsuleId")])
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capsuleId: Long,
    val back: String? = null,
    val front: String? = null,
    val takenAt: Long,
    val role: Int = ROLE_THEN,
) {
    val files: List<String> get() = listOfNotNull(back, front)

    companion object {
        const val ROLE_THEN = 0
        const val ROLE_NOW = 1
    }
}

/** A rule that calls the user to make a capsule: when, how often, what goes in, and how long it stays sealed. */
@Entity(tableName = "rituals")
data class Ritual(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val timing: Timing = Timing.RANDOM,
    /** Random window, minutes after midnight. */
    val windowStart: Int = 10 * 60,
    val windowEnd: Int = 22 * 60,
    /** Fixed time, minutes after midnight. */
    val at: Int = 21 * 60,
    val repeat: Repeat = Repeat.WEEKLY,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = ALL_DAYS,
    /** Day of month for MONTHLY and YEARLY (1..31; clamped to short months). */
    val monthDay: Int = 1,
    /** Month for YEARLY, 1..12. */
    val month: Int = 1,
    val photo: PhotoMode = PhotoMode.DUAL,
    val textMode: TextMode = TextMode.LINE,
    val voice: Boolean = false,
    /** Show a question; [prompt] blank means a different one from the built-in set each time. */
    val usePrompt: Boolean = false,
    val prompt: String = "",
    /** Two minutes to answer, BeReal-style; late answers get a stamp. */
    val timer: Boolean = true,
    /** Days the capsule stays sealed; [LADDER] grows with how long the app has been used. */
    val lockDays: Int = LADDER,
    /** When it calls next (epoch ms); 0 when not planned yet. Kept so a random time isn't re-rolled on every start. */
    val nextAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ALL_DAYS = 0b1111111
        const val LADDER = -1
    }
}

/** One time a ritual called. [capsuleId] is set once it was answered. */
@Entity(tableName = "calls", indices = [Index("ritualId")])
data class Call(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ritualId: Long,
    val firedAt: Long,
    /** When the two minutes run out; equals [firedAt] when the ritual has no timer. */
    val deadline: Long,
    val capsuleId: Long? = null,
    val dismissed: Boolean = false,
)

class Converters {
    @TypeConverter fun kind(v: Kind): String = v.name
    @TypeConverter fun kind(v: String): Kind = runCatching { Kind.valueOf(v) }.getOrDefault(Kind.LETTER)
    @TypeConverter fun photo(v: PhotoMode): String = v.name
    @TypeConverter fun photo(v: String): PhotoMode = runCatching { PhotoMode.valueOf(v) }.getOrDefault(PhotoMode.ANY)
    @TypeConverter fun text(v: TextMode): String = v.name
    @TypeConverter fun text(v: String): TextMode = runCatching { TextMode.valueOf(v) }.getOrDefault(TextMode.LINE)
    @TypeConverter fun timing(v: Timing): String = v.name
    @TypeConverter fun timing(v: String): Timing = runCatching { Timing.valueOf(v) }.getOrDefault(Timing.RANDOM)
    @TypeConverter fun repeat(v: Repeat): String = v.name
    @TypeConverter fun repeat(v: String): Repeat = runCatching { Repeat.valueOf(v) }.getOrDefault(Repeat.WEEKLY)
}

class Snapshot(
    val capsules: List<Capsule>,
    val photos: List<Photo>,
    val rituals: List<Ritual>,
    val calls: List<Call>,
)

@Dao
abstract class DearDao {
    // Capsules
    @Query("SELECT * FROM capsules ORDER BY openAt")
    abstract fun observeCapsules(): Flow<List<Capsule>>

    @Query("SELECT * FROM capsules ORDER BY openAt")
    abstract suspend fun capsules(): List<Capsule>

    @Query("SELECT * FROM capsules WHERE id = :id")
    abstract fun observeCapsule(id: Long): Flow<Capsule?>

    @Query("SELECT * FROM capsules WHERE id = :id")
    abstract suspend fun capsule(id: Long): Capsule?

    /** The earliest capsule whose opening hasn't been announced yet, even if its time has already come. */
    @Query("SELECT MIN(openAt) FROM capsules WHERE openedAt IS NULL AND notified = 0")
    abstract suspend fun nextOpening(): Long?

    @Query("SELECT * FROM capsules WHERE openedAt IS NULL AND notified = 0 AND openAt <= :now ORDER BY openAt")
    abstract suspend fun dueUnnotified(now: Long): List<Capsule>

    @Query("SELECT MIN(createdAt) FROM capsules")
    abstract suspend fun firstCreated(): Long?

    @Insert
    abstract suspend fun insertCapsule(c: Capsule): Long

    @Update
    abstract suspend fun updateCapsule(c: Capsule)

    @Delete
    protected abstract suspend fun deleteCapsuleRow(c: Capsule)

    // Photos
    @Query("SELECT * FROM photos ORDER BY takenAt")
    abstract fun observePhotos(): Flow<List<Photo>>

    @Query("SELECT * FROM photos ORDER BY takenAt")
    abstract suspend fun photos(): List<Photo>

    @Query("SELECT * FROM photos WHERE capsuleId = :capsuleId ORDER BY takenAt")
    abstract fun observePhotosOf(capsuleId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE capsuleId = :capsuleId ORDER BY takenAt")
    abstract suspend fun photosOf(capsuleId: Long): List<Photo>

    @Insert
    abstract suspend fun insertPhoto(p: Photo): Long

    @Delete
    abstract suspend fun deletePhoto(p: Photo)

    @Query("DELETE FROM photos WHERE capsuleId = :capsuleId")
    protected abstract suspend fun deletePhotosOf(capsuleId: Long)

    // Rituals
    @Query("SELECT * FROM rituals ORDER BY createdAt")
    abstract fun observeRituals(): Flow<List<Ritual>>

    @Query("SELECT * FROM rituals ORDER BY createdAt")
    abstract suspend fun rituals(): List<Ritual>

    @Query("SELECT * FROM rituals WHERE id = :id")
    abstract suspend fun ritual(id: Long): Ritual?

    @Insert
    abstract suspend fun insertRitual(r: Ritual): Long

    @Update
    abstract suspend fun updateRitual(r: Ritual)

    @Delete
    abstract suspend fun deleteRitual(r: Ritual)

    // Calls
    @Query("SELECT * FROM calls ORDER BY firedAt")
    abstract fun observeCalls(): Flow<List<Call>>

    @Query("SELECT * FROM calls ORDER BY firedAt")
    abstract suspend fun calls(): List<Call>

    @Query("SELECT * FROM calls WHERE id = :id")
    abstract suspend fun call(id: Long): Call?

    @Query("SELECT MAX(firedAt) FROM calls WHERE ritualId = :ritualId")
    abstract suspend fun lastCall(ritualId: Long): Long?

    @Insert
    abstract suspend fun insertCall(c: Call): Long

    @Update
    abstract suspend fun updateCall(c: Call)

    /** Removes the capsule with its photos and returns the files to delete. */
    @Transaction
    open suspend fun deleteCapsule(c: Capsule): List<String> {
        val files = photosOf(c.id).flatMap { it.files } + listOfNotNull(c.voice)
        deletePhotosOf(c.id)
        deleteCapsuleRow(c)
        return files
    }

    // Backup
    @Transaction
    open suspend fun snapshot() = Snapshot(capsules(), photos(), rituals(), calls())

    @Query("DELETE FROM capsules") protected abstract suspend fun clearCapsules()
    @Query("DELETE FROM photos") protected abstract suspend fun clearPhotos()
    @Query("DELETE FROM rituals") protected abstract suspend fun clearRituals()
    @Query("DELETE FROM calls") protected abstract suspend fun clearCalls()

    @Insert protected abstract suspend fun insertCapsules(l: List<Capsule>)
    @Insert protected abstract suspend fun insertPhotos(l: List<Photo>)
    @Insert protected abstract suspend fun insertRituals(l: List<Ritual>)
    @Insert protected abstract suspend fun insertCalls(l: List<Call>)

    @Transaction
    open suspend fun replaceAll(s: Snapshot) {
        clearCapsules(); clearPhotos(); clearRituals(); clearCalls()
        insertCapsules(s.capsules); insertPhotos(s.photos); insertRituals(s.rituals); insertCalls(s.calls)
    }
}

@Database(
    entities = [Capsule::class, Photo::class, Ritual::class, Call::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DearDatabase : RoomDatabase() {
    abstract fun dao(): DearDao

    companion object {
        fun create(context: Context): DearDatabase =
            Room.databaseBuilder(context.applicationContext, DearDatabase::class.java, "dearme.db").build()
    }
}
