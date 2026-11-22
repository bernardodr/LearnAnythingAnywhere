# The laa-server image: the Spring Boot server on a Java 21 runtime, run as a user without root rights.
#
# Built from the repository root: `docker build -f deploy/server.Dockerfile .` (deploy.sh and the deploy workflow do
# this for you). server.Dockerfile.dockerignore lets in only the server's sources, so `.env` and other secrets can
# never reach the image. The tests don't run here: they run before a change is merged (`./mvnw test`, and CI).
#
# Settings all come from the environment at run time (deploy/compose.yml passes the server's .env), so one image
# serves every environment.

# ---- Build: compile the jar, then split it into layers so a code change only replaces the small top layer.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build
COPY server/mvnw server/pom.xml ./
COPY server/.mvn .mvn
# Dependencies first, in their own layer and cache, so they're downloaded again only when pom.xml changes.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY server/src/main src/main
# maven.test.skip: the image has no test sources, so tests are neither compiled nor run.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -Dmaven.test.skip=true package \
 && cp target/server-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Run: only the Java runtime and the layered app.
FROM eclipse-temurin:21-jre-noble
# A fixed, unprivileged user: the server needs no rights beyond reading its own files.
RUN groupadd --system --gid 10001 laa && useradd --system --uid 10001 --gid laa --no-create-home laa
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER laa
EXPOSE 8080
# The heap is sized from the container's memory limit (compose.yml), leaving a quarter for everything else in the
# JVM. Running out of memory ends the process, so Docker restarts it, rather than leaving a half-working server.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
