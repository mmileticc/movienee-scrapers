package com.milinko.scraping.scraper

import com.milinko.scraping.agent.ScrapingConfig.USER_AGENT
import com.milinko.scraping.model.ScrapedMovie
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger("RodaCineplexScraper")

/**
 * Roda Cineplex (Belgrade), rodacineplex.com. Same company and platform as Arena Cineplex (same
 * URL scheme `/repertoar/YYYY-MM-DD` and `/uskoro`, same `li.replist` cards), so this scraper is
 * intentionally almost identical to [ArenaCineplexScraper] with a different base URL and location.
 */
class RodaCineplexScraper : CinemaScraper {
    override val cinemaName = "Roda Cineplex"
    private val baseUrl = "http://www.rodacineplex.com"
    private val location = "RODA CINEPLEX"
    private val latitude = 44.77397506094518
    private val longitude = 20.414585469313216

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

            val programmeMovies = parseMoviesFromDoc(programmeDoc, isUpcoming = false)
            if (programmeMovies.isEmpty()) {
                logger.info("[$cinemaName] The programme returned 0 movies - check the selectors.")
            }
            nowShowing.addAll(programmeMovies)
        } catch (e: IOException) {
            logger.warn("[$cinemaName] Failed to scrape the programme: ${e.message}")
        }

        // 2. "Coming soon" section (isUpcoming = true)
        try {
            val comingSoonDoc = Jsoup.connect(comingSoonUrl)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            comingSoon.addAll(parseMoviesFromDoc(comingSoonDoc, isUpcoming = true))
        } catch (e: IOException) {
            logger.warn("[$cinemaName] Failed to scrape the coming-soon section: ${e.message}")
        }

        // 3. De-duplicate within this cinema (same as Arena Cineplex).
        val byTitle = nowShowing.associateBy { it.originalTitle ?: it.title }.toMutableMap()

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
