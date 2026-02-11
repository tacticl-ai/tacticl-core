FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY application/build/libs/application-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
