package io.github.aristidisg.ensemble.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The app's only clock. The engine never reads time; it is handed epoch days from here. */
@Singleton
class TimeSource @Inject constructor() {
    fun nowMillis(): Long = System.currentTimeMillis()
    fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    fun hour(): Int = LocalDateTime.now(ZoneId.systemDefault()).hour
    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
