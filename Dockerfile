# Build stage: JDK and Maven wrapper. Tests run in CI because they need Docker.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies get their own layer, so source-only changes reuse the cache.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp package -DskipTests && cp target/fhirvault-*.jar app.jar

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
