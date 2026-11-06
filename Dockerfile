
# ============================================================================
# Inkomoko USSD (*311#) - multi-stage build
# ============================================================================

# Stage 0: React Admin Portal
FROM node:20-alpine AS portal

WORKDIR /fe

COPY frontend/package.json frontend/package-lock.json ./

RUN --mount=type=cache,target=/root/.npm,id=inkomoko-ussd-npm,sharing=shared \
    npm ci --no-audit --no-fund

COPY frontend/ ./

RUN PORTAL_OUT_DIR=/fe/dist npm run build


# Stage 1: Download Maven dependencies
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu AS dependencies

ENV MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:+UseStringDeduplication"

WORKDIR /build

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

RUN chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2,id=inkomoko-ussd-m2,sharing=shared \
    ./mvnw dependency:go-offline -B -q && \
    ./mvnw dependency:resolve-plugins -B -q


# Stage 2: Build application
FROM dependencies AS builder

ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=192m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:ActiveProcessorCount=1"

COPY src ./src

COPY --from=portal /fe/dist ./src/main/resources/static/portal

RUN --mount=type=cache,target=/root/.m2,id=inkomoko-ussd-m2,sharing=shared \
    ./mvnw package -DskipTests -Dmaven.javadoc.skip=true -B -q && \
    mv target/*.jar app.jar


# Stage 3: Runtime
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

WORKDIR /app

COPY --from=builder /build/app.jar app.jar

RUN mkdir -p /opt/tomcat/logs

ENV LOGGING_FILE_NAME=/opt/tomcat/logs/inkomoko-ussd.log
ENV TZ=Africa/Kigali

EXPOSE 8093

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=70", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Duser.timezone=Africa/Kigali", \
    "-jar", "app.jar"]
