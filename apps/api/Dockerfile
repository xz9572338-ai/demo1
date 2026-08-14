FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system openplatform && useradd --system --gid openplatform --home-dir /app openplatform
COPY --chown=openplatform:openplatform target/api-*.jar app.jar
USER openplatform
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
