package com.example.dopaminecut2.data.repository

import com.example.dopaminecut2.data.model.DailyStatistics
import com.example.dopaminecut2.data.model.User

interface UserRepositoryInterface {
    suspend fun getUserInfo(userId: String): Result<User>

    suspend fun getDailyStatistics(userId: String, date: String): Result<DailyStatistics?>

    /** yyyyMMdd 양끝 날짜를 포함하며 월별 문서를 최대 한 번씩 읽는다. */
    suspend fun getStatisticsRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Result<List<DailyStatistics>>
}
