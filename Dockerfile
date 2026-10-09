# ---- Build stage: compile and create a runnable distribution (bin/ + lib/) ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon --version
COPY src/ src/
RUN ./gradlew --no-daemon installDist

# ---- Runtime stage: JRE + Chromium for the Playwright-based scrapers ----
FROM eclipse-temurin:21-jre-noble
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
COPY --from=build /src/build/install/movienee-scrapers /app
# The Playwright CLI from the same library version installs a matching Chromium plus the
# system libraries it needs.
RUN java -cp "/app/lib/*" com.microsoft.playwright.CLI install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*
# Relative paths (--json results.json, scraper-debug/) end up here; mount a folder to keep them.
WORKDIR /output
ENTRYPOINT ["/app/bin/movienee-scrapers"]
