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

# Download OpenTelemetry Java agent for automatic instrumentation
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.25.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
RUN chmod 644 /app/opentelemetry-javaagent.jar

COPY application-api/build/libs/application-api.jar app.jar
# Note: Playwright Chromium install removed — business-browser module not active.
# System libs above are kept so the image is ready when it's re-enabled.

EXPOSE 8080

# Attach OpenTelemetry agent with -javaagent flag
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
