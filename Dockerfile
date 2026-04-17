FROM eclipse-temurin:25-jre-noble

# Install Chromium system dependencies (needed if Playwright is in the classpath)
RUN apt-get update && apt-get install -y \
    libglib2.0-0 libnss3 libnspr4 libdbus-1-3 libatk1.0-0 \
    libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 libatspi2.0-0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 libpango-1.0-0 \
    libcairo2 libasound2t64 libwayland-client0 \
    && rm -rf /var/lib/apt/lists/*

# Playwright browser path
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright
RUN mkdir -p /opt/playwright

WORKDIR /app

COPY application-api/build/libs/application-api.jar app.jar

# Install Playwright Chromium browser at build time — skip gracefully if not in JAR.
# Playwright is only included when business-browser module is active (cloud-browser-automation branch).
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /tmp/extracted && cd /tmp/extracted && unzip -q /app/app.jar \
    && if ls BOOT-INF/lib/playwright-*.jar 1>/dev/null 2>&1; then \
         echo "Playwright found — installing Chromium" \
         && java -cp "BOOT-INF/lib/*:BOOT-INF/classes" com.microsoft.playwright.CLI install chromium; \
       else \
         echo "Playwright not in JAR — skipping browser install"; \
       fi \
    && rm -rf /tmp/extracted

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
