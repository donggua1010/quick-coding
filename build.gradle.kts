import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.jiemmo"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.freemarker:freemarker:2.3.32")
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaUltimate("2024.2.6")
        bundledPlugins("com.intellij.java", "com.intellij.database")
        testFramework(TestFrameworkType.Platform)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        name = "quick-coding"
        ideaVersion {
            sinceBuild = "242.0"
            untilBuild = "251.*"
        }
    }

    publishing {
        // 认证令牌来自环境变量 PUBLISH_TOKEN（或 -PpublishToken=xxx），不要把令牌写进代码仓库
        token.set(System.getenv("PUBLISH_TOKEN")
                ?: project.findProperty("publishToken") as String?)
    }
}