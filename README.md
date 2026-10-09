# Movienee Scrapers

Kotlin scrapers that collect the current and upcoming movie programme of the major cinema chains
in Serbia and turn seven very different websites into one uniform data model.

**[Project page](https://mmileticc.github.io/movienee-scrapers/)** ·
**[API documentation](https://mmileticc.github.io/movienee-scrapers/docs/)**

## Supported cinemas

| Chain | Locations | Website | Technique |
|---|---|---|---|
| Cineplexx | 7 (Belgrade, Novi Sad, Niš, Kragujevac) | cineplexx.rs | Playwright (React app) |
| Cine Grand | 5 (Belgrade, Niš, Čačak, Šabac) | cinegrand-mcf.rs | Playwright (Nuxt app) |
| CineStar | 4 (Belgrade, Novi Sad, Pančevo, Zrenjanin) | cinestarcinemas.rs | Playwright |
| MTS Dvorana | Belgrade | mtsdvorana.rs | Jsoup |
| Arena Cineplex | Novi Sad | arenacineplex.com | Jsoup |
| Roda Cineplex | Belgrade | rodacineplex.com | Jsoup |
| Tuckwood Cineplex | Belgrade | tuck.rs | Jsoup (WordPress) |

Server-rendered sites are read with plain HTTP + [Jsoup](https://jsoup.org/); JavaScript apps are
rendered in headless Chromium via [Playwright for Java](https://playwright.dev/java/).

## How it works

```
CinemaScraper (interface)          one implementation per chain
        │  scrape(date): List<ScrapedMovie>
        ▼
ScrapingEngineConcurrent           runs all scrapers in parallel (coroutines, Dispatchers.IO);
        │                          a failing scraper only loses its own cinema
        ▼
List<ScrapedMovie>                 title, original title, chain, location, coordinates,
                                   booking URL, now showing / coming soon
```

Design points:

- **One row per (movie, location).** Chains that list movies globally (Cineplexx, Cine Grand) open
  each movie's page to find the locations showing it; chains with per-location pages (CineStar)
  open each movie page only once, no matter how many locations show it.
- **Original titles** are extracted wherever the site has them - they make matching a movie
  against databases such as TMDB far more reliable than local (Serbian) titles.
- **Coordinates** of every location (`locations/CinemaLocations.kt`) allow sorting cinemas by
  distance from the user.
- **Fault isolation.** A changed selector or a timeout never breaks the whole run. When a Playwright
  page fails, an HTML + PNG snapshot is saved to `scraper-debug/`, which usually shows at a glance
  whether the site changed or blocked the request.
- **Identifiable User-Agent** with a link to this repository (`agent/ScrapingConfig.kt`).

## Running

### With Docker (no JDK or browser setup needed)

```bash
docker build -t movienee-scrapers .

# All cinemas, today
docker run --rm --ipc=host movienee-scrapers

# Selected chains, results saved to ./output/results.json on the host
docker run --rm --ipc=host -v "$PWD/output:/output" movienee-scrapers --cinema arena --json results.json
```

The image contains JRE 21 and a Chromium build matching the Playwright version. `--ipc=host` is
recommended by Playwright for Chromium in Docker; `/output` is the working directory, so JSON
results and `scraper-debug/` snapshots land in the mounted folder.

### With Gradle

Requirements: JDK 21.

```bash
# Chromium for the Playwright-based scrapers (once)
./gradlew playwrightCli --args="install chromium"

# All cinemas, today
./gradlew run

# Selected chains, a specific date, results also saved as JSON
./gradlew run --args="--cinema arena --cinema roda --date 2026-10-10 --json results.json"
```

Options: `--cinema <name>` (repeatable, case-insensitive part of the chain name), `--date <yyyy-MM-dd>`
(default today), `--json <file>`, `--help`.

Output format:

```
[NOW] <local title> (<original title>) | <location> -> <booking URL>
[UPCOMING] <local title> (<original title>) | <location> -> <booking URL>

=== Summary for <date> ===
- Arena Cineplex: <number of movies>
- Roda Cineplex: <number of movies>
```

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `SCRAPER_USER_AGENT` | browser string + `MovieneeScrapers/1.0 (+repo link)` | User-Agent for all requests - please put your own contact here if you run the scrapers regularly |
| `PLAYWRIGHT_DEEP_TIMEOUT_MS` | `25000` | Timeout for Cineplexx movie pages |
| `TUCKWOOD_TIMEOUT_MS` | `25000` | Timeout for Tuckwood requests |

## Adding a cinema

Implement `CinemaScraper` (a `cinemaName` and `scrape(date): List<ScrapedMovie>`; it must not throw
for a single failing page), add the location coordinates to `CinemaLocations`, and register the
scraper in `Main.kt`. See the [API documentation](https://mmileticc.github.io/movienee-scrapers/docs/).

```bash
# Generate the API documentation locally -> build/dokka/html/index.html
./gradlew dokkaGenerate
```

## Project structure

```
src/main/kotlin/scraping/
├── Main.kt                      command-line entry point
├── ScrapingEngineConcurrent.kt  runs the scrapers in parallel
├── scraper/                     one scraper per chain + CinemaScraper + debug snapshots
├── model/ScrapedMovie.kt        the uniform output model
├── locations/                   coordinates of every location
└── agent/ScrapingConfig.kt      shared settings (User-Agent)
site/                            project page (GitHub Pages)
dokka/module.md                  front page of the API documentation
```

## Tech stack

Kotlin 2.3 · Coroutines · Jsoup · Playwright for Java · kotlinx.serialization · Logback · Gradle ·
Dokka · Docker · GitHub Actions

## Disclaimer

This is an unofficial, non-commercial project and is not affiliated with any of the cinemas. All
programme data belongs to the respective cinemas, and the booking links lead directly to their own
pages. Websites change - selectors that work today may break tomorrow. If you run the scrapers,
please do so responsibly: rarely (once a day is plenty) and in line with each site's terms of use.

## License

[MIT](LICENSE)
