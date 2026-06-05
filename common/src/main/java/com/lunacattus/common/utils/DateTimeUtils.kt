package com.lunacattus.common.utils

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 将时间戳（毫秒）转换为指定格式的日期时间字符串。
 *
 * @param pattern 日期时间格式模板，默认 yyyy-MM-dd HH:mm:ss。
 * @param timeZone 目标时区，默认使用系统当前时区 [TimeZone.getDefault]。
 * @param locale 语言环境，默认使用系统当前环境 [Locale.getDefault]。
 * @return 格式化后的日期时间字符串。
 */
fun Long.toDateTimeString(
    pattern: String = "yyyy-MM-dd HH:mm:ss",
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val sdf = SimpleDateFormat(pattern, locale)
    sdf.timeZone = timeZone
    return sdf.format(Date(this))
}

/**
 * 将时间戳（毫秒）转换为符合特定时区的“智能”人性化日期字符串（如：今天、昨天、前天或具体年月日）。
 *
 * @param todayString "今天" 的本地化字符串。
 * @param yesterdayString "昨天" 的本地化字符串。
 * @param dayBeforeYesterdayString "前天" 的本地化字符串。
 * @param timeZone 目标时区，智能计算天数差时将基于该时区判定，默认使用系统当前时区 [TimeZone.getDefault]。
 * @param locale 语言环境，用于决定非今天/昨天的日期排版格式，默认使用系统当前环境 [Locale.getDefault]。
 * @return 智能转换后的本地化日期字符串。
 */
fun Long.toSmartDateString(
    todayString: String,
    yesterdayString: String,
    dayBeforeYesterdayString: String,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val now = Calendar.getInstance(timeZone, locale)
    val target =
        Calendar.getInstance(timeZone, locale).apply { timeInMillis = this@toSmartDateString }

    val nowYear = now.get(Calendar.YEAR)
    val targetYear = target.get(Calendar.YEAR)

    fun Calendar.isSameDay(other: Calendar): Boolean {
        return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
                get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }

    val yesterday = Calendar.getInstance(timeZone, locale).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val dayBeforeYesterday =
        Calendar.getInstance(timeZone, locale).apply { add(Calendar.DAY_OF_YEAR, -2) }

    return when {
        target.isSameDay(now) -> todayString
        target.isSameDay(yesterday) -> yesterdayString
        target.isSameDay(dayBeforeYesterday) -> dayBeforeYesterdayString
        nowYear == targetYear -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "MMMd")
            SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(target.time)
        }

        else -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "yyyyMMMd")
            SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(target.time)
        }
    }
}