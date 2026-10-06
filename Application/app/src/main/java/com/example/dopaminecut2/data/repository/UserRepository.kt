package com.example.dopaminecut2.data.repository

import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.User
import com.example.dopaminecut2.data.remote.RemoteUserDataSource
import com.example.dopaminecut2.statistics.UsageSnapshotStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow

class UserRepository(
    private val remoteDataSource: RemoteUserDataSource,
    private val localUsageStore: UsageSnapshotStore
) : UserRepositoryInterface {
    private val users = MutableStateFlow<Map<String, User>>(emptyMap())
    private val monthlyCache = ConcurrentHashMap<String, Map<String, DailyStatistics>>()
    fun clearStatisticsCache(uid: String) { monthlyCache.keys.removeAll { it.startsWith("$uid|") } }

    override suspend fun getUserInfo(userId: String): Result<User> {
        users.value[userId]?.let { return Result.success(it) }
        return runCatching { remoteDataSource.fetchUser(userId) }
            .onSuccess { user -> users.value = users.value + (userId to user) }
    }

    override suspend fun getDailyStatistics(
        userId: String,
        date: String
    ): Result<DailyStatistics?> = runCatching {
        localUsageStore.get(userId, date)?.toDailyStatistics() ?: run {
            val month = date.take(6)
            val cacheKey = "$userId|$month"
            val values = monthlyCache[cacheKey] ?: remoteDataSource
                .fetchMonthlyStatistics(userId, month)
                .also { fetched -> monthlyCache[cacheKey] = fetched }
            values[date]
        }
    }

    override suspend fun getStatisticsRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Result<List<DailyStatistics>> = runCatching {
        val start = LocalDate.parse(startDate, DATE_FORMATTER)
        val end = LocalDate.parse(endDate, DATE_FORMATTER)
        require(!end.isBefore(start)) { "통계 시작일이 종료일보다 늦습니다." }

        val remote = linkedMapOf<String, DailyStatistics>()
        for (month in monthsBetween(start, end)) {
            val cacheKey = "$userId|$month"
            val values = monthlyCache[cacheKey] ?: remoteDataSource.fetchMonthlyStatistics(userId, month)
                .also { values -> monthlyCache[cacheKey] = values }
            remote.putAll(values)
        }

        val local = localUsageStore.getRange(userId, startDate, endDate)
            .associate { snapshot -> snapshot.date to snapshot.toDailyStatistics() }

        (remote + local).values
            .filter { statistics -> statistics.date in startDate..endDate }
            .sortedBy(DailyStatistics::date)
    }

    private fun monthsBetween(start: LocalDate, end: LocalDate): List<String> {
        val result = mutableListOf<String>()
        var cursor = YearMonth.from(start)
        val last = YearMonth.from(end)
        while (!cursor.isAfter(last)) {
            result += cursor.format(MONTH_FORMATTER)
            cursor = cursor.plusMonths(1)
        }
        return result
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
    }
}
