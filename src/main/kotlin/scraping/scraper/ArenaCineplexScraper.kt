package com.milinko.scraping.scraper

import com.milinko.scraping.agent.ScrapingConfig.USER_AGENT
import com.milinko.scraping.model.ScrapedMovie
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger("ArenaCineplexScraper")

/**
 * Arena Cineplex (Novi Sad), arenacineplex.com: server-rendered pages, read with Jsoup.
 * Programme of a day: `/repertoar/YYYY-MM-DD`; coming soon: `/uskoro`. One fixed location.
 */
class ArenaCineplexScraper : CinemaScraper {
    override val cinemaName = "Arena Cineplex"
    private val baseUrl = "http://www.arenacineplex.com"
    private val location = "Arena Cineplex, Bulevar Mihajla Pupina 3, Novi Sad"
    private val latitude = 45.253912026035266
    private val longitude = 19.845361912039472

    override fun scrape(date: LocalDate): List<ScrapedMovie> {
        val nowShowing = mutableListOf<ScrapedMovie>()
        val comingSoon = mutableListOf<ScrapedMovie>()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val programmeUrl = "$baseUrl/repertoar/${date.format(formatter)}"
        val comingSoonUrl = "$baseUrl/uskoro"

        // 1. Current programme (isUpcoming = false)
        try {
            val programmeDoc = Jsoup.connect(programmeUrl)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            nowShowing.addAll(parseMoviesFromDoc(programmeDoc, isUpcoming = false))
        } catch (e: IOException) {
            logger.warn("[$cinemaName] Failed to scrape the programme: ${e.message}")
        }

        // 2. "Coming soon" section (isUpcoming = true)
        try {
            val comingSoonDoc = Jsoup.connect(comingSoonUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()

            comingSoon.addAll(parseMoviesFromDoc(comingSoonDoc, isUpcoming = true))
        } catch (e: IOException) {
            logger.warn("[$cinemaName] Failed to scrape the coming-soon section: ${e.message}")
        }

        // 3. De-duplicate within this cinema: movies already in the programme take priority.
        val byTitle = nowShowing.associateBy { it.originalTitle ?: it.title }.toMutableMap()

        // Add a coming-soon movie only if it is not already in today's programme.
        for (upcoming in comingSoon) {
            val key = upcoming.originalTitle ?: upcoming.title
            if (!byTitle.containsKey(key)) {
                byTitle[key] = upcoming
            } else {
                logger.info("[$cinemaName] [DEDUP] Skipping '$key' from coming soon - already in the programme.")
            }
        }

        return byTitle.values.toList()
    }

    private fun parseMoviesFromDoc(doc: Document, isUpcoming: Boolean): List<ScrapedMovie> {
        val result = mutableListOf<ScrapedMovie>()
        val cards = doc.select("li.replist")

        for (card in cards) {
            val details = card.select("div.col-md-5")
            if (details.isEmpty()) continue

            val title = details.select("h3").text().trim()
            val originalTitleRaw = details.select("p").text().trim()
            var link = details.select("a").attr("href").trim()

            if (link.startsWith("/")) {
                link = baseUrl + link
            }

            val originalTitle = if (originalTitleRaw.isNotEmpty() && originalTitleRaw != title) {
                originalTitleRaw
            } else {
                null
            }

            if (title.isNotEmpty() && link.isNotEmpty()) {
                val section = if (isUpcoming) "COMING SOON" else "PROGRAMME"
                logger.info("[$cinemaName] [$section] Found: $title")

                result.add(
                    ScrapedMovie(
                        title = title,
                        originalTitle = originalTitle,
                        cinemaName = cinemaName,
                        bookingUrl = link,
                        cinemaLocation = location,
                        latitude = latitude,
                        longitude = longitude,
                        isUpcoming = isUpcoming
                    )
                )
            }
        }
        return result
    }
}
