package io.github.aristidisg.ensemble.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** One card of a logged deck: its outfit signature and the pieces it showed. */
@Serializable
data class DeckCardLog(val signature: String, val items: List<String> = emptyList())

/** Deck history (deck_log.json). Older versions stored a plain list of signatures. */
object DeckLog {
    private val cards = ListSerializer(DeckCardLog.serializer())
    private val legacy = ListSerializer(String.serializer())

    fun encode(c: List<DeckCardLog>): String = DataJson.encodeToString(cards, c)

    fun decode(json: String): List<DeckCardLog> =
        runCatching { DataJson.decodeFromString(cards, json) }
            .recoverCatching { DataJson.decodeFromString(legacy, json).map { DeckCardLog(it) } }
            .getOrDefault(emptyList())

    /** Per item: how many of [decks] showed it at least once. */
    fun exposure(decks: List<List<DeckCardLog>>): Map<String, Int> =
        decks.flatMap { d -> d.flatMap { it.items }.distinct() }.groupingBy { it }.eachCount()
}
