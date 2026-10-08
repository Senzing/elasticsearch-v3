# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
[markdownlint](https://dlaa.me/markdownlint/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-10-08

### changed in 1.2.0

- Upgraded to `senzing/senzingapi-runtime:3.13.2`; the image's own `g2.jar` is used at runtime
- Upgraded to elasticsearch-java 9.5.4
- Upgraded to Java 25
- Docker image is now a multi-stage build
- Exits with a non-zero status if Senzing fails to start or any entity fails to index
- docker-compose uses the official `postgres` image, `senzing/init-postgresql` and `senzing/senzingapi-tools`

## [1.0.0] - 2023-07-06

### changed in 1.0.0

- Project remade; uses elasticsearch 8.0
- Now only posts all currently loaded G2 entities to elastic for searching

## [0.0.1] - 2018-12-22

### Added to 0.0.1

- Initial prototype
