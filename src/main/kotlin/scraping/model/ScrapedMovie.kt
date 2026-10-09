package com.milinko.scraping.model

import kotlinx.serialization.Serializable

/** One movie at one cinema location, as found by a scraper. */
@Serializable
data class ScrapedMovie(
    val title: String,
    val originalTitle: String?,
    val cinemaName: String,         // e.g. "Cineplexx"
    val cinemaLocation: String,     // e.g. "CINEPLEXX 4D DELTA CITY"
    val latitude: Double?,          // for sorting cinemas by distance
    val longitude: Double?,         // for sorting cinemas by distance
    val bookingUrl: String,
    val isUpcoming: Boolean = false,
) {
    companion object {
        /** [cinemaLocation] of a coming-soon movie that no cinema has scheduled yet. */
        const val NO_LOCATION = "Coming soon"
    }
}
