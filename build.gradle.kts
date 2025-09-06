import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
  kotlin("jvm") version "2.0.20"
  id("org.jetbrains.intellij.platform") version "2.8.0"
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    // Локальная IDE для runIde/сборки
    local("/Applications/WebStorm.app/Contents")
  }
}

kotlin {
  jvmToolchain(17)
}

intellijPlatform {
  pluginConfiguration {
    id = "dev.promptpack"
    name = "PromptPack"
    version = "0.1.0"
    description = "Copy selected files/folders as LLM-ready fenced blocks with an optional project file tree."
    vendor {
      name = "You"
      email = "you@example.com"
    }
    ideaVersion {
      sinceBuild = "242"
      untilBuild = "242.*"
    }
  }

  buildSearchableOptions = false

  pluginVerification {
    ides {
      // Верифицируемся на релизных WebStorm 242.*
      select {
        types = listOf(IntelliJPlatformType.WebStorm)
        channels = listOf(ProductRelease.Channel.RELEASE)
        sinceBuild = "242"
        untilBuild = "242.*"
      }
    }
  }
}

ktlint {
  version.set("1.4.1")
  filter { exclude("**/build/**") }
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(files("$rootDir/detekt.yml"))
  baseline = file("$rootDir/detekt-baseline.xml")
  source.setFrom("src/main/kotlin", "src/test/kotlin")
}

tasks.named("check") {
  dependsOn("ktlintCheck", "detekt")
}
