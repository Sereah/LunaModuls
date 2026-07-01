# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build all library modules
./gradlew assembleRelease

# Build a specific module
./gradlew :common:assembleRelease
./gradlew :logger:assembleRelease
./gradlew :network:assembleRelease

# Clean all modules
./gradlew clean

# Clean and rebuild a specific module
./gradlew :common:clean :common:assembleRelease
```

Configuration cache is enabled (`gradle.properties`). If builds behave unexpectedly, run `./gradlew --no-configuration-cache` to rule out cache issues.

### ⚠️ Tests

**This project has no tests.** There are no `src/test/` or `src/androidTest/` directories in any module. Test-related commands (`./gradlew test`) will succeed with no tests executed. Test infrastructure is declared in `build.gradle.kts` (`testInstrumentationRunner`) but no test sources exist.

### Publishing Library Modules

```bash
# Generate the Central Portal deployment bundle (ZIP)
./gradlew :common:zipDeploymentBundle

# Publish to Sonatype Central Portal (requires credentials in local.properties)
./gradlew :common:publishToCentralPortal
```

### Publishing Framework JAR Module

```bash
# Publish the framework-jar plugin + all framework JARs to local bundle
./gradlew :framework-jar:zipDeploymentBundle

# Upload everything to Sonatype Central Portal
./gradlew :framework-jar:publishToCentralPortal
```

Credentials are resolved in priority order: Gradle project property → `local.properties` → environment variable (uppercased, dots replaced with `_`). Required: `ossrhUsername`, `ossrhPassword`; for signing: `signing.secretKey`, `signing.password`, optionally `signing.keyId`.

---

## Project Architecture

**LunaModules** is a multi-module Android library project publishing to `com.lunacattus.android`. All modules are Android libraries targeting compileSdk 36.1, minSdk 31, Java 17.

### Modules

| Module | Type | Version | Dependencies | Description |
|--------|------|---------|-------------|-------------|
| `:common` | Android lib | 1.0.2 | kotlinx-coroutines-core | Core utilities, coroutine wrappers, extensions |
| `:logger` | Android lib | 1.1.0 | annotation-jvm | Standalone logging library |
| `:network` | Android lib | 1.0.0 | kotlinx-coroutines-core, okhttp, annotation-jvm | HTTP + WebSocket client |
| `:framework-jar` | Java lib (Gradle plugin) | 1.0.0 | kotlin-gradle-plugin | Gradle plugin + framework JAR publishing |

Each module is independently publishable — there are **no inter-module dependencies**.

### Module Details

- **common** — `SafeCoroutine` (structured concurrency: `launchSafe`, `asyncSafe`, `cancelSafe` that auto-catch and log exceptions), `CommonLog` (pluggable log channel via `setLogger(debug, error)`), extension functions for `Context`, `String`, `DateTime`, `File`, `View`, `Assets`, `Device`.

- **logger** — Full logging system: Logcat output, file persistence with automatic rotation and size-based cleanup, Box-style bordered log printing, automatic TAG inference from calling class (stack trace inspection). Thread-safe via single-thread executor.

- **network** — `IHttpClient` interface with `post`/`get` returning `Result<String>`, `HttpManager` (OkHttp implementation), `HttpConfig` for timeouts. `IWebSocketClient` interface with `StateFlow<WebSocketState>` / `SharedFlow<WebSocketEvent>`, `WebSocketManager` with exponential-backoff auto-reconnect and configurable heartbeat/ping. `RequestIdGenerator` produces ULID-based request IDs with device-code prefix. Event model: sealed interfaces `WebSocketEvent` (Connected, Disconnected, MessageReceived, Error) and `WebSocketState` (Idle, Connecting, Connected, Reconnecting, Disconnected, Failed).

- **framework-jar** — Not an Android library. A Gradle plugin (`lunacattus.android.framework.jar`) that resolves AOSP framework JARs from Maven Central for `compileOnly` / `ksp` / `bootstrapClasspath` / `KotlinCompile libraries`. The plugin resolves a bundle POM that transitively includes all companion JARs (framework, bluetooth, wifi, etc.) — no companion list needed. Supports `custom = true` to resolve the custom variant bundle. The module also auto-discovers and publishes JAR files from `frameworkLibs/` by naming convention — no code changes needed to add new versions or types.

---

## Build Logic (build-logic/)

The `build-logic/convention` composite build defines one custom plugin:

### `lunacattus.android.library.publish` — Publishing convention

Applied by all Android library modules. Configures `maven-publish` + `signing`, creates a `release` Maven publication (with sources + javadoc JARs), publishes to group `com.lunacattus.android`. Provides `lunaPublish { }` DSL:

```kotlin
lunaPublish {
    artifactId = "common"
    artifactVersion = "1.0.2"
    artifactName = "Luna Common"
    artifactDescription = "Core utilities for Luna projects"
}
```

Adds `zipDeploymentBundle` and `publishToCentralPortal` tasks. POM metadata includes Apache 2.0 license, SCM links.

---

## `:framework-jar` Module

A Java library module that serves two independent purposes:

### 1. Gradle Plugin — `lunacattus.android.framework.jar`

Published to Maven Central. Provides `frameworkJar { }` DSL:

```kotlin
// Consumer project's build.gradle.kts
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.lunacattus.android:framework-jar:1.0.0")
    }
}
apply(plugin = "lunacattus.android.framework.jar")

frameworkJar {
    version = "13"
    custom = false    // true → use custom variant
}
```

The plugin resolves `android-framework-bundle:{version}` from Maven Central. This is a **POM-only artifact** that transitively includes all companion JARs (framework, bluetooth, wifi, etc.) for that version — no companion list needed in the plugin.

When `custom = true` but the version has no custom JARs, `android-framework-bundle-custom:{v}` was never published — Gradle fails to resolve the dependency with an "artifact not found" error.

### 2. Framework JAR Publishing

Publishes AOSP framework JARs to Maven Central under `com.lunacattus.android`.

Files in `frameworkLibs/` are **auto-discovered** by naming convention:

| File | Published Artifact |
|------|-------------------|
| `framework-14.jar` | `android-framework:14` |
| `framework-custom-14.jar` | `android-framework-custom:14` |
| `bluetooth-13.jar` | `bluetooth-framework:13` |
| `wifi-14.jar` | `wifi-framework:14` |

Additionally, for each version a **bundle POM** is auto-generated:

```
android-framework-bundle:14  (POM only — lists all of the above)
```

The consumer plugin resolves the bundle to get everything. **No code changes needed for any addition.**

To publish a new version: drop JAR files into `frameworkLibs/` and run `./gradlew :framework-jar:publishToCentralPortal`. That's it.

---

## Version Catalog (`gradle/libs.versions.toml`)

Key versions: AGP 9.2.1, Kotlin 2.1.10, kotlinx-coroutines 1.11.0, OkHttp 5.3.2, Hilt 2.59.2, KSP 2.3.4.

**Note:** Hilt (`hilt-android`, `hilt-compiler`, `hilt-core`) and KSP (`com.google.devtools.ksp`) are declared in the catalog but **not currently applied** in any module's `build.gradle.kts`. They exist for future use.

---

## Logging Pattern

Both `common` and `network` define internal log objects (`CommonLog`, `NetworkLog`) with an identical pattern: they default to `android.util.Log` but expose a `setLogger(debug, error)` function for consumers to inject custom log pipelines. The `logger` module is a separate standalone offering and is not used internally by the other modules.

---

## Gradle Settings

- **Wrapper**: Gradle 9.5.0
- **JVM Args**: `-Xmx2048m -Dfile.encoding=UTF-8`
- **Configuration cache**: enabled
- **Kotlin code style**: official
- **SDK**: `/home/glacien/Android/Sdk` (in `local.properties`)
- **Settings**: `.claude/settings.local.json` allows `Bash(git *)` commands
