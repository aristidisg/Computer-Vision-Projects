package io.github.aristidisg.ensemble.data.repo

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import io.github.aristidisg.ensemble.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Shared JSON configuration for documents, settings and exports. */
val DataJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
}

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings = try {
        DataJson.decodeFromString(Settings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings", e)
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(DataJson.encodeToString(Settings.serializer(), t).encodeToByteArray())
    }
}

/** All settings and profile choices, one JSON document in DataStore. */
class SettingsRepository(private val store: DataStore<Settings>) {
    val settings: Flow<Settings> = store.data

    suspend fun current(): Settings = store.data.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.updateData(transform)
    }

    suspend fun replace(s: Settings) {
        store.updateData { s }
    }

    companion object {
        fun create(context: Context): SettingsRepository = SettingsRepository(
            DataStoreFactory.create(
                serializer = SettingsSerializer,
                corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { Settings() },
                produceFile = { context.dataStoreFile("settings.json") },
            ),
        )
    }
}
