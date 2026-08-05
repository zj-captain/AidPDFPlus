package com.ysdc.aidpdf.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val STORE_FILE_NAME = "aid_pdf_preferences"
private const val LANGUAGE_TAG_KEY = "languageTag"
private val LEGACY_STORE_FILE_NAMES = arrayOf(
    "aid_pdf_documents",
    "aid_pdf_reminder_stats",
    "aid_pdf_reminder_navigation",
    "aid_pdf_reminder_alarm"
)

object SharedPreferencesDelegates {

    @Volatile
    private var legacyMigrationComplete = false

    private val preferences: SharedPreferences
        get() {
            val preferences = appInstance.getSharedPreferences(STORE_FILE_NAME, Context.MODE_PRIVATE)
            migrateLegacyPreferences(preferences)
            return preferences
        }

    fun boolean(defaultValue: Boolean = false, key: String? = null): ReadWriteProperty<Any?, Boolean> {
        return preference(
            key = key,
            defaultValue = defaultValue,
            read = SharedPreferences::getBoolean,
            write = { name, value -> putBoolean(name, value) }
        )
    }

    fun int(defaultValue: Int = 0, key: String? = null): ReadWriteProperty<Any?, Int> {
        return preference(
            key = key,
            defaultValue = defaultValue,
            read = SharedPreferences::getInt,
            write = { name, value -> putInt(name, value) }
        )
    }

    fun long(defaultValue: Long = 0L, key: String? = null): ReadWriteProperty<Any?, Long> {
        return preference(
            key = key,
            defaultValue = defaultValue,
            read = SharedPreferences::getLong,
            write = { name, value -> putLong(name, value) }
        )
    }

    fun float(defaultValue: Float = 0f, key: String? = null): ReadWriteProperty<Any?, Float> {
        return preference(
            key = key,
            defaultValue = defaultValue,
            read = SharedPreferences::getFloat,
            write = { name, value -> putFloat(name, value) }
        )
    }

    fun double(defaultValue: Double = 0.0, key: String? = null): ReadWriteProperty<Any?, Double> {
        return preference(
            key = key,
            defaultValue = defaultValue,
            read = { name, fallback -> readDouble(name, fallback) },
            write = { name, value -> putLong(name, java.lang.Double.doubleToRawLongBits(value)) }
        )
    }

    fun string(defaultValue: String = "", key: String? = null): ReadWriteProperty<Any?, String> {
        return preference(
            key = key,
            defaultValue = defaultValue,
            read = { name, fallback -> getString(name, fallback) ?: fallback },
            write = { name, value -> putString(name, value) }
        )
    }

    fun contains(key: String): Boolean = preferences.contains(key)

    fun remove(key: String) {
        preferences.edit { remove(key) }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    @Synchronized
    private fun migrateLegacyPreferences(target: SharedPreferences) {
        if (legacyMigrationComplete) return
        target.edit {
            LEGACY_STORE_FILE_NAMES.forEach { storeName ->
                val legacy = appInstance.getSharedPreferences(storeName, Context.MODE_PRIVATE)
                legacy.all.forEach { (key, value) ->
                    if (!target.contains(key)) {
                        when (value) {
                            is Boolean -> putBoolean(key, value)
                            is Float -> putFloat(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is String -> putString(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }
            }
        }
        legacyMigrationComplete = true
    }

    private fun <T> preference(
        key: String?,
        defaultValue: T,
        read: SharedPreferences.(String, T) -> T,
        write: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
    ): ReadWriteProperty<Any?, T> {
        return PreferenceDelegate(
            explicitKey = key,
            defaultValue = defaultValue,
            preferencesProvider = { preferences },
            read = read,
            write = write
        )
    }

    private class PreferenceDelegate<T>(
        private val explicitKey: String?,
        private val defaultValue: T,
        private val preferencesProvider: () -> SharedPreferences,
        private val read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> SharedPreferences.Editor
    ) : ReadWriteProperty<Any?, T> {

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return preferencesProvider().read(resolveKey(property), defaultValue)
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            preferencesProvider().edit { write(resolveKey(property), value) }
        }

        private fun resolveKey(property: KProperty<*>): String {
            return explicitKey ?: property.name
        }
    }
}

private fun SharedPreferences.readDouble(key: String, defaultValue: Double): Double {
    val rawBits = all[key] as? Long ?: return defaultValue
    return java.lang.Double.longBitsToDouble(rawBits)
}

var isSamSungAndKorean by SharedPreferencesDelegates.string("")

var isFirstRun by SharedPreferencesDelegates.boolean(true)
var launchCount by SharedPreferencesDelegates.int(0)
var lastOpenedAt by SharedPreferencesDelegates.long(0L)
var languageTag by SharedPreferencesDelegates.string("en", LANGUAGE_TAG_KEY)
val hasSavedLanguageTag: Boolean
    get() = SharedPreferencesDelegates.contains(LANGUAGE_TAG_KEY)
var pdfScaleRatio by SharedPreferencesDelegates.float(0f)
var totalRevenue by SharedPreferencesDelegates.double(0.0)
var userAlias by SharedPreferencesDelegates.string("")
var hasCompletedUmpConsent by SharedPreferencesDelegates.boolean(false)
var firstConsentCountryCode by SharedPreferencesDelegates.string("")
var installReferrer by SharedPreferencesDelegates.string("")
var hasInitFcmTopic by SharedPreferencesDelegates.boolean(false)
var hasReportedInstall by SharedPreferencesDelegates.boolean(false)
var hasReportedBuyUser by SharedPreferencesDelegates.boolean(false)
var hasReportedReferrerUser by SharedPreferencesDelegates.boolean(false)
var hasReportedReviewUser by SharedPreferencesDelegates.boolean(false)
var hasReportedTestAdsUser by SharedPreferencesDelegates.boolean(false)
var hasReportedEmulatorUser by SharedPreferencesDelegates.boolean(false)
var hasReportedNoSimUser by SharedPreferencesDelegates.boolean(false)
var hasReportedAdbUser by SharedPreferencesDelegates.boolean(false)
var documentRecordsJson by SharedPreferencesDelegates.string("[]", "records")
var pendingReminderTarget by SharedPreferencesDelegates.string("", "pending_target")
var pendingReminderTrigger by SharedPreferencesDelegates.string("", "pending_trigger")
var pendingReminderSource by SharedPreferencesDelegates.string("", "pending_source")
var nextReminderAlarmAt by SharedPreferencesDelegates.long(0L, "next_alarm_at")
var hasShownReminderOverlay by SharedPreferencesDelegates.boolean(false, "has_shown_reminder_overlay")

var timeReminderDay by SharedPreferencesDelegates.int(-1, "time_day")
var timeReminderCount by SharedPreferencesDelegates.int(0, "time_count")
var timeReminderLastShownAt by SharedPreferencesDelegates.long(0L, "time_last")
var unlockReminderDay by SharedPreferencesDelegates.int(-1, "unlock_day")
var unlockReminderCount by SharedPreferencesDelegates.int(0, "unlock_count")
var unlockReminderLastShownAt by SharedPreferencesDelegates.long(0L, "unlock_last")
var alarmReminderDay by SharedPreferencesDelegates.int(-1, "alarm_day")
var alarmReminderCount by SharedPreferencesDelegates.int(0, "alarm_count")
var alarmReminderLastShownAt by SharedPreferencesDelegates.long(0L, "alarm_last")
var homeReminderDay by SharedPreferencesDelegates.int(-1, "home_day")
var homeReminderCount by SharedPreferencesDelegates.int(0, "home_count")
var homeReminderLastShownAt by SharedPreferencesDelegates.long(0L, "home_last")
var appExitReminderDay by SharedPreferencesDelegates.int(-1, "app_exit_day")
var appExitReminderCount by SharedPreferencesDelegates.int(0, "app_exit_count")
var appExitReminderLastShownAt by SharedPreferencesDelegates.long(0L, "app_exit_last")
var recentReminderDay by SharedPreferencesDelegates.int(-1, "recent_day")
var recentReminderCount by SharedPreferencesDelegates.int(0, "recent_count")
var recentReminderLastShownAt by SharedPreferencesDelegates.long(0L, "recent_last")
var adClickReminderDay by SharedPreferencesDelegates.int(-1, "ad_click_day")
var adClickReminderCount by SharedPreferencesDelegates.int(0, "ad_click_count")
var adClickReminderLastShownAt by SharedPreferencesDelegates.long(0L, "ad_click_last")

var timeReminderContentIndex by SharedPreferencesDelegates.int(-1, "time_content_index")
var unlockReminderContentIndex by SharedPreferencesDelegates.int(-1, "unlock_content_index")
var alarmReminderContentIndex by SharedPreferencesDelegates.int(-1, "alarm_content_index")
var homeReminderContentIndex by SharedPreferencesDelegates.int(-1, "home_content_index")
var appExitReminderContentIndex by SharedPreferencesDelegates.int(-1, "app_exit_content_index")
var recentReminderContentIndex by SharedPreferencesDelegates.int(-1, "recent_content_index")
var adClickReminderContentIndex by SharedPreferencesDelegates.int(-1, "ad_click_content_index")

var timeReminderSystemNoticeSlot by SharedPreferencesDelegates.int(0, "time_system_notice_slot")
var unlockReminderSystemNoticeSlot by SharedPreferencesDelegates.int(0, "unlock_system_notice_slot")
var alarmReminderSystemNoticeSlot by SharedPreferencesDelegates.int(0, "alarm_system_notice_slot")
var additionalReminderSystemNoticeSlot by SharedPreferencesDelegates.int(0, "additional_system_notice_slot")

var lastRateShowTime by SharedPreferencesDelegates.long(0L, "last_rate_show_time")
var rateValue by SharedPreferencesDelegates.int(0, "user_rate_value")
