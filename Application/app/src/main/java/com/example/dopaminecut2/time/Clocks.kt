package com.example.dopaminecut2.time

import android.os.SystemClock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

fun interface MonotonicClock {
    fun nowMillis(): Long
}

object AndroidMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

fun interface WallClock {
    fun now(): Date
}

object SystemWallClock : WallClock {
    override fun now(): Date = Date()
}

fun interface DateIdProvider {
    fun currentDateId(): String
}

class LocalDateIdProvider(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DateIdProvider {
    override fun currentDateId(): String =
        LocalDate.now(zoneId).format(DateTimeFormatter.BASIC_ISO_DATE)
}
