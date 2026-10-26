package OttSource

import android.content.Context
import android.content.SharedPreferences

object NetflixMirrorStorage {

    private var context: Context? = null
    private var prefs: SharedPreferences? = null

    private var cachedCookie: String? = null
    private var cachedTimestamp: Long = 0L

    private const val PREF_NAME = "NetflixMirrorPrefs"
    private const val COOKIE_KEY = "nf_cookie"
    private const val TIMESTAMP_KEY = "nf_cookie_timestamp"

    private const val COOKIE_VALIDITY = 43_200_000L // 12 hours

    fun init(context: Context) {
        this.context = context.applicationContext

        this.prefs = context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        cachedCookie = prefs?.getString(COOKIE_KEY, null)
        cachedTimestamp = prefs?.getLong(TIMESTAMP_KEY, 0L) ?: 0L
    }

    fun getCookie(): Pair<String?, Long> {
        val cookie = cachedCookie ?: prefs?.getString(COOKIE_KEY, null)
        val timestamp =
            if (cachedTimestamp != 0L) {
                cachedTimestamp
            } else {
                prefs?.getLong(TIMESTAMP_KEY, 0L) ?: 0L
            }

        return cookie to timestamp
    }

    fun getValidCookie(): String? {
        val (cookie, timestamp) = getCookie()

        if (cookie.isNullOrEmpty()) return null

        val validFormat =
            cookie.contains("::") ||
            cookie.contains("%3A%3A")

        val validTime =
            timestamp > 0L &&
            System.currentTimeMillis() - timestamp < COOKIE_VALIDITY

        return if (validFormat && validTime) {
            cookie
        } else {
            null
        }
    }

    fun saveCookie(cookie: String) {
        if (cookie.isBlank()) return

        cachedCookie = cookie
        cachedTimestamp = System.currentTimeMillis()

        prefs?.edit()
            ?.putString(COOKIE_KEY, cachedCookie)
            ?.putLong(TIMESTAMP_KEY, cachedTimestamp)
            ?.apply()
    }

    fun clearCookie() {
        cachedCookie = null
        cachedTimestamp = 0L

        prefs?.edit()
            ?.remove(COOKIE_KEY)
            ?.remove(TIMESTAMP_KEY)
            ?.apply()
    }
}