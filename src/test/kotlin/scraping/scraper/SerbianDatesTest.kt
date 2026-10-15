package com.milinko.scraping.scraper

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerbianDatesTest {

    private val october = LocalDate.of(2026, 10, 15)

    @Test
    fun `full and short dates`() {
        assertEquals(LocalDate.of(2026, 10, 15), parseSerbianDate("15. oktobar 2026.", october))
        assertEquals(LocalDate.of(2026, 10, 21), parseSerbianDate("21. okt", october))
        assertEquals(LocalDate.of(2026, 8, 3), parseSerbianDate("3. avgust 2026.", october))
    }

    @Test
    fun `a date without a year in January is next year when the programme is read in December`() {
        assertEquals(LocalDate.of(2027, 1, 2), parseSerbianDate("2. januar", LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun `not a date`() {
        assertNull(parseSerbianDate("Rezerviši", october))
        assertNull(parseSerbianDate("31. februar 2026.", october))
    }
}
