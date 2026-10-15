package com.milinko.scraping.scraper

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TuckwoodCineplexScraperTest {

    private val doc = Jsoup.parse(javaClass.getResource("/showtimes/tuckwood-programme.html")!!.readText())

    @Test
    fun `reads the screenings of every day with the hall names`() {
        val movies = TuckwoodCineplexScraper().parseMoviesFromDoc(
            doc, isUpcoming = false, date = LocalDate.of(2026, 10, 15), now = LocalDateTime.of(2026, 10, 15, 14, 0)
        )
        val movie = movies.single()
        assertEquals("U Dubinama", movie.title)
        assertEquals("Whalefall", movie.originalTitle)
        // 13:40 on the 15th already started.
        assertEquals(listOf("2026-10-15T20:15", "2026-10-16T17:00"), movie.showtimes.map { it.startsAt })
        assertEquals(listOf("Lauren Bacall", "James Dean"), movie.showtimes.map { it.hall })
        assertEquals("http://ulaznice.tuck.rs/rs/site/repertoireDetail/index/2233", movie.showtimes[0].bookingUrl)
    }
}
