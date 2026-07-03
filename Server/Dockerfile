# syntax=docker/dockerfile:1.7

FROM amazoncorretto:21-alpine AS build

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew installDist --no-daemon

FROM amazoncorretto:21-alpine

WORKDIR /app

RUN addgroup -S oeims && adduser -S oeims -G oeims \
    && mkdir -p /data \
    && chown -R oeims:oeims /app /data

COPY --from=build --chown=oeims:oeims /app/build/install/oeims-server ./

USER oeims

ENV DATABASE_PATH=/data/oeims.db

EXPOSE 8080

CMD ["./bin/oeims-server"]
