ARG BASE_IMAGE=senzing/senzingapi-runtime:3.13.2@sha256:0b81ebfa328ff27f548ccbf769940de3dac3dbd223bc4818e3897c5497887910
ARG BUILDER_IMAGE=maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976

# -----------------------------------------------------------------------------
# Stage: builder
# -----------------------------------------------------------------------------

# The jar is platform-independent, so build it once on the build platform instead of under emulation.

FROM --platform=$BUILDPLATFORM ${BUILDER_IMAGE} AS builder

COPY elasticsearch /build
WORKDIR /build

RUN mvn -B clean package

# -----------------------------------------------------------------------------
# Stage: final
# -----------------------------------------------------------------------------

FROM ${BASE_IMAGE}

ENV REFRESHED_AT=2026-10-08

LABEL Name="senzing/elasticsearch-v3" \
      Maintainer="support@senzing.com" \
      Version="1.2.0"

# Run as "root" for system installation.

USER root

RUN apt-get update \
  && apt-get -y install --no-install-recommends \
      openjdk-25-jre-headless \
  && apt-get -y clean \
  && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/g2elasticsearch-1.2.0.jar /app/

HEALTHCHECK CMD test -f /app/g2elasticsearch-1.2.0.jar

USER 1001

WORKDIR /app
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "g2elasticsearch-1.2.0.jar"]
