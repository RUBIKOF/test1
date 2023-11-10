package com.example


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.ArrayList


class HentaiLaProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override var mainUrl = "https://www4.hentaila.com/"
    override var name = "HentaiLA"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
            TvType.NSFW
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
                Pair(
                        "$mainUrl/genero/tetonas",
                        "Tetonas"
                ),
                Pair(
                        "$mainUrl/genero/incesto",
                        "Incesto"
                ),
                Pair(
                        "$mainUrl/genero/milfs",
                        "Milf"
                ),
        )

        val items = ArrayList<HomePageList>()
        val isHorizontal = true
        items.add(
                HomePageList(
                        "Últimos episodios",
                        app.get(mainUrl).document.select("#aa-wp > div > section.section.episodes > div > article").map {

                            val title = it.selectFirst("h2")?.text()
                            val dubstat = if (title!!.contains("Latino") || title.contains("Castellano"))
                                DubStatus.Dubbed else DubStatus.Subbed
                            val poster = mainUrl +
                                    it.selectFirst("img")?.attr("src")
                            val epRegex = Regex("/(\\d+)/|/especial/|/ova/")
                            val x = it.selectFirst("a")?.attr("href")?.replace("/ver/","hentai-")
                            val z = x?.substring(x.lastIndexOf("-")).toString()
                            val url = mainUrl + x?.replace(z,"")
                            val epNum =
                                    it.selectFirst("span")?.text()?.replace("Episodio ", "")?.toIntOrNull()

                            newAnimeSearchResponse(title, url) {
                                this.posterUrl = poster
                                addDubStatus(dubstat, epNum)
                            }
                        }, isHorizontal)
        )
        urls.apmap { (url, name) ->
            val soup = app.get(url).document
            val home = soup.select(".section article").map {
                val title = it.selectFirst("h2")?.text()
                val poster = mainUrl + it.selectFirst("img")?.attr("src")
                AnimeSearchResponse(
                        title!!,
                        fixUrl(it.selectFirst("a")?.attr("href") ?: ""),
                        this.name,
                        TvType.Anime,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                                DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                )
            }
            items.add(HomePageList(name, home))
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class MainSearch(
            @JsonProperty("animes") val animes: List<Animes>,
            @JsonProperty("anime_types") val animeTypes: AnimeTypes
    )

    data class Animes(
            @JsonProperty("id") val id: String,
            @JsonProperty("slug") val slug: String,
            @JsonProperty("title") val title: String,
            @JsonProperty("image") val image: String,
            @JsonProperty("synopsis") val synopsis: String,
            @JsonProperty("type") val type: String,
            @JsonProperty("status") val status: String,
            @JsonProperty("thumbnail") val thumbnail: String
    )
    data class Searching(
            @JsonProperty("id") val id: String,
            @JsonProperty("title") val title: String,
            @JsonProperty("type") val type: String,
            @JsonProperty("slug") val slug: String
    )

    data class AnimeTypes(
            @JsonProperty("TV") val TV: String,
            @JsonProperty("OVA") val OVA: String,
            @JsonProperty("Movie") val Movie: String,
            @JsonProperty("Special") val Special: String,
            @JsonProperty("ONA") val ONA: String,
            @JsonProperty("Music") val Music: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val main = app.get("$mainUrl/api/search?value=$query").text
        //val json = parseJson<MainSearch>(main)
        val json = parseJson<ArrayList<Searching>>(main)
        return json.apmap {
            val title = it.title
            val href = "$mainUrl/hentai-${it.slug}"
            val image = mainUrl + "uploads/portadas/${it.id}.jpg"
            AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    image,
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = mainUrl + doc.selectFirst("#aa-wp > div > section > article > div.h-thumb > figure > img")?.attr("src")
        val title = doc.selectFirst(".h-title")?.text()
        val type = "OVA"
        val description = doc.selectFirst(".h-content > p")?.text()
        val genres = doc.select(".genres > a")
                .map { it.text() }
        val status = when (doc.selectFirst(".status-off")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "Concluido" -> ShowStatus.Completed
            else -> null
        }

        //Espacio Prueba
        val test = doc.select("article.hentai.episode.sm").size
        val x = doc.select(".episodes-list a").attr("href")
        val episodes1 = java.util.ArrayList<Episode>()
        val z = x?.substring(x.lastIndexOf("-")).toString()
        val n = x?.replace(z,"")

        for(i in 1..test) {
             val ff = doc.select(".episodes-list article:nth-child("+((test+1)-i)+") img").attr("src")
            val zz = mainUrl.removeSuffix("/")+ff
            val link = "${
                //url.removeSuffix("/")}/$it"
                mainUrl.removeSuffix("/")+n}-"+i
            val ep = Episode(
                    link,
                    posterUrl = zz
            )
            episodes1.add(ep)
        }

        //Fin espacio prueba

        return newAnimeLoadResponse(title!!, url, TvType.Others) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes1)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    data class Nozomi(
            @JsonProperty("file") val file: String?
    )

    private fun streamClean(
            name: String,
            url: String,
            referer: String,
            quality: String?,
            callback: (ExtractorLink) -> Unit,
            m3u8: Boolean
    ): Boolean {
        callback(
                ExtractorLink(
                        name,
                        name,
                        url,
                        referer,
                        getQualityFromName(quality),
                        m3u8
                )
        )
        return true
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("var videos = [[")) {
                val videos = script.data().replace("\\/", "/")
                fetchUrls(videos).map {
                    it.replace("https://ok.ru", "http://ok.ru")
                }.apmap {
                    loadExtractor(it, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}