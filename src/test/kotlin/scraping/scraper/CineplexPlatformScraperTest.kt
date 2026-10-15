package com.milinko.scraping.scraper

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CineplexPlatformScraperTest {

    private val day = LocalDate.of(2026, 10, 15)
    private val doc = Jsoup.parse(
        javaClass.getResource("/showtimes/arena-programme.html")!!.readText(),
        "http://www.arenacineplex.com/repertoar/2026-10-15"
    )

    @Test
    fun `reads every screening with time, hall, price and ticket link`() {
        val movies = ArenaCineplexScraper().parseProgramme(doc, day, now = day.atStartOfDay())

        assertEquals(listOf("U DUBINAMA", "NEMA DOBRIH MUŠKARACA"), movies.map { it.title })
        val first = movies[0]
        assertEquals("Weapons", first.originalTitle)
        assertEquals("http://www.arenacineplex.com/film/4367/U-DUBINAMA", first.bookingUrl)
        assertEquals(listOf("2026-10-15T14:10", "2026-10-15T19:00", "2026-10-16T00:15"), first.showtimes.map { it.startsAt })
        val evening = first.showtimes[1]
        assertEquals("2", evening.hall)
        assertEquals(600, evening.priceRsd)
        assertEquals("http://ulaznice.arenacineplex.com/rs/site/numSale/index/199916", evening.bookingUrl)

        // Same title in both places = no original title; a hall can have a name.
        val second = movies[1]
        assertNull(second.originalTitle)
        assertEquals(520, second.showtimes.single().priceRsd)
        assertEquals("Velika sala", second.showtimes.single().hall)
    }

    @Test
    fun `the format comes from the title`() {
        assertEquals("3D, sinhronizovano", formatFromTitle("ZOOTROPOLIS 2 3D(sinhronizovano) KF"))
        assertEquals("titlovano", formatFromTitle("OSTRVO SEĆANJA (titlovano)"))
        assertEquals("sinhronizovano", formatFromTitle("Ostrvo Sećanja (sinhro.)"))
        assertNull(formatFromTitle("U DUBINAMA"))
    }

    @Test
    fun `screenings that already started are left out`() {
        val movies = ArenaCineplexScraper().parseProgramme(doc, day, now = LocalDateTime.of(2026, 10, 15, 18, 0))
        assertEquals(listOf("2026-10-15T19:00", "2026-10-16T00:15"), movies[0].showtimes.map { it.startsAt })
        assertEquals(emptyList(), movies[1].showtimes)
    }
}
