package cn.ksuser.auth.core.settings

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DisplayTime {
    /** Zoned timestamps represent instants; legacy timestamps deliberately keep their wall time. */
    fun local(value: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
        runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone).toLocalDateTime() }
            .recoverCatching { Instant.parse(value).atZone(zone).toLocalDateTime() }
            .recoverCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
            .getOrNull()

    fun format(value: String?): String = value?.takeIf(String::isNotBlank)?.let {
        local(it)?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(if (L10n.language == "en") Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE)) ?: it
    } ?: L10n.text("暂无记录")

    fun day(value: String): String = local(value)?.toLocalDate()?.toString() ?: value.take(10)
}
