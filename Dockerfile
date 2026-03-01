FROM eclipse-temurin:21-jre-jammy

# Install Chromium dependencies for Playwright
RUN apt-get update && apt-get install -y \
    libglib2.0-0 libnss3 libnspr4 libdbus-1-3 libatk1.0-0 \
    libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 libatspi2.0-0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 libpango-1.0-0 \
    libcairo2 libasound2 libwayland-client0 \
    && rm -rf /var/lib/apt/lists/*

# Playwright browser path
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright
RUN mkdir -p /opt/playwright

WORKDIR /app

COPY application/build/libs/application.jar app.jar

# Install Playwright Chromium browser at build time (uses the bundled CLI)
RUN java -cp app.jar com.microsoft.playwright.CLI install chromium

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
