# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules
./gradlew assembleRelease

# Build a specific module
./gradlew :common:assembleRelease

# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :common:test

# Generate the Central Portal deployment bundle (used before publishing)
./gradlew :common:zipDeploymentBundle

# Publish a module to Sonatype Central Portal (requires ossrhUsername/ossrhPassword in local.properties)
./gradlew :common:publishToCentralPortal
```

Configuration cache is enabled (`gradle.properties`). If builds behave unexpectedly, run `./gradlew --no-configuration-cache` to rule out cache issues.

## Project Architecture

**LunaModules** is a multi-module Android library project publishing to `com.lunacattus.android`. All modules are Android libraries targeting compileSdk 36.1, minSdk 31, Java 17.

### Module Dependency Graph

```
common (1.0.2)   ←  no internal dependencies
logger (1.1.0)   ←  no internal dependencies (standalone)
network (1.0.0)  ←  no internal dependencies (standalone)
```

Each module is independently publishable. There are no inter-module dependencies — each module is self-contained.

### Modules

- **common** — Core utilities shared across Luna projects. Contains `SafeCoroutine` (structured concurrency wrappers with `launchSafe`, `asyncSafe`, `cancelSafe` that automatically catch and log unhandled exceptions), `CommonLog` (pluggable log channel — call `setLogger()` to redirect logs through a custom pipeline), and extension functions for `Context`, `String`, `DateTime`, `File`, `View`, `Assets`, and `Device`.

- **logger** — Standalone logging library. Supports Logcat output, file persistence with automatic rotation and size-based cleanup, Box-style bordered log printing, and automatic TAG inference from the calling class name (via stack trace inspection). Thread-safe: all file I/O is delegated to a single-thread executor.

- **network** — HTTP and WebSocket client library built on OkHttp 5.x. `HttpManager` provides `post`/`get` returning `Result<String>` with configurable timeouts via `HttpConfig`. `WebSocketManager` implements `IWebSocketClient` with Kotlin coroutine `StateFlow`/`SharedFlow` for state and event observation, exponential-backoff auto-reconnect, configurable heartbeat/ping, and a sealed-interface event model (`WebSocketEvent` / `WebSocketState`). `RequestIdGenerator` produces ULID-based request IDs with a device-code prefix.

### Build Logic (build-logic/)

The `build-logic/convention` module defines a custom Gradle plugin (`lunacattus.android.library.publish`) that each library module applies. It:

1. Configures `maven-publish` and `signing` plugins
2. Creates a `release` Maven publication from the Android library's `release` variant (with sources + javadoc JARs)
3. Publishes to group `com.lunacattus.android`
4. Provides a `lunaPublish { }` extension DSL for artifact metadata (artifactId, artifactVersion, artifactName, artifactDescription)
5. Adds a `zipDeploymentBundle` task that packages the publication into a ZIP
6. Adds a `publishToCentralPortal` task that uploads the ZIP to Sonatype Central Portal via its REST API

Credentials are resolved from (in priority order): Gradle project property → `local.properties` → environment variable (uppercased, dots replaced with `_`). Required properties for publishing: `ossrhUsername`, `ossrhPassword`; for signing: `signing.secretKey`, `signing.password`, and optionally `signing.keyId`.

### Version Catalog

Dependency versions are centralized in `gradle/libs.versions.toml`. Key versions: AGP 9.2.1, Kotlin 2.1.10, kotlinx-coroutines 1.11.0, OkHttp 5.3.2.

### Logging Pattern Across Modules

Both `common` and `network` define their own internal log objects (`CommonLog`, `NetworkLog`) with an identical pattern: they default to `android.util.Log` but expose a `setLogger(debug, error)` function so consumers can inject custom log pipelines. The `logger` module is a separate standalone offering and is not used internally by the other modules.
