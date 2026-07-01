# Framework JAR Module

此模块负责两件事：
1. **发布 framework JAR 文件**到 Maven Central
2. **提供 Gradle 插件** `lunacattus.android.framework.jar`，消费者通过它拉取 JAR

---

## 发布新版本

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

> `type = "framework"` 是特例 → artifact ID `android-framework`
> 其他 type（bluetooth、wifi 等）→ `{type}-framework`

### 自动生成的 bundle

除了单个 artifact，系统会自动为每个版本生成一个 **bundle POM**：

```
android-framework-bundle:14  (POM only)
  ├── android-framework:14
  ├── bluetooth-framework:14
  ├── wifi-framework:14
  └── ...
```

消费者插件解析这个 bundle 即可自动获取该版本的所有 JAR。**无需配置 companion 列表。**

### Custom Bundle

如果某个版本包含**至少一个** custom JAR（如 `framework-custom-14.jar`），系统会额外发布一个 **custom bundle**：

```
android-framework-bundle-custom:14  (POM only)
  ├── android-framework-custom:14     ← custom variant
  ├── bluetooth-framework:14          ← standard（该版本无 custom bluetooth）
  └── wifi-framework-custom:14        ← custom variant
```

Custom bundle 中每个 type 优先使用 custom 版本，不存在则回退到标准版。

消费者使用 `custom = true` 即可拉取 custom bundle：

```kotlin
frameworkJar {
    version = "14"
    custom = true    // → 解析 android-framework-bundle-custom:14
}
```

> **注意：** 如果该版本没有任何 custom JAR，`android-framework-bundle-custom:{v}` 不会发布到 Maven Central。此时消费者设置 `custom = true` 会导致 Gradle 依赖解析失败（artifact not found）。

### 步骤二：发布

```bash
./gradlew :framework-jar:publishToCentralPortal
```

**无需修改任何代码。** 所有 JAR 文件自动被发现、发布单个 artifact、生成 bundle POM。

### 删除已发布的版本

从 `frameworkLibs/` 中删除对应文件即可。该版本不再出现在后续发布中。

---

## 添加新的 companion 类型（如 wifi 从 framework 中拆分）

**无需任何代码改动。** 把 `wifi-{version}.jar` 放入 `frameworkLibs/`，发布即可：
- `wifi-framework:{version}` 自动发布
- `android-framework-bundle:{version}` 自动包含它
- 消费者解析 bundle 时自动拉取
