# ============================================================================
# Inkomoko USSD (*311#) - multi-stage build (same layout as loan-service)
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1: Download dependencies (cached unless pom.xml changes)
# ----------------------------------------------------------------------------
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu AS dependencies

RUN apt-get update && \
    apt-get install -y curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:+UseStringDeduplication"

WORKDIR /build

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
RUN chmod +x mvnw

# BuildKit cache mount keeps ~/.m2 between pipeline runs.
RUN --mount=type=cache,target=/root/.m2,id=inkomoko-ussd-m2,sharing=shared \
    ./mvnw dependency:go-offline -B -q && \
    ./mvnw dependency:resolve-plugins -B -q

# ----------------------------------------------------------------------------
# Stage 2: Build application (only rebuilds when source changes)
# ----------------------------------------------------------------------------
FROM dependencies AS builder

ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=192m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:ActiveProcessorCount=1"

COPY src ./src

RUN --mount=type=cache,target=/root/.m2,id=inkomoko-ussd-m2,sharing=shared \
    ./mvnw package -DskipTests -Dmaven.javadoc.skip=true -B -q && \
    mv target/*.jar app.jar

# ----------------------------------------------------------------------------
# Stage 3: Runtime
# ----------------------------------------------------------------------------
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

RUN apt-get update && \
    apt-get install -y curl ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/app.jar app.jar

RUN mkdir -p /opt/tomcat/logs

ENV LOGGING_FILE_NAME=/opt/tomcat/logs/inkomoko-ussd.log
ENV TZ=Africa/Kigali

EXPOSE 8093

# UseContainerSupport + MaxRAMPercentage: size the heap from the container limit, not host RAM.
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=70", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Duser.timezone=Africa/Kigali", \
    "-jar", "app.jar"]
