package com.milinko.scraping.scraper

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HappeningPlatformTest {

    // The shape of a real movie page's payload (Nuxt "devalue": entries refer to others by index).
    private val payload = """
        [["ShallowReactive",1],{"data":2},["ShallowReactive",3],
         {"happening-detail-name-x-sr":4,"happening-projections-name-x-sr":8},
         {"data":5},{"name":6,"originalName":7},"Osvetnici: Uspon Doktora Duma","Avengers: Doomsday",
         {"data":9},[10,18],
         {"id":11,"locationName":12,"hallName":13,"startTime":14,"mediaType":15,"availableSeats":17},
         75335,"mts Dvorana","Sala 6","2026-12-16T00:05:00",{"name":16},"3D Titl",234,
         {"id":19,"locationName":20,"hallName":21,"startTime":22}, 75336,"Cine Grand BIG Čačak","Sala 1","2026-10-15T18:30:00"]
    """.trimIndent()

    private val doc = Jsoup.parse("""<html><body><script type="application/json" id="__NUXT_DATA__">$payload</script></body></html>""")

    @Test
    fun `reads the movie and its screenings from the Nuxt payload`() {
        val page = parseHappeningPage(doc)!!
        assertEquals("Osvetnici: Uspon Doktora Duma", page.name)
        assertEquals("Avengers: Doomsday", page.originalName)
        val first = page.projections.first()
        assertEquals(75335L, first.id)
        assertEquals("Sala 6", first.hallName)
        assertEquals("3D Titl", first.format)
        assertEquals(234, first.availableSeats)
        assertEquals(LocalDateTime.of(2026, 12, 16, 0, 5), first.startTime)
        assertNull(page.projections[1].format)
    }

    @Test
    fun `builds the ticket page of a screening like the site does`() {
        val projection = parseHappeningPage(doc)!!.projections.first()
        assertEquals(
            "https://www.mtsdvorana.rs/u-bioskopu/osvetnici-uspon-doktora-duma/mts-dvorana/sala-6/2026-12-16-0005/75335",
            projectionUrl("https://www.mtsdvorana.rs", "Osvetnici: Uspon Doktora Duma", projection)
        )
        assertEquals("cine-grand-big-cacak", slug("Cine Grand BIG Čačak"))
    }

    @Test
    fun `only screenings in the window that have not started`() {
        val projections = parseHappeningPage(doc)!!.projections
        val inWeek = projections.inWindow(LocalDate.of(2026, 10, 15), days = 7, now = LocalDateTime.of(2026, 10, 15, 12, 0))
        assertEquals(listOf(75336L), inWeek.map { it.id })
        assertEquals(emptyList(), projections.inWindow(LocalDate.of(2026, 10, 15), 7, LocalDateTime.of(2026, 10, 15, 19, 0)).map { it.id })
    }

    @Test
    fun `a page without Nuxt data gives null`() {
        assertNull(parseHappeningPage(Jsoup.parse("<html><body>maintenance</body></html>")))
    }
}
