package com.milinko.scraping.scraper

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.milinko.scraping.agent.ScrapingConfig.USER_AGENT
import com.milinko.scraping.locations.CinemaLocations
import com.milinko.scraping.model.ScrapedMovie
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("CineGrandScraper")

/** What a Cine Grand movie page provides: the original title and the locations showing it. */
data class CineGrandDeepDetails(
    val originalTitle: String?,
    val locations: List<String>
)

/**
 * Cine Grand (several locations), cinegrand-mcf.rs: a Nuxt app rendered with Playwright.
 * The programme (`/u-bioskopu`) and coming soon (`/uskoro`) list movies; each movie's page lists
 * the locations showing it, so one row is produced per (movie, location).
 */
class CineGrandScraper : CinemaScraper {
    override val cinemaName = "Cine Grand"
    private val baseUrl = "https://cinegrand-mcf.rs"

    override fun scrape(date: LocalDate): List<ScrapedMovie> {
        val nowShowing = mutableListOf<ScrapedMovie>()
        val comingSoon = mutableListOf<ScrapedMovie>()

        val programmeUrl = "$baseUrl/u-bioskopu"
        val comingSoonUrl = "$baseUrl/uskoro"

        logger.info("[$cinemaName] Starting the Playwright driver...")

        Playwright.create().use { playwright ->
            try {
                val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
                val context = browser.newContext(Browser.NewContextOptions().setViewportSize(1920, 1080))
                val page = context.newPage()

                page.setExtraHTTPHeaders(mapOf("User-Agent" to USER_AGENT))

                // Block images, fonts and media for speed.
                page.route("**/*") { route ->
                    val resourceType = route.request().resourceType()
                    if (resourceType == "image" || resourceType == "font" || resourceType == "media") {
                        route.abort()
                    } else {
                        route.resume()
                    }
                }

                // 1. Current programme (/u-bioskopu)
                try {
                    logger.info("[$cinemaName] [PROGRAMME] Opening: $programmeUrl")
                    page.navigate(programmeUrl)
                    page.waitForSelector("a.group.block", Page.WaitForSelectorOptions().setTimeout(15000.0))

                    val programmeDoc = Jsoup.parse(page.content())
                    nowShowing.addAll(
                        parseMoviesFromDoc(context, programmeDoc, isUpcoming = false, alreadyScrapedLinks = emptySet())
                    )
                } catch (e: Exception) {
                    logger.warn("[$cinemaName] Failed to scrape the current programme: ${e.message}")
                    // Save exactly what the browser saw when it failed; otherwise nobody knows
                    // whether a selector changed, the site blocked the bot, or something else.
                    saveDebugSnapshot(page, cinemaName, "programme")
                }

                val programmeLinks = nowShowing.map { it.bookingUrl.substringBefore("?") }.toSet()

                // 2. "Coming soon" section (/uskoro)
                try {
                    logger.info("[$cinemaName] [COMING SOON] Opening: $comingSoonUrl")
                    page.navigate(comingSoonUrl)
                    page.waitForSelector("a.group.block", Page.WaitForSelectorOptions().setTimeout(15000.0))

                    val comingSoonDoc = Jsoup.parse(page.content())
                    comingSoon.addAll(
                        parseMoviesFromDoc(context, comingSoonDoc, isUpcoming = true, alreadyScrapedLinks = programmeLinks)
                    )
                } catch (e: Exception) {
                    logger.warn("[$cinemaName] Failed to scrape the coming-soon section: ${e.message}")
                    saveDebugSnapshot(page, cinemaName, "coming-soon")
                } finally {
                    context.close()
                    browser.close()
                }
            } catch (e: Exception) {
                logger.warn("[$cinemaName] Failed to start or prepare the browser - nothing scraped this run: ${e.message}")
            }
        }

        // 3. De-duplicate per (movie, location).
        val byKey = nowShowing.associateBy { (it.originalTitle ?: it.title) + it.cinemaLocation }.toMutableMap()
        val titlesPlayingToday = nowShowing.map { it.originalTitle ?: it.title }.toSet()

        for (upcoming in comingSoon) {
            val titleKey = upcoming.originalTitle ?: upcoming.title
            val fullKey = titleKey + upcoming.cinemaLocation

            if (upcoming.cinemaLocation == ScrapedMovie.NO_LOCATION) {
                // A coming-soon movie without a location is added only if it plays nowhere today.
                if (!titlesPlayingToday.contains(titleKey)) {
                    byKey[fullKey] = upcoming
                }
            } else {
                if (!byKey.containsKey(fullKey)) {
                    byKey[fullKey] = upcoming
                }
            }
        }

        return byKey.values.toList()
    }

    private fun parseMoviesFromDoc(
        context: BrowserContext,
        doc: Document,
        isUpcoming: Boolean,
        alreadyScrapedLinks: Set<String>
    ): List<ScrapedMovie> {
        val result = mutableListOf<ScrapedMovie>()
        val cards = doc.select("a.group.block")
        val section = if (isUpcoming) "COMING SOON" else "PROGRAMME"

        logger.info("[$cinemaName] [$section] Movies on the page: ${cards.size}")

        for (card in cards) {
            var movieLink = card.attr("href").trim()
            val title = card.select("h3").first()?.text()?.trim() ?: ""

            if (movieLink.startsWith("/")) {
                movieLink = baseUrl + movieLink
            }

            val cleanUrl = movieLink.substringBefore("?")

            if (title.isNotEmpty() && movieLink.isNotEmpty()) {
                if (isUpcoming && alreadyScrapedLinks.contains(cleanUrl)) {
                    logger.info("[$cinemaName] [$section] Skipping the detail request for: $title (already scraped from the programme)")
                    continue
                }

                logger.info("[$cinemaName] [$section] Opening details for: $title")
                val deepDetails = scrapeDeepDetails(context, movieLink)

                // One row per location found on the movie page.
                if (deepDetails.locations.isNotEmpty()) {
                    for (cinemaLocation in deepDetails.locations) {
                        val coordinates = CinemaLocations.findCoordinates(cinemaLocation)

                        result.add(
                            ScrapedMovie(
                                title = title,
                                originalTitle = deepDetails.originalTitle,
                                cinemaName = cinemaName,
                                cinemaLocation = cinemaLocation,
                                latitude = coordinates?.lat,
                                longitude = coordinates?.lon,
                                bookingUrl = movieLink,
                                isUpcoming = isUpcoming
                            )
                        )
                    }
                } else {
                    result.add(
                        ScrapedMovie(
                            title = title,
                            originalTitle = deepDetails.originalTitle,
                            cinemaName = cinemaName,
                            cinemaLocation = ScrapedMovie.NO_LOCATION,
                            latitude = null,
                            longitude = null,
                            bookingUrl = movieLink,
                            isUpcoming = isUpcoming
                        )
                    )
                }
            }
        }
        return result
    }

    private fun scrapeDeepDetails(context: BrowserContext, movieUrl: String): CineGrandDeepDetails {
        if (movieUrl.isEmpty()) return CineGrandDeepDetails(null, emptyList())

        val maxAttempts = 2

        repeat(maxAttempts) { index ->
            val attempt = index + 1
            val foundLocations = mutableListOf<String>()
            var originalTitle: String? = null
            val detailPage = context.newPage()

            try {
                detailPage.route("**/*") { route ->
                    val resourceType = route.request().resourceType()
                    if (resourceType == "image" || resourceType == "font" || resourceType == "media") {
                        route.abort()
                    } else {
                        route.resume()
                    }
                }

                detailPage.navigate(movieUrl)
                detailPage.waitForLoadState(LoadState.DOMCONTENTLOADED)
                detailPage.waitForSelector(".hf-original", Page.WaitForSelectorOptions().setTimeout(10000.0))
                try {
                    detailPage.waitForSelector(".hp-raspon-cname", Page.WaitForSelectorOptions().setTimeout(3500.0))
                } catch (e: Exception) {
                    // Ignored - the movie probably has no screenings scheduled yet.
                }

                val doc = Jsoup.parse(detailPage.content())

                // Original title
                doc.select(".hf-original").first()?.let { originalTitle = it.text().trim() }

                // Every location with screenings is a ".hp-raspon-cinema" block with a
                // ".hp-raspon-cname" heading; each becomes its own row.
                for (cinema in doc.select(".hp-raspon-cname")) {
                    val cinemaLocation = cinema.text().trim()
                    if (cinemaLocation.isNotEmpty() && !foundLocations.contains(cinemaLocation)) {
                        foundLocations.add(cinemaLocation)
                    }
                }

                return CineGrandDeepDetails(originalTitle, foundLocations)
            } catch (e: Exception) {
                val suffix = if (attempt < maxAttempts) " - retrying" else ""
                logger.warn("[$cinemaName] Failed to read $movieUrl (attempt $attempt/$maxAttempts): ${e.message}$suffix")
            } finally {
                detailPage.close()
            }
        }

        return CineGrandDeepDetails(null, emptyList())
    }
}
