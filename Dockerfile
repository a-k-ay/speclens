# ---- Stage 1: build the jar (full JDK + Maven wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the build definition first, so dependency downloads are cached as a layer
# and only re-run when pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (GitHub Actions); the image build only packages.
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run (JRE only, non-root) ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 speclens
COPY --from=build /app/target/speclens-*.jar app.jar
# Fictional sample documents, loaded into the demo project when DEMO_SEED=true.
COPY samples/*.pdf samples/*.docx samples/

USER speclens
EXPOSE 8080

# Sized for a 512 MB container (Render free tier):
#  - heap capped at 60% of container memory, leaving room for metaspace, threads and buffers
#  - Serial GC: lowest memory overhead for a single small instance
#  - C1 JIT only: less JIT memory and faster startup (cold starts matter on a free tier)
#  - smaller thread stacks
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxMetaspaceSize=128m"

ENTRYPOINT ["java", "-jar", "app.jar"]
