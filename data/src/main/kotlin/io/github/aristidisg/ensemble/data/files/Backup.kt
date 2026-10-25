package io.github.aristidisg.ensemble.data.files

import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.repo.DataJson
import io.github.aristidisg.ensemble.data.repo.Docs
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.model.CheckIn
import io.github.aristidisg.ensemble.model.CustomRule
import io.github.aristidisg.ensemble.model.DayPlan
import io.github.aristidisg.ensemble.model.FeedbackEvent
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Trip
import io.github.aristidisg.ensemble.model.WearLog
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class BackupManifest(val schemaVersion: Int, val appVersion: String, val exportedAtMillis: Long)

@Serializable
data class BackupData(
    val settings: Settings,
    val items: List<Item>,
    val outfits: List<Outfit>,
    val events: List<FeedbackEvent>,
    val wearLogs: List<WearLog>,
    val checkIns: List<CheckIn>,
    val plans: List<DayPlan>,
    val trips: List<Trip>,
    val looks: List<InspirationLook>,
    val rules: List<CustomRule>,
    val scenarios: List<Scenario>,
)

data class BackupPreview(val manifest: BackupManifest, val counts: Map<String, Int>)

/**
 * Export: a .zip with manifest.json, data.json and the images. Import: merge or replace, with a
 * preview of the counts first. Streams come from the system file picker (no storage permission).
 */
class Backup(
    private val db: EnsembleDatabase,
    private val images: ImageStore,
    private val settings: SettingsRepository,
    private val feedback: FeedbackRepository,
) {
    suspend fun export(out: OutputStream, appVersion: String, now: Long) = withContext(Dispatchers.IO) {
        val data = BackupData(
            settings = settings.current(),
            items = db.items().all().map(Docs::item),
            outfits = db.outfits().all().map(Docs::outfit),
            events = feedback.events(),
            wearLogs = db.wear().all().map(Docs::wear),
            checkIns = db.wear().allCheckIns().map(Docs::checkIn),
            plans = db.plans().allPlans().map(Docs::plan),
            trips = db.plans().allTrips().map(Docs::trip),
            looks = db.looks().all().map(Docs::look),
            rules = db.rules().allRules().map(Docs::rule),
            scenarios = db.rules().allScenarios().map(Docs::scenario),
        )
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(DataJson.encodeToString(BackupManifest.serializer(), BackupManifest(SCHEMA, appVersion, now)).encodeToByteArray())
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(DataJson.encodeToString(BackupData.serializer(), data).encodeToByteArray())
            for (f in images.allImageFiles()) {
                zip.putNextEntry(ZipEntry("images/" + images.relative(f)))
                f.inputStream().use { it.copyTo(zip) }
            }
        }
    }

    /** Reads only the manifest and data counts. */
    fun preview(input: InputStream): BackupPreview {
        var manifest: BackupManifest? = null; var data: BackupData? = null
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                when (e.name) {
                    "manifest.json" -> manifest = DataJson.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                    "data.json" -> data = DataJson.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                }
            }
        }
        val d = data ?: error("Not an Ensemble backup")
        return BackupPreview(manifest ?: BackupManifest(SCHEMA, "?", 0), counts(d))
    }

    fun counts(d: BackupData) = linkedMapOf(
        "Pieces" to d.items.size, "Outfits" to d.outfits.size, "Feedback events" to d.events.size,
        "Wear log" to d.wearLogs.size, "Plans" to d.plans.size, "Trips" to d.trips.size,
        "Inspiration looks" to d.looks.size, "Custom rules" to d.rules.size,
    )

    suspend fun import(input: InputStream, replace: Boolean) = withContext(Dispatchers.IO) {
        var data: BackupData? = null
        val staging = File(images.root, "import-staging").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                when {
                    e.name == "data.json" -> data = DataJson.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                    e.name.startsWith("images/") && !e.isDirectory && !e.name.contains("..") -> {
                        val target = File(staging, e.name.removePrefix("images/"))
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val d = data ?: error("Not an Ensemble backup")
        if (replace) { db.clearAllTables(); feedback.invalidate() }
        db.withTransaction {
            db.items().upsertAll(d.items.map(Docs::item))
            db.outfits().upsertAll(d.outfits.map(Docs::outfit))
            db.wear().upsertAll(d.wearLogs.map(Docs::wear))
            db.wear().upsertCheckIns(d.checkIns.map(Docs::checkIn))
            db.plans().upsertPlans(d.plans.map(Docs::plan))
            db.plans().upsertTrips(d.trips.map(Docs::trip))
            db.looks().upsertAll(d.looks.map(Docs::look))
            db.rules().upsertRules(d.rules.map(Docs::rule))
            db.rules().upsertScenarios(d.scenarios.map(Docs::scenario))
            feedback.insertAll(d.events)
        }
        if (replace) { images.clear(); settings.replace(d.settings) }
        staging.walkTopDown().filter { it.isFile }.forEach { f ->
            val dest = File(images.root, f.relativeTo(staging).path)
            dest.parentFile?.mkdirs(); f.copyTo(dest, overwrite = true)
        }
        staging.deleteRecursively()
        val s = settings.current()
        feedback.rebuild(s.learningEpoch, LearningConfig.of(s))
    }

    /** "Delete all data" after a typed confirmation. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        feedback.invalidate()
        images.clear()
        settings.replace(Settings())
    }

    companion object {
        const val SCHEMA = 1
    }
}
