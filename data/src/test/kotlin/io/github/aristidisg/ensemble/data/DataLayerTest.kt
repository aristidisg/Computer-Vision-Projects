package io.github.aristidisg.ensemble.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.files.Backup
import io.github.aristidisg.ensemble.data.files.ImageStore
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.RuleRepository
import io.github.aristidisg.ensemble.data.repo.DataJson
import io.github.aristidisg.ensemble.data.repo.DeckCardLog
import io.github.aristidisg.ensemble.data.repo.DeckLog
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.weather.OpenMeteoClient
import io.github.aristidisg.ensemble.engine.DemoWardrobe
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ContextSnapshot
import io.github.aristidisg.ensemble.model.FeedbackEvent
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.LatLon
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.ShoppingEntry
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataLayerTest {
    private lateinit var context: Context
    private lateinit var db: EnsembleDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, EnsembleDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun demo() = DemoWardrobe.parse(javaClass.getResource("/demo_wardrobe.json")?.readText() ?: context.assets.open("demo_wardrobe.json").bufferedReader().readText())

    @Test fun itemsRoundTripThroughRoom() = runTest {
        val repo = WardrobeRepository(db)
        val items = demo()
        repo.upsertAll(items, now = 1000)
        val loaded = repo.items.first()
        assertEquals(items.size, loaded.size)
        assertEquals(items.first { it.id == "oxford-white" }.copy(updatedAtMillis = 1000), loaded.first { it.id == "oxford-white" })
        repo.delete("oxford-white")
        assertEquals(items.size - 1, repo.count())
    }

    @Test fun outfitsAreFoundBySignature() = runTest {
        val repo = OutfitRepository(db)
        val o = Outfit("o1", listOf(OutfitPiece("a", Slot.TOP), OutfitPiece("b", Slot.BOTTOM)))
        repo.save(o)
        assertEquals(o, repo.bySignature(Outfit.signatureOf(listOf("b", "a"))))
    }

    @Test fun feedbackStatsEqualTheirRebuild() = runTest {
        val repo = FeedbackRepository(db)
        val cfg = LearningConfig()
        repeat(5) { i ->
            repo.record(FeedbackEvent("e$i", 1000L + i, 20000, FeedbackType.SWIPE_YES, "s", emptyList(),
                context = ContextSnapshot("casual", Season.AUTUMN, "12-16", 3, 12.0, 16.0), features = listOf("item:a", "fam:NAVY|WHITE")), cfg)
        }
        val incremental = repo.currentStats()
        repo.rebuild(0, cfg)
        assertEquals(incremental, repo.currentStats())
        // The in-memory copy always equals what is stored.
        assertEquals(repo.currentStats(), FeedbackRepository(db).currentStats())
        repo.hardReset()
        assertEquals(FeedbackRepository(db).currentStats(), repo.currentStats())
        assertTrue(repo.events().isEmpty())
    }

    @Test fun presetsMergeWithEditedScenarios() = runTest {
        val repo = RuleRepository(db)
        val office = repo.allScenarios().first { it.id == "office" }
        repo.saveScenario(office.copy(name = "Studio"))
        assertEquals("Studio", repo.allScenarios().first { it.id == "office" }.name)
        repo.deleteScenario("office")
        assertEquals(office, repo.allScenarios().first { it.id == "office" })
    }

    @Test fun exportThenImportRestoresEverything() = runTest {
        val images = ImageStore(context.filesDir)
        val settings = SettingsRepository.create(context)
        val feedback = FeedbackRepository(db)
        WardrobeRepository(db).upsertAll(demo(), 5)
        images.original("tee-white").writeText("jpeg")
        val backup = Backup(db, images, settings, feedback)
        val out = ByteArrayOutputStream()
        backup.export(out, "test", 42)
        val preview = backup.preview(ByteArrayInputStream(out.toByteArray()))
        assertEquals(demo().size, preview.counts["Pieces"])
        backup.deleteAll()
        assertEquals(0, WardrobeRepository(db).count())
        backup.import(ByteArrayInputStream(out.toByteArray()), replace = true)
        assertEquals(demo().size, WardrobeRepository(db).count())
        assertEquals("jpeg", images.original("tee-white").readText())
    }

    @Test fun settingsWithoutShoppingListStillLoad() {
        // Settings saved by earlier versions have no shoppingList; they must decode, not reset.
        val old = DataJson.encodeToString(Settings.serializer(), Settings(boldness = 0.7))
            .replace(Regex(",?\"shoppingList\":\\[\\]"), "")
        assertTrue("shoppingList" !in old)
        val loaded = DataJson.decodeFromString(Settings.serializer(), old)
        assertEquals(0.7, loaded.boldness, 1e-9)
        assertTrue(loaded.shoppingList.isEmpty())
        val entry = ShoppingEntry("e", Subcategory.CHINOS, ColorFamily.OLIVE, "#6B6B3A", unlocks = 4)
        val withList = Settings(shoppingList = listOf(entry))
        assertEquals(withList, DataJson.decodeFromString(Settings.serializer(), DataJson.encodeToString(Settings.serializer(), withList)))
    }

    @Test fun deckLogReadsOldAndNewFormats() {
        assertEquals(listOf(DeckCardLog("abc"), DeckCardLog("def")), DeckLog.decode("[\"abc\",\"def\"]"))
        val cards = listOf(DeckCardLog("s1", listOf("tee", "jeans")), DeckCardLog("s2", listOf("tee", "chinos")))
        assertEquals(cards, DeckLog.decode(DeckLog.encode(cards)))
        assertTrue(DeckLog.decode("not json").isEmpty())
        // Counted per card that showed the piece.
        assertEquals(mapOf("tee" to 3, "jeans" to 1, "chinos" to 1), DeckLog.exposure(cards + DeckCardLog("s3", listOf("tee"))))
    }

    @Test fun parsesRecordedForecast() {
        val body = javaClass.getResource("/open_meteo_forecast.json")!!.readText()
        val s = OpenMeteoClient().parseForecast(body, LatLon("Athens", 37.98, 23.72), 0)
        assertEquals(10800, s.utcOffsetSeconds)
        assertEquals(4, s.hourly.size)
        assertEquals(17.1, s.hourly[0].feels, 1e-9)
        assertEquals(31.5, s.hourly[3].wind, 1e-9)
        assertEquals(20741L, s.daily[0].epochDay)
        assertEquals(18 * 60 + 40, s.daily[0].sunsetMinute)
        val w = io.github.aristidisg.ensemble.engine.WeatherWindow.aggregate(s, 20741L, 9, 13)!!
        assertEquals(17.1, w.feelsMin, 1e-9)
        assertEquals(70.0, w.rainProbMax, 1e-9)
    }

    @Test fun parsesRecordedGeocoding() {
        val body = javaClass.getResource("/open_meteo_geocoding.json")!!.readText()
        val r = OpenMeteoClient().parseGeocoding(body)
        assertEquals(2, r.size)
        assertEquals("Athens, Attica, Greece", r[0].name)
    }
}
