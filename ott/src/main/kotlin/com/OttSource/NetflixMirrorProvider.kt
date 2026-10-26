package OttSource

import android.content.Context
import OttSource.entities.EpisodesData
import OttSource.entities.PostData
import OttSource.entities.SearchData

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.OttSource.DesiExtensionPopup

import okhttp3.Interceptor
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import java.net.URLEncoder

class NetflixMirrorProvider : MainAPI() {

    companion object {
        var context: Context? = null
    }

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override var lang = "hi"

    override var mainUrl = "https://net52.cc"
    override var name = "Netflix Hindi"

    override val hasMainPage = true

    private val defaultUa =
        "Mozilla/5.0 (Linux; Android 12; RMX2117 Build/SP1A.210812.016; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
        "Chrome/147.0.7727.55 Mobile Safari/537.36 /OS.Gatu v3.0"

    private val mobileHomeUrl
        get() = "$mainUrl/mobile/home?app=1"

    private val browserHeaders = mapOf(
        "Accept" to
            "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8," +
            "application/signed-exchange;v=b3;q=0.7",

        "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
        "Cache-Control" to "max-age=0",
        "Connection" to "keep-alive",

        "sec-ch-ua" to
            "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Android WebView\";v=\"144\"",

        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Android\"",

        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",

        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to defaultUa,
        "X-Requested-With" to "XMLHttpRequest"
    )

    private fun initStorage() {
        val ctx = context ?: return
        NetflixMirrorStorage.init(ctx)
    }

    private suspend fun getValidToken(): String {
        initStorage()

        val cached = NetflixMirrorStorage.getValidCookie()

        if (!cached.isNullOrEmpty()) {
            return cached
        }

        return bypass(mainUrl)
    }

    private fun siteCookies(token: String): Map<String, String> {
        return buildMap {
            if (token.isNotEmpty()) {
                put("t_hash_t", token)
            }

            put("ott", "nf")
            put("hd", "on")
        }
    }

    private fun isVerificationWall(
        url: String,
        body: String,
        responseCode: Int,
        responseUrl: String
    ): Boolean {

        if (responseCode == 302) {
            return true
        }

        if (responseUrl.contains("verify", ignoreCase = true)) {
            return true
        }

        if (url.contains("/mobile/home")) {
            val acceptedHome =
                body.contains(
                    "<title>Home - Android Mobile</title>",
                    ignoreCase = true
                ) ||
                body.contains("tray-container", ignoreCase = true) ||
                body.contains("<article", ignoreCase = true)

            return !acceptedHome
        }

        if (url.contains(".php", ignoreCase = true)) {
            return body
                .trimStart()
                .startsWith("<")
        }

        return false
    }

    private suspend fun siteGet(
        url: String,
        referer: String = mobileHomeUrl,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {

        var token = getValidToken()

        fun requestHeaders(): Map<String, String> {
            return browserHeaders + extraHeaders
        }

        var response = app.get(
            url,
            headers = requestHeaders(),
            referer = referer,
            cookies = siteCookies(token)
        )

        var body = response.text

        val http = response.okhttpResponse

        val rejected = isVerificationWall(
            url = url,
            body = body,
            responseCode = http.code,
            responseUrl = http.request.url.toString()
        )

        if (!rejected) {
            return body
        }

        NetflixMirrorStorage.clearCookie()

        token = bypass(mainUrl, force = true)

        if (token.isEmpty()) {
            return body
        }

        response = app.get(
            url,
            headers = requestHeaders(),
            referer = referer,
            cookies = siteCookies(token)
        )

        body = response.text

        return body
    }

    private fun posterUrl(id: String): String {
        return "https://imgcdn.kim/poster/v/$id.jpg"
    }

    private fun episodePosterUrl(id: String): String {
        return "https://imgcdn.kim/epimg/150/$id.jpg"
    }

    private fun fixNetMirrorUrl(raw: String): String {
        val clean = raw
            .replace("\\", "")
            .trim()

        if (clean.isEmpty()) return ""

        return when {
            clean.startsWith("http://", true) -> clean
            clean.startsWith("https://", true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "$mainUrl$clean"
            else -> "$mainUrl/$clean"
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
runCatching {
        DesiExtensionPopup.show()}

        val body = siteGet(
            url = mobileHomeUrl,
            referer = mobileHomeUrl
        )

        val document = Jsoup.parse(body)

        val items = document
            .select(".tray-container, #top10")
            .map {
                it.toHomePageList()
            }

        return newHomePageResponse(
            items,
            false
        )
    }

    private fun Element.toHomePageList(): HomePageList {

        val title =
            select("h2, span")
                .firstOrNull()
                ?.text()
                ?.trim()
                .orEmpty()

        val items =
            select("article, .top10-post")
                .mapNotNull {
                    it.toSearchResult()
                }

        return HomePageList(
            title,
            items,
            isHorizontalImages = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {

        val id =
            selectFirst("a")
                ?.attr("data-post")
                ?.takeIf { it.isNotBlank() }
                ?: attr("data-post")
                    .takeIf { it.isNotBlank() }
                ?: return null

        return newAnimeSearchResponse(
            "",
            Id(id).toJson()
        ) {
            posterUrl = posterUrl(id)

            posterHeaders = mapOf(
                "Referer" to "$mainUrl/home"
            )
        }
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val encodedQuery =
            URLEncoder.encode(
                query,
                Charsets.UTF_8.name()
            )

        val url =
            "$mainUrl/mobile/search.php" +
            "?s=$encodedQuery&t=$unixTime"

        val body = siteGet(
            url = url,
            referer = "$mainUrl/home"
        )

        val data =
            try {
                parseJson<SearchData>(body)
            } catch (_: Exception) {
                return emptyList()
            }

        return data.searchResult.map {
            newAnimeSearchResponse(
                it.t,
                Id(it.id).toJson()
            ) {
                posterUrl = posterUrl(it.id)

                posterHeaders = mapOf(
                    "Referer" to "$mainUrl/home"
                )
            }
        }
    }

    override suspend fun load(
        url: String
    ): LoadResponse? {

        val id =
            runCatching {
                parseJson<Id>(url).id
            }.getOrNull()
                ?: return null

        val body = siteGet(
            url =
                "$mainUrl/mobile/post.php?id=$id&t=$unixTime",
            referer = "$mainUrl/home"
        )

        val data =
            try {
                parseJson<PostData>(body)
            } catch (_: Exception) {
                return null
            }

        val episodes = arrayListOf<Episode>()

        val title = data.title

        val castList =
            data.cast
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

        val cast =
            castList.map {
                ActorData(Actor(it))
            }

        val genre =
            data.genre
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }

        val rating =
            data.match
                ?.replace("IMDb ", "")

        var runTime: Int = 0
        runTime = convertRuntimeToMinutes(data.runtime.toString()) ?: 0

        val suggest =
            data.suggest?.map {
                newAnimeSearchResponse(
                    "",
                    Id(it.id).toJson()
                ) {
                    posterUrl = posterUrl(it.id)

                    posterHeaders = mapOf(
                        "Referer" to "$mainUrl/home"
                    )
                }
            }

        val firstEpisode =
            data.episodes.firstOrNull()

        if (firstEpisode == null) {

            episodes.add(
                newEpisode(
                    LoadData(
                        title,
                        id
                    )
                ) {
                    name = data.title
                }
            )

        } else {

            data.episodes
                .filterNotNull()
                .forEach {

                    episodes.add(
                        newEpisode(
                            LoadData(
                                title,
                                it.id
                            )
                        ) {

                            name = it.t

                            episode =
                                it.ep
                                    .replace("E", "")
                                    .toIntOrNull()

                            season =
                                it.s
                                    .replace("S", "")
                                    .toIntOrNull()

                            posterUrl =
                                "https://imgcdn.kim/poster/v/150/${it.id}.jpg"

                            var fetchedRunTime: Int = 0
                            fetchedRunTime = it.time.replace("m", "").toIntOrNull() ?: 0
                            this.runTime = fetchedRunTime
                        }
                    )
                }

            if (data.nextPageShow == 1) {
                val nextSeason =
                    data.nextPageSeason

                if (!nextSeason.isNullOrEmpty()) {

                    episodes.addAll(
                        getEpisodes(
                            title = title,
                            eid = url,
                            sid = nextSeason,
                            page = 2
                        )
                    )
                }
            }

            data.season
                ?.dropLast(1)
                ?.forEach {

                    episodes.addAll(
                        getEpisodes(
                            title = title,
                            eid = url,
                            sid = it.id,
                            page = 1
                        )
                    )
                }
        }

        val type =
            if (firstEpisode == null) {
                TvType.Movie
            } else {
                TvType.TvSeries
            }

        return newTvSeriesLoadResponse(
            title,
            url,
            type,
            episodes
        ) {

            posterUrl =
                posterUrl(id)

            backgroundPosterUrl =
                posterUrl(id)

            posterHeaders =
                mapOf(
                    "Referer" to "$mainUrl/home"
                )

            plot =
                data.desc

            year =
                data.year.toIntOrNull()

            tags =
                genre

            actors =
                cast

            score =
                Score.from10(rating)

            duration =
                runTime

            contentRating =
                data.ua

            recommendations =
                suggest
        }
    }

    private suspend fun getEpisodes(
        title: String,
        eid: String,
        sid: String,
        page: Int
    ): List<Episode> {

        val episodes = arrayListOf<Episode>()

        var currentPage = page

        while (true) {

            val url =
                "$mainUrl/mobile/episodes.php" +
                "?s=$sid" +
                "&series=$eid" +
                "&t=$unixTime" +
                "&page=$currentPage"

            val body = siteGet(
                url = url,
                referer = "$mainUrl/home"
            )

            val data =
                try {
                    parseJson<EpisodesData>(body)
                } catch (_: Exception) {
                    break
                }

            data.episodes?.forEach {

                episodes.add(
                    newEpisode(
                        LoadData(
                            title,
                            it.id
                        )
                    ) {

                        name =
                            it.t

                        episode =
                            it.ep
                                .replace("E", "")
                                .toIntOrNull()

                        season =
                            it.s
                                .replace("S", "")
                                .toIntOrNull()

                        posterUrl =
                            episodePosterUrl(it.id)

                        var fetchedRunTime: Int = 0
                        fetchedRunTime = it.time.replace("m", "").toIntOrNull() ?: 0
                        this.runTime = fetchedRunTime
                    }
                )
            }

            if (data.nextPageShow == 0) {
                break
            }

            currentPage++
        }

        return episodes
    }

    private fun playlistHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "*/*",
            "Referer" to mobileHomeUrl,
            "User-Agent" to defaultUa,
            "X-Requested-With" to "app.netmirror.netmirrornew",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )
    }

    private fun playlistCookies(
        cookie: String
    ): Map<String, String> {
        return mapOf(
            "t_hash_t" to cookie,
            "ott" to "nf",
            "hd" to "on"
        )
    }

    private fun parsePlaylist(
        body: String
    ): JSONArray? {

        if (body.contains("in=unknown::db")) {
            return null
        }

        return try {
            JSONArray(body)
                .takeIf { it.length() > 0 }
        } catch (_: Exception) {
            null
        }
    }

        override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // ID parse karna (Screenshot style combined with safe call)
        val loadData = runCatching { AppUtils.parseJson<LoadData>(data) }.getOrNull() ?: return false
        val id = loadData.id

        var cookieVal = getValidToken()

        if (cookieVal.isEmpty()) {
            cookieVal = bypass(mainUrl)
        }
        if (cookieVal.isEmpty()) {
            return false
        }

        // Netflix ke liye playlist.php (Prime ke liye pv/playlist.php, HotStar ke liye hs/playlist.php)
        val playlistUrl = "$mainUrl/mobile/playlist.php?id=$id&tm=${System.currentTimeMillis() / 1000}"

        var response = app.get(
            playlistUrl,
            headers = playlistHeaders(),
            cookies = playlistCookies(cookieVal),
            referer = mobileHomeUrl
        )

        var rawBody = response.text
        var playlist = parsePlaylist(rawBody)

        if (playlist == null) {
            NetflixMirrorStorage.clearCookie()
            cookieVal = bypass(mainUrl, force = true)

            if (cookieVal.isEmpty()) return false

            response = app.get(
                playlistUrl,
                headers = playlistHeaders(),
                cookies = playlistCookies(cookieVal),
                referer = mobileHomeUrl
            )
            rawBody = response.text
            playlist = parsePlaylist(rawBody)
        }

        if (playlist == null) return false

        var linksFound = 0

        for (i in 0 until playlist.length()) {
            val item = playlist.optJSONObject(i) ?: continue

            // 1. SUBTITLES KA PREFIX LOGIC YAHAN HAI
            item.optJSONArray("tracks")?.let { tracks ->
                for (j in 0 until tracks.length()) {
                    val track = tracks.optJSONObject(j) ?: continue

                    if (track.optString("kind").equals("captions", ignoreCase = true)) {
                        val rawFile = track.optString("file").replace("\\", "")
                        
                        // YAHAN AADHI LINK THEEK HOGI:
                        val file = if (rawFile.startsWith("/")) "$mainUrl$rawFile" else rawFile
                        
                        val label = track.optString("label").ifEmpty { "Subtitle" }

                        if (file.isNotBlank()) {
                            subtitleCallback(SubtitleFile(label, file))
                        }
                    }
                }
            }

            // 2. VIDEO LINKS KA PREFIX LOGIC YAHAN HAI
            item.optJSONArray("sources")?.let { sources ->
                for (j in 0 until sources.length()) {
                    val source = sources.optJSONObject(j) ?: continue

                    val rawFile = source.optString("file").replace("\\", "")
                    
                    // YAHAN AADHI LINK THEEK HOGI:
                    val file = if (rawFile.startsWith("/")) "$mainUrl$rawFile" else rawFile
                    
                    val label = source.optString("label").ifEmpty { "Video" }

                    if (file.isNotBlank()) {
                        
                        // Quality extract kar lete hain pehle ki tarah (optional)
                        val quality = Regex("""(\d{3,4})p""")
                            .find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?: Qualities.Unknown.value

                        callback(
                            ExtractorLink(
                                source = name,
                                name = label,
                                url = file,
                                referer = "$mainUrl/mobile/home?app=1",
                                quality = quality,
                                type = if (file.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                                headers = mapOf(
                                    "User-Agent" to defaultUa,
                                    "X-Requested-With" to "app.netmirror.netmirrornew",
                                    "Cookie" to "t_hash_t=$cookieVal; ott=nf; hd=on", // Prime/HotStar ke liye nf ko pv/hs kar dena
                                    "Referer" to "$mainUrl/mobile/home?app=1"
                                )
                            )
                        )
                        linksFound++
                    }
                }
            }
        }

        return linksFound > 0
    }

    @Suppress("ObjectLiteralToLambda")
    override fun getVideoInterceptor(
        extractorLink: ExtractorLink
    ): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            // Jab player m3u8 playlist, ts chunks, ya vtt subtitles maange
            if (url.contains(".m3u8") || url.contains(".ts") || url.contains(".vtt")) {
                
                val cookieVal = NetflixMirrorStorage.getCookie().first ?: ""

                val newRequest = request.newBuilder()
                    // YAHAN TACHIYOMI WALE SAME HEADERS HAIN:
                    .header("Cookie", "t_hash_t=$cookieVal; ott=nf; hd=on") // (PrimeVideo mein pv aur HotStar mein hs kar dena)
                    .header("X-Requested-With", "app.netmirror.netmirrornew")
                    .header("Referer", "$mainUrl/mobile/home?app=1")
                    .build()

                return@Interceptor chain.proceed(newRequest)
            }

            chain.proceed(request)
        }
    }


    data class Id(
        val id: String
    )

    data class LoadData(
        val title: String,
        val id: String
    )
}