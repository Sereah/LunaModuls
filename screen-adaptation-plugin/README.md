# Screen Adaptation Plugin

一个 Gradle 插件，用于根据设计图参数自动生成 Android 多屏幕适配的 `dimens.xml` 资源文件。

## 功能特性

- **自动生成 `dimens.xml`**：根据设计图尺寸，按比例生成多屏幕适配的 dp/sp 资源
- **多目标屏幕**：支持配置多个目标设备参数，为每种屏幕生成对应的 `values-sw<N>dp` 文件夹
- **ADB 自动探测**：可开启自动探测当前连接的 ADB 设备，实时生成适配资源
- **外部配置文件**：支持 CSV 格式的外部配置文件批量导入目标屏幕参数
- **灵活的命名格式**：可自定义 dp/sp 资源的命名格式

## 插件 ID

```kotlin
id = "com.lunacattus.android.screen-adaptation"
```

## 使用方式

### 1. 应用插件

```kotlin
// build.gradle.kts (app 或 library 模块)
plugins {
    id("com.lunacattus.android.screen-adaptation") version "1.0.0"
}
```

### 2. 基本配置

```kotlin
screenAdaptation {
    // 设计图宽高（像素）
    designWidthPx = 1080
    designHeightPx = 1920
    designDpi = 320              // 设计图 DPI（如 160=mdpi, 320=xhdpi）

    // 适配基准："width" 或 "height"
    baseOn = "width"

    // （可选）目标屏幕列表
    target(1280, 720, 240)       // 宽 1280px, 高 720px, DPI 240
    target(1920, 1080, 420)      // 宽 1920px, 高 1080px, DPI 420

    // （可选）生成维度的最大值，默认取自设计图宽高中的最大值
    maxDp = 1080

    // （可选）资源命名格式，{i} 为占位符
    dpNameFormat = "_{i}px"            // 默认：生成如 <dimen name="_10px">10.00dp</dimen>
    spNameFormat = "_font_{i}px"       // 默认：生成如 <dimen name="_font_10px">10.00sp</dimen>
}
```

### 3. ADB 自动探测

```kotlin
screenAdaptation {
    designWidthPx = 1080
    designHeightPx = 1920
    designDpi = 320

    autoTargetDevice = true      // 开启后自动获取当前连接的 ADB 设备参数
}
```

### 4. 外部配置文件

```csv
# target_devices.csv — 格式: width,height,dpi
1280,720,240
1920,1080,420
```

```kotlin
screenAdaptation {
    designWidthPx = 1080
    designHeightPx = 1920
    designDpi = 320

    targetConfigFile = file("target_devices.csv")
}
```

## 生成效果

执行后将在 `build/generated/res/screenAdaptation/` 下生成：

```
build/generated/res/screenAdaptation/
├── values-sw320dp/
│   └── dimens.xml      # 适配 320dp 最小宽度屏幕
├── values-sw600dp/
│   └── dimens.xml      # 适配 600dp 最小宽度屏幕（如平板）
└── values-sw800dp/
    └── dimens.xml      # 适配 800dp 最小宽度屏幕
```

同时在 `src/main/res/values/dimens.xml` 生成基准资源（比例 1:1）。

## 工作原理

1. 根据 `designWidthPx`、`designHeightPx`、`designDpi` 计算设计图的 dp 基准值
2. 遍历所有目标屏幕参数，按 `baseOn`（宽度或高度）计算缩放比例
3. 对每个目标屏幕，计算其 `sw<N>dp` 最小宽度限定符
4. 在每个限定符目录下生成按比例缩放后的 `dimens.xml`

### dp/sp 转换公式

```
dp = px / (dpi / 160)
```

## 构建发布

本插件发布在 Maven Central，更新版本时：

```bash
# 修改 build.gradle.kts 中的 version
# 然后发布到 Sonatype Central Portal
./gradlew :screen-adaptation-plugin:publishToCentralPortal
```

## 许可证

Apache License, Version 2.0
