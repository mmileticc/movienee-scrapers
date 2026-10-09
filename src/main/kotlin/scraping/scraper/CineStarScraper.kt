package com.milinko.scraping.scraper

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.milinko.scraping.agent.ScrapingConfig.USER_AGENT
import com.milinko.scraping.locations.CinemaLocations
import com.milinko.scraping.model.ScrapedMovie
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("CineStarScraper")

/**
 * CineStar (4 locations), cinestarcinemas.rs: classic server-rendered pages (jQuery + Bootstrap),
 * currently read with Playwright.
 *
 * Unlike Cine Grand (organized per movie), every location has its own page (e.g. "/novi-sad-big")
 * with its complete programme (title, genre, runtime, screenings for about three weeks). The
 * original title is not on those cards, only on the movie's own page ("Izvorni naslov" inside
 * ".actors-producer"), so that page is opened once per unique movie - not per (movie, location),
 * since the same movie often plays at all four locations.
 *
 * The location pages were taken from the "Izaberi svoj CineStar" dropdown (`<select id="kino">`)
 * on cinestarcinemas.rs/program-svi-bioskopi.
 */
class CineStarScraper : CinemaScraper {
    override val cinemaName = "CineStar"
    private val baseUrl = "https://cinestarcinemas.rs"

    // Location page path -> location name (the key used in CinemaLocations).
    private val locations = mapOf(
        "/beograd-concept-cinema-ada-mall" to "CINESTAR BEOGRAD ADA MALL",
        "/novi-sad-big" to "CINESTAR NOVI SAD BIG",
        "/pancevo-big" to "CINESTAR PANČEVO BIG",
        "/zrenjanin-big" to "CINESTAR ZRENJANIN BIG"
    )

    override fun scrape(date: LocalDate): List<ScrapedMovie> {
        val rawMovies = mutableListOf<ScrapedMovie>()
        var finalMovies: List<ScrapedMovie> = emptyList()

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

                try {
                    for ((path, locationName) in locations) {
                        try {
                            val url = "$baseUrl$path"
                            logger.info("[$cinemaName] [$locationName] Opening: $url")
                            page.navigate(url)
                            page.waitForSelector(".movie-item", Page.WaitForSelectorOptions().setTimeout(15000.0))

                            val doc = Jsoup.parse(page.content())
                            val locationMovies = parseMoviesFromDoc(doc, locationName)
                            logger.info("[$cinemaName] [$locationName] Movies found: ${locationMovies.size}")
                            rawMovies.addAll(locationMovies)
                        } catch (e: Exception) {
                            logger.warn("[$cinemaName] [$locationName] Failed to scrape: ${e.message}")
                            saveDebugSnapshot(page, cinemaName, path.trim('/'))
                        }
                    }

                    finalMovies = try {
                        enrichWithOriginalTitles(context, rawMovies)
                    } catch (e: Exception) {
                        // If enrichment fails completely, return the movies without original
                        // titles rather than lose the whole run over a secondary detail.
                        logger.warn("[$cinemaName] Adding original titles failed - returning the movies without them: ${e.message}")
                        rawMovies
                    }
                } finally {
                    context.close()
                    browser.close()
                }
            } catch (e: Exception) {
                logger.warn("[$cinemaName] Failed to start or prepare the browser - nothing scraped this run: ${e.message}")
                finalMovies = rawMovies
            }
        }

        return finalMovies
    }

    private fun parseMoviesFromDoc(doc: Document, locationName: String): List<ScrapedMovie> {
        val result = mutableListOf<ScrapedMovie>()
        val coordinates = CinemaLocations.findCoordinates(locationName)

        for (card in doc.select(".movie-item")) {
            val bookingUrl = card.select("a.poster-wrapper").first()?.attr("href")?.trim().orEmpty()
            val heading = card.select(".movie-desc h2").first()

            if (bookingUrl.isEmpty() || heading == null) {
                continue
            }

            // A ".filmlabel" badge in the heading marks a coming-soon movie; it is removed from
            // the title text.
            val labelElement = heading.select(".filmlabel").first()
            val isUpcoming = labelElement != null
            labelElement?.remove()
            val title = heading.text().trim()

            if (title.isEmpty()) {
                continue
            }

            result.add(
                ScrapedMovie(
                    title = title,
                    originalTitle = null,
                    cinemaName = cinemaName,
                    cinemaLocation = locationName,
                    latitude = coordinates?.lat,
                    longitude = coordinates?.lon,
                    bookingUrl = bookingUrl,
                    isUpcoming = isUpcoming
                )
            )
        }

        return result
    }

    /**
     * Adds the original title ("Izvorni naslov") from each movie's page. The same movie (same
     * numeric id at the end of bookingUrl, e.g. ".../hari-poter-i-kamen-mudrosti/6579") appears once
     * per location, so titles are cached by that id and each page is read only once.
     */
    private fun enrichWithOriginalTitles(context: BrowserContext, movies: List<ScrapedMovie>): List<ScrapedMovie> {
        val originalTitlesById = mutableMapOf<String, String?>()

        return movies.map { movie ->
            val movieId = movie.bookingUrl.substringAfterLast("/")
            val originalTitle = if (originalTitlesById.containsKey(movieId)) {
                originalTitlesById.getValue(movieId)
            } else {
                logger.info("[$cinemaName] Reading the original title of: ${movie.title}")
                val title = extractOriginalTitle(context, movie.bookingUrl)
                originalTitlesById[movieId] = title
                title
            }
            if (originalTitle != null) movie.copy(originalTitle = originalTitle) else movie
        }
    }

    private fun extractOriginalTitle(context: BrowserContext, movieUrl: String): String? {
        if (movieUrl.isEmpty()) return null

        val detailPage = context.newPage()
        return try {
            detailPage.route("**/*") { route ->
                val resourceType = route.request().resourceType()
                if (resourceType == "image" || resourceType == "font" || resourceType == "media") {
                    route.abort()
                } else {
                    route.resume()
                }
            }

            detailPage.navigate(movieUrl)
            detailPage.waitForSelector(".movie-desc h1", Page.WaitForSelectorOptions().setTimeout(10000.0))

            val doc = Jsoup.parse(detailPage.content())

            // The site labels the field "Izvorni naslov" (original title).
            doc.select(".movie-detail-item:has(span.gray:contains(Izvorni naslov)) span:not(.gray)")
                .first()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.warn("[$cinemaName] Failed to read the original title from $movieUrl: ${e.message}")
            null
        } finally {
            detailPage.close()
        }
    }
}
