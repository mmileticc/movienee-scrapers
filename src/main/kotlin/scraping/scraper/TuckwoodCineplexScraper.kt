package com.milinko.scraping.scraper

import com.milinko.scraping.agent.ScrapingConfig.USER_AGENT
import com.milinko.scraping.model.ScrapedMovie
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("TuckwoodCineplexScraper")

/**
 * Tuckwood Cineplex (Kneza Miloša 7a, Belgrade), tuck.rs: a server-rendered WordPress site
 * (WPBakery + the "Amy Movie" plugin), so - like Arena/Roda - plain Jsoup is enough.
 *
 * Movie cards (".amy-movie-item"):
 * - the title is always "Serbian title / Original title" in "h3.amy-movie-field-title a"; it is
 *   split on " / " (with spaces) so a title containing a bare "/" is not cut;
 * - the link to the movie page is the same href as on the title/poster ("/amy_movie/.../").
 *
 * One fixed location.
 */
class TuckwoodCineplexScraper : CinemaScraper {
    override val cinemaName = "Tuckwood Cineplex"
    private val baseUrl = "https://www.tuck.rs"
    private val location = "TUCKWOOD CINEPLEX"
    // Approximate coordinates of Kneza Miloša 7a, Belgrade.
    private val latitude = 44.80984832581882
    private val longitude = 20.465467427357588

    private val timeoutMs = (System.getenv("TUCKWOOD_TIMEOUT_MS")?.toIntOrNull() ?: 25000)

    override fun scrape(date: LocalDate): List<ScrapedMovie> {
        val nowShowing = mutableListOf<ScrapedMovie>()
        val comingSoon = mutableListOf<ScrapedMovie>()

        val programmeUrl = "$baseUrl/repertoar/"
        val comingSoonUrl = "$baseUrl/uskoro/"

        // 1. Current programme (isUpcoming = false)
        val programmeDoc = fetchWithRetry(programmeUrl, "PROGRAMME")
        if (programmeDoc != null) {
            val programmeMovies = parseMoviesFromDoc(programmeDoc, isUpcoming = false)
            if (programmeMovies.isEmpty()) {
                logger.info("[$cinemaName] The programme returned 0 movies - check the selectors.")
            }
            nowShowing.addAll(programmeMovies)
        }

        // 2. "Coming soon" section (isUpcoming = true)
        val comingSoonDoc = fetchWithRetry(comingSoonUrl, "COMING SOON")
        if (comingSoonDoc != null) {
            comingSoon.addAll(parseMoviesFromDoc(comingSoonDoc, isUpcoming = true))
        }

        // 3. De-duplicate within this cinema (same as Arena/Roda Cineplex).
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

    /**
     * Jsoup GET with one retry: a single "Read timed out" on this slower server should not leave
     * the cinema without data for the whole day. Returns null only if both attempts fail; the
     * caller then skips that section instead of throwing.
     */
    private fun fetchWithRetry(url: String, section: String): Document? {
        val maxAttempts = 2

        repeat(maxAttempts) { index ->
            val attempt = index + 1
            try {
                return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMs)
                    .get()
            } catch (e: IOException) {
                val suffix = if (attempt < maxAttempts) " - retrying" else ""
                logger.warn("[$cinemaName] [$section] Request failed (attempt $attempt/$maxAttempts, timeout ${timeoutMs}ms): ${e.message}$suffix")
            }
        }

        return null
    }

    private fun parseMoviesFromDoc(doc: Document, isUpcoming: Boolean): List<ScrapedMovie> {
        val result = mutableListOf<ScrapedMovie>()
        val cards = doc.select(".amy-movie-item")

        for (card in cards) {
            val titleLink = card.select("h3.amy-movie-field-title a").first()
            val fullTitle = titleLink?.text()?.trim().orEmpty()
            var link = titleLink?.attr("href")?.trim().orEmpty()

            if (link.isEmpty()) {
                link = card.select(".amy-movie-item-poster a").first()?.attr("href")?.trim().orEmpty()
            }

            if (fullTitle.isEmpty() || link.isEmpty()) continue

            val title = fullTitle.substringBeforeLast(" / ").trim()
            val originalTitle = fullTitle.substringAfterLast(" / ").trim()
                .takeIf { it.isNotEmpty() && it != title }

            if (title.isEmpty()) continue

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
        return result
    }
}
