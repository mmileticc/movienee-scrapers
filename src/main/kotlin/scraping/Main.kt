package com.milinko.scraping

import com.milinko.scraping.scraper.ArenaCineplexScraper
import com.milinko.scraping.scraper.CineGrandScraper
import com.milinko.scraping.scraper.CineStarScraper
import com.milinko.scraping.scraper.CinemaScraper
import com.milinko.scraping.scraper.CineplexxScraper
import com.milinko.scraping.scraper.MtsDvoranaScraper
import com.milinko.scraping.scraper.RodaCineplexScraper
import com.milinko.scraping.scraper.TuckwoodCineplexScraper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintStream
import java.time.LocalDate
import kotlin.system.exitProcess

private val ALL_SCRAPERS: List<CinemaScraper> = listOf(
    MtsDvoranaScraper(),
    CineplexxScraper(),
    ArenaCineplexScraper(),
    CineGrandScraper(),
    CineStarScraper(),
    RodaCineplexScraper(),
    TuckwoodCineplexScraper(),
)

private val prettyJson = Json { prettyPrint = true }

private const val USAGE = """
Usage: ./gradlew run --args="[options]"

  --cinema <name>   Scrape only this chain (repeatable, case-insensitive), e.g. --cinema arena
  --date <yyyy-MM-dd>  Programme date (default: today)
  --json <file>     Also write all results as JSON to <file>
  --help            Show this help

Chains: MTS Dvorana, Cineplexx, Arena Cineplex, Cine Grand, CineStar, Roda Cineplex, Tuckwood Cineplex
"""

/**
 * Command-line entry point: runs the selected scrapers in parallel, prints every movie and a
 * per-cinema summary, and optionally writes the results to a JSON file.
 */
suspend fun main(args: Array<String>) {
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    System.setErr(PrintStream(System.err, true, "UTF-8"))

    val cinemaFilters = mutableListOf<String>()
    var date = LocalDate.now()
    var jsonFile: File? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--cinema" -> cinemaFilters += args.getOrNull(++i) ?: exitWithUsage("--cinema needs a value")
            "--date" -> date = LocalDate.parse(args.getOrNull(++i) ?: exitWithUsage("--date needs a value"))
            "--json" -> jsonFile = File(args.getOrNull(++i) ?: exitWithUsage("--json needs a value"))
            "--help", "-h" -> { println(USAGE); return }
            else -> exitWithUsage("Unknown option: ${args[i]}")
        }
        i++
    }

    val scrapers = if (cinemaFilters.isEmpty()) ALL_SCRAPERS else ALL_SCRAPERS.filter { scraper ->
        cinemaFilters.any { scraper.cinemaName.contains(it, ignoreCase = true) }
    }
    if (scrapers.isEmpty()) exitWithUsage("No scraper matches ${cinemaFilters.joinToString()}")

    val movies = ScrapingEngineConcurrent(scrapers).runAllScrapers(date)

    movies.sortedWith(compareBy({ it.cinemaName }, { it.cinemaLocation }, { it.title })).forEach { movie ->
        val status = if (movie.isUpcoming) "UPCOMING" else "NOW"
        val original = movie.originalTitle?.let { " ($it)" }.orEmpty()
        println("[$status] ${movie.title}$original | ${movie.cinemaLocation} -> ${movie.bookingUrl}")
    }

    println("\n=== Summary for $date ===")
    scrapers.forEach { scraper ->
        val count = movies.count { it.cinemaName == scraper.cinemaName }
        val note = if (count == 0) "  <- check the log above and scraper-debug/" else ""
        println("- ${scraper.cinemaName}: $count$note")
    }

    jsonFile?.let { file ->
        file.writeText(prettyJson.encodeToString(movies))
        println("\nWrote ${movies.size} movies to ${file.absolutePath}")
    }
}

private fun exitWithUsage(message: String): Nothing {
    System.err.println(message)
    System.err.println(USAGE)
    exitProcess(1)
}
