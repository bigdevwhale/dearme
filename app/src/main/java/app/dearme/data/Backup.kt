package app.dearme.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * One file that carries every capsule to the next phone: a zip with the records and media, scrambled so that
 * unzipping it doesn't spoil unopened letters. The key ships with the app — honest about being a lock on a diary,
 * not a safe.
 */
object Backup {
    private const val VERSION = 1
    private val MAGIC = "DEARME1".toByteArray()
    private const val MANIFEST = "dearme.json"
    private const val MEDIA = "media/"

    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("dearme:letters-to-the-future".toByteArray()), "AES")

    private fun cipher(mode: Int, iv: ByteArray) = Cipher.getInstance("AES/CTR/NoPadding").apply { init(mode, key, IvParameterSpec(iv)) }

    fun write(context: Context, s: Snapshot, out: OutputStream) {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        out.write(MAGIC)
        out.write(iv)
        val zip = ZipOutputStream(CipherOutputStream(out, cipher(Cipher.ENCRYPT_MODE, iv)))
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(encode(s).toByteArray())
        zip.closeEntry()
        val files = s.photos.flatMap { it.files } + s.capsules.mapNotNull { it.voice }
        files.distinct().forEach { name ->
            val f = Media.file(context, name)
            if (!f.exists()) return@forEach
            zip.putNextEntry(ZipEntry(MEDIA + name))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.close()
    }

    /** Unpacks media into a staging folder and returns the records; throws [IllegalArgumentException] for a foreign file. */
    fun read(context: Context, input: InputStream): Pair<Snapshot, File> {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        runCatching { data.readFully(magic) }
        require(magic.contentEquals(MAGIC)) { "Not a DearMe backup" }
        val iv = ByteArray(16).also { data.readFully(it) }
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var snapshot: Snapshot? = null
        ZipInputStream(CipherInputStream(data, cipher(Cipher.DECRYPT_MODE, iv))).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == MANIFEST -> snapshot = decode(zip.readBytes().decodeToString())
                    e.name.startsWith(MEDIA) -> {
                        val name = File(e.name.removePrefix(MEDIA)).name
                        if (name.isNotBlank()) File(staging, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        return (snapshot ?: throw IllegalArgumentException("Not a DearMe backup")) to staging
    }

    /** Swaps the staged media in; files of the old capsules are removed. */
    fun commitMedia(context: Context, staging: File) {
        val dir = Media.dir(context)
        dir.listFiles()?.forEach { it.delete() }
        staging.listFiles()?.forEach { f -> f.copyTo(File(dir, f.name), overwrite = true) }
        staging.deleteRecursively()
    }

    fun encode(s: Snapshot): String = JSONObject()
        .put("app", "dearme")
        .put("version", VERSION)
        .put("capsules", JSONArray(s.capsules.map {
            JSONObject().put("id", it.id).put("kind", it.kind.name).put("createdAt", it.createdAt).put("openAt", it.openAt)
                .put("openedAt", it.openedAt ?: JSONObject.NULL).put("notified", it.notified).put("text", it.text).put("prompt", it.prompt)
                .put("voice", it.voice ?: JSONObject.NULL).put("voiceMs", it.voiceMs).put("ritualId", it.ritualId ?: JSONObject.NULL)
                .put("lateMs", it.lateMs).put("replyTo", it.replyTo ?: JSONObject.NULL)
        }))
        .put("photos", JSONArray(s.photos.map {
            JSONObject().put("id", it.id).put("capsuleId", it.capsuleId).put("back", it.back ?: JSONObject.NULL)
                .put("front", it.front ?: JSONObject.NULL).put("takenAt", it.takenAt).put("role", it.role)
        }))
        .put("rituals", JSONArray(s.rituals.map {
            JSONObject().put("id", it.id).put("name", it.name).put("enabled", it.enabled).put("timing", it.timing.name)
                .put("windowStart", it.windowStart).put("windowEnd", it.windowEnd).put("at", it.at).put("repeat", it.repeat.name)
                .put("days", it.days).put("monthDay", it.monthDay).put("month", it.month).put("photo", it.photo.name)
                .put("textMode", it.textMode.name).put("voice", it.voice).put("usePrompt", it.usePrompt).put("prompt", it.prompt)
                .put("timer", it.timer).put("lockDays", it.lockDays).put("createdAt", it.createdAt)
        }))
        .put("calls", JSONArray(s.calls.map {
            JSONObject().put("id", it.id).put("ritualId", it.ritualId).put("firedAt", it.firedAt).put("deadline", it.deadline)
                .put("capsuleId", it.capsuleId ?: JSONObject.NULL).put("dismissed", it.dismissed)
        }))
        .toString()

    fun decode(text: String): Snapshot {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "dearme") { "Not a DearMe backup" }
        val c = Converters()
        return Snapshot(
            capsules = root.list("capsules") {
                Capsule(
                    id = getLong("id"), kind = c.kind(getString("kind")), createdAt = getLong("createdAt"), openAt = getLong("openAt"),
                    openedAt = longOrNull("openedAt"), notified = optBoolean("notified"), text = optString("text"), prompt = optString("prompt"),
                    voice = stringOrNull("voice"), voiceMs = optLong("voiceMs"), ritualId = longOrNull("ritualId"),
                    lateMs = optLong("lateMs"), replyTo = longOrNull("replyTo"),
                )
            },
            photos = root.list("photos") {
                Photo(getLong("id"), getLong("capsuleId"), stringOrNull("back"), stringOrNull("front"), getLong("takenAt"), optInt("role"))
            },
            rituals = root.list("rituals") {
                Ritual(
                    id = getLong("id"), name = getString("name"), enabled = optBoolean("enabled", true), timing = c.timing(getString("timing")),
                    windowStart = getInt("windowStart"), windowEnd = getInt("windowEnd"), at = getInt("at"), repeat = c.repeat(getString("repeat")),
                    days = getInt("days"), monthDay = getInt("monthDay"), month = getInt("month"), photo = c.photo(getString("photo")),
                    textMode = c.text(getString("textMode")), voice = optBoolean("voice"), usePrompt = optBoolean("usePrompt"),
                    prompt = optString("prompt"), timer = optBoolean("timer", true), lockDays = getInt("lockDays"), createdAt = getLong("createdAt"),
                )
            },
            calls = root.list("calls") {
                Call(getLong("id"), getLong("ritualId"), getLong("firedAt"), getLong("deadline"), longOrNull("capsuleId"), optBoolean("dismissed"))
            },
        )
    }

    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.stringOrNull(k: String): String? = if (isNull(k)) null else getString(k)

    private fun <T> JSONObject.list(key: String, read: JSONObject.() -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).read() }
    }
}
