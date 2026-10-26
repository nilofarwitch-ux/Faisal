@file:Suppress(
    "DEPRECATION",
    "DEPRECATION_ERROR",
    "UNUSED_PARAMETER",
    "UNCHECKED_CAST"
)

package OttSource

import android.util.Base64
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.api.Log
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

/**
 * Shared JSON parser
 */
val JSONParser = object : ResponseParser {

    private val mapper: ObjectMapper =
        jacksonObjectMapper()
            .configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
            )
            .configure(
                JsonReadFeature
                    .ALLOW_UNESCAPED_CONTROL_CHARS
                    .mappedFeature(),
                true
            )

    override fun <T : Any> parse(
        text: String,
        kClass: KClass<T>
    ): T {
        return mapper.readValue(
            text,
            kClass.java
        )
    }

    override fun <T : Any> parseSafe(
        text: String,
        kClass: KClass<T>
    ): T? {
        return try {
            mapper.readValue(
                text,
                kClass.java
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun writeValueAsString(
        obj: Any
    ): String {
        return mapper.writeValueAsString(obj)
    }
}

/**
 * Shared HTTP client
 */
val app = Requests(
    responseParser = JSONParser
).apply {
    defaultHeaders = mapOf(
        "User-Agent" to USER_AGENT
    )
}

inline fun <reified T : Any> parseJson(
    text: String
): T {
    return JSONParser.parse(
        text,
        T::class
    )
}

inline fun <reified T : Any> tryParseJson(
    text: String
): T? {
    return try {
        JSONParser.parseSafe(
            text,
            T::class
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Converts:
 *
 * "2h 10m" -> 130
 * "45m"    -> 45
 * "1h"     -> 60
 */
fun convertRuntimeToMinutes(
    runtime: String
): Int {

    var totalMinutes = 0

    val parts = runtime
        .trim()
        .split(Regex("\\s+"))

    for (part in parts) {
        when {
            part.endsWith("h", ignoreCase = true) -> {
                val hours =
                    part
                        .removeSuffix("h")
                        .removeSuffix("H")
                        .trim()
                        .toIntOrNull()
                        ?: 0

                totalMinutes += hours * 60
            }

            part.endsWith("m", ignoreCase = true) -> {
                val minutes =
                    part
                        .removeSuffix("m")
                        .removeSuffix("M")
                        .trim()
                        .toIntOrNull()
                        ?: 0

                totalMinutes += minutes
            }
        }
    }

    return totalMinutes
}

suspend fun bypass(
    mainUrl: String,
    force: Boolean = false
): String {

    if (force) {
        NetflixMirrorStorage.clearCookie()
    }

    val (savedCookie, savedTimestamp) =
        NetflixMirrorStorage.getCookie()

    val savedValid =
        !savedCookie.isNullOrEmpty() &&
        (
            savedCookie.contains("::") ||
            savedCookie.contains("%3A%3A")
        ) &&
        System.currentTimeMillis() - savedTimestamp < 43_200_000L

    if (savedValid) {
        return savedCookie!!
    }

    val defaultUa =
        "Mozilla/5.0 (Linux; Android 12; RMX2117 Build/SP1A.210812.016; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
        "Chrome/147.0.7727.55 Mobile Safari/537.36 /OS.Gatu v3.0"

    val appReq = "app.netmirror.netmirrornew"

    val homeUrl = "$mainUrl/mobile/home?app=1"

    val homeRes = try {
        app.get(
            homeUrl,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "User-Agent" to defaultUa,
                "X-Requested-With" to appReq
            )
        )
    } catch (_: Exception) {
        return ""
    }

    val addhash = Regex(
        """data-addhash="([^"]+)""""
    ).find(homeRes.text)?.groupValues?.getOrNull(1)

        ?.trim()

    if (addhash.isNullOrEmpty()) {
        return ""
    }

        try {
        app.get(
            "https://userver.net52.cc/?ffr455=$addhash&a=y&t=${java.lang.Math.random()}",
            headers = mapOf(
                "User-Agent" to defaultUa,
                "X-Requested-With" to appReq
            ),
            timeout = 3L
        )
    } catch (_: Exception) {
        // Reference intentionally ignores this request failure.
    }


    for (i in 1..10) {

        delay(
            if (i == 1) {
                4000L
            } else {
                5000L
            }
        )

        val verifyRes = try {
            app.post(
                "$mainUrl/mobile/verify2.php",
                headers = mapOf(
                    "Accept" to "*/*",
                    "User-Agent" to defaultUa,
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to homeUrl,
                    "Content-Type" to "application/x-www-form-urlencoded"
                ),
                data = mapOf(
                    "verify" to addhash
                )
            )
        } catch (_: Exception) {
            continue
        }

        val body = verifyRes.text

        val done =
            body.contains("\"statusup\":\"All Done\"") ||
            body.contains("All Done", ignoreCase = true)

        if (!done) continue

        val cookieHeader =
            verifyRes.okhttpResponse
                .headers("Set-Cookie")
                .firstOrNull {
                    it.contains("t_hash_t=", ignoreCase = true)
                }

        if (cookieHeader != null) {

            val cookie = cookieHeader
                .substringAfter("t_hash_t=", "")
                .substringBefore(";")
                .trim()

            if (cookie.isNotEmpty()) {
                NetflixMirrorStorage.saveCookie(cookie)
                return cookie
            }
        }
    }

    return ""
}

val newTvBaseHeaders =
    mapOf(
        "Cache-Control" to
            "no-cache, no-store, must-revalidate",
        "Pragma" to "no-cache",
        "Expires" to "0",
        "X-Requested-With" to
            "NetmirrorNewTV v1.0",
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; " +
            "rv:136.0) Gecko/20100101 Firefox/136.0 " +
            "/OS.GatuNewTV v1.0",
        "Accept" to
            "application/json, text/plain, */*"
    )

val newTvDomains =
    listOf(
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==",
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3QuYXBw",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNsaWNr",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lmluaw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNob3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnN0b3Jl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lndpa2k=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbms=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo="
    )

fun decodeBase64(
    value: String
): String {
    return String(
        Base64.decode(
            value,
            Base64.DEFAULT
        )
    )
}

private var resolvedApiUrl: String = ""

suspend fun resolveApiUrl(): String {

    if (resolvedApiUrl.isNotBlank()) {
        return resolvedApiUrl
    }

    for (encoded in newTvDomains) {

        val base =
            decodeBase64(encoded)
                .trimEnd('/')

        try {

            val response =
                app.get(
                    "$base/checknewtv.php",
                    headers = newTvBaseHeaders
                ).parsed<NewTvTokenResponse>()

            val tokenHash =
                response.token_hash

            if (!tokenHash.isNullOrBlank()) {

                resolvedApiUrl =
                    decodeBase64(tokenHash)
                        .trimEnd('/')

                return resolvedApiUrl
            }

        } catch (_: Exception) {
        }
    }

    throw Exception(
        "Failed to resolve NewTV API base URL"
    )
}

fun buildNewTvHeaders(
    ott: String,
    extra: Map<String, String> = emptyMap()
): Map<String, String> {

    val result =
        newTvBaseHeaders.toMutableMap()

    result["Ott"] = ott

    extra.forEach { (key, value) ->
        result[key] = value
    }

    return result
}

data class NewTvTokenResponse(
    val token_hash: String? = null
)

data class NewTvPlayerResponse(
    val status: String? = null,
    val video_link: String? = null,
    val referer: String? = null
)