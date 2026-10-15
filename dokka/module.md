# Module movienee-scrapers

Scrapers for the movie programme of the major cinema chains in Serbia. Each chain's website is
turned into the same model, [com.milinko.scraping.model.ScrapedMovie]: one row per movie and
cinema location, with the original title, coordinates, a booking link and the showtimes of the
next 7 days ([com.milinko.scraping.model.ScrapedShowtime]).

Start with [com.milinko.scraping.scraper.CinemaScraper] (the contract every scraper implements) and
[com.milinko.scraping.ScrapingEngineConcurrent] (runs them in parallel). To support a new cinema,
implement `CinemaScraper` and add it to the list in `Main.kt`.

# Package com.milinko.scraping

The parallel engine and the command-line entry point.

# Package com.milinko.scraping.scraper

One scraper per cinema chain, all over plain HTTP: server-rendered pages with Jsoup, the Nuxt sites
(MTS Dvorana, Cine Grand) from the JSON state in their pages, Cineplexx from its JSON API. Only the
Cineplexx fallback renders the site with Playwright; its failed pages are saved to `scraper-debug/`.

# Package com.milinko.scraping.model

The uniform output model.

# Package com.milinko.scraping.locations

Coordinates of every supported cinema location.

# Package com.milinko.scraping.agent

Settings shared by all scrapers, such as the User-Agent.
