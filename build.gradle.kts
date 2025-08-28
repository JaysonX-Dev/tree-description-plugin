plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.github.chinese-annotations"
version = "2.4.0"

repositories {
    mavenCentral()
}

dependencies {
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

// 配置 IntelliJ Platform Plugin
intellij {
    version.set("2023.1.5")
    type.set("IC") // IC = IntelliJ IDEA Community, IU = IntelliJ IDEA Ultimate
    
    plugins.set(listOf(
        // 可以根据需要添加依赖的插件
        // "com.intellij.java"
    ))
}

tasks {
    // JVM 兼容性配置
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    // 优化 runIde 任务性能
    runIde {
        jvmArgs = listOf(
            "-Xmx3072m",
            "-XX:MaxMetaspaceSize=512m",
            "-XX:+UseG1GC",
            "-XX:+UseStringDeduplication",
            "-Didea.ProcessCanceledException=disabled",
            "-Didea.debug.mode=true",
            "-Didea.system.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-system",
            "-Didea.config.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-config",
            "-Didea.plugins.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-plugins",
            "-Didea.log.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-logs"
        )
    }

    // 优化构建性能
    buildSearchableOptions {
        enabled = false  // 禁用搜索选项构建以加快速度
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("243.*")  // 支持到2024.3版本
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

