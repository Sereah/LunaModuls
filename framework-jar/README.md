# Framework JAR Module

提供两个功能：

1. **Gradle 插件** `lunacattus.android.framework.jar` — 消费者通过 DSL 拉取 AOSP framework JAR
2. **JAR 自动发布** — 将 `frameworkLibs/` 中的 JAR 发布到 Maven Central

---

## 一、消费者：使用 Gradle 插件

### 添加插件

**方式一：版本目录（推荐）**

```toml
# gradle/libs.versions.toml
[plugins]
framework-jar = { id = "com.lunacattus.android.framework.jar", version = "1.0.0" }
```

```kotlin
// build.gradle.kts (app 或 library 模块)
plugins {
    alias(libs.plugins.framework.jar)
}

frameworkJar {
    version = "13"          // 指定 AOSP 版本
    custom = false          // true → 使用 custom variant 的 JAR
}
```

**方式二：直接声明**

```kotlin
// build.gradle.kts
plugins {
    id("com.lunacattus.android.framework.jar") version "1.0.0"
}

frameworkJar {
    version = "13"
    custom = false
}
```

### 已发布的 JAR 版本

| 版本 | 包含的 JAR |
|------|-----------|
| 11 | `android-framework:11` |
| 12 | `android-framework:12` |
| 13 | `android-framework:13`, `bluetooth-framework:13` |
| 13-custom | `android-framework-custom:13`, `bluetooth-framework-custom:13` |

version 和 custom 组合决定解析哪个 bundle：

```kotlin
frameworkJar { version = "13"; custom = false }  // → android-framework-bundle:13
frameworkJar { version = "13"; custom = true }   // → android-framework-bundle-custom:13
```

### 插件作用

插件会自动解析 `android-framework-bundle:{version}`（POM-only artifact），它传递性地包含该版本的所有 AOSP 框架 JAR（framework、bluetooth、wifi 等），并配置到：

- `compileOnly` 依赖
- `ksp` / `KotlinCompile` 的 `libraries`
- `JavaCompile` 的 `bootstrapClasspath`

**无需手动添加任何 companion 列表。**

### 原理

插件 → `android-framework-bundle:{version}` (POM) → `android-framework:{version}` + `bluetooth-framework:{version}` + `wifi-framework:{version}` + ...

### Custom variant

部分版本存在 `-custom` 变体（带额外调试符号等）。设置 `custom = true` 将改为解析 `android-framework-bundle-custom:{version}`。

```kotlin
frameworkJar {
    version = "13"
    custom = true
}
```

> **注意：** 如果目标版本没有 custom JAR，`android-framework-bundle-custom:{v}` 未被发布，Gradle 会报 "artifact not found" 错误。

---

## 二、发布方：发布 Framework JAR

### 步骤一：放文件

把 JAR 文件放到 `frameworkLibs/` 目录，按命名规范：

```
{type}-{version}.jar              →  标准版
{type}-custom-{version}.jar       →  custom 版
```

| 示例文件 | 发布的单个 artifact |
|---------|-------------------|
| `framework-14.jar` | `android-framework:14` |
| `framework-custom-14.jar` | `android-framework-custom:14` |
| `bluetooth-14.jar` | `bluetooth-framework:14` |
| `wifi-14.jar` | `wifi-framework:14` |

> - `type = "framework"` → artifact ID `android-framework`
> - 其他 type（`bluetooth`、`wifi` 等）→ `{type}-framework`

### 自动生成的 bundle

除了单个 artifact，系统会自动为每个版本生成一个 **bundle POM**：

```
android-framework-bundle:14  (POM only)
  ├── android-framework:14
  ├── bluetooth-framework:14
  ├── wifi-framework:14
  └── ...
```

消费者插件解析 bundle 即可自动获取该版本的所有 JAR。**无需配置 companion 列表。**

### Custom bundle

如果某个版本包含**至少一个** custom JAR（如 `framework-custom-14.jar`），系统会额外发布一个 custom bundle：

```
android-framework-bundle-custom:14  (POM only)
  ├── android-framework-custom:14     ← custom variant
  ├── bluetooth-framework:14          ← 该版本无 custom bluetooth，回退到标准版
  └── wifi-framework-custom:14        ← custom variant
```

Custom bundle 中每个 type 优先使用 custom 版本，不存在则回退到标准版。

### 步骤二：发布

```bash
./gradlew :framework-jar:publishToCentralPortal
```

**无需修改任何代码。** 所有 JAR 文件自动被发现、发布单个 artifact、生成 bundle POM。

### 删除已发布的版本

从 `frameworkLibs/` 中删除对应文件即可。该版本不再出现在后续发布中。

### 添加新的 companion 类型（如 wifi 从 framework 中拆分）

**无需任何代码改动。** 把 `wifi-{version}.jar` 放入 `frameworkLibs/`，发布即可：
- `wifi-framework:{version}` 自动发布
- `android-framework-bundle:{version}` 自动包含它
- 消费者解析 bundle 时自动拉取
