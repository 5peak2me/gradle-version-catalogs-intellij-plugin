# Gradle Version Catalogs IntelliJ Plugin

[![JetBrains Plugin Version](https://img.shields.io/jetbrains/plugin/v/25647-gradle-version-catalogs?label=Gradle%20Version%20Catalogs)](https://plugins.jetbrains.com/plugin/25647-gradle-version-catalogs)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/25647.svg)](https://plugins.jetbrains.com/plugin/25647)
[![Kotlin](https://img.shields.io/badge/dynamic/toml?url=https://raw.githubusercontent.com/5peak2me/gradle-version-catalogs-intellij-plugin/v2/gradle/libs.versions.toml&query=$.versions.kotlin&label=Kotlin&color=blue&logo=kotlin)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/dynamic/regex?url=https://raw.githubusercontent.com/5peak2me/gradle-version-catalogs-intellij-plugin/v2/gradle/wrapper/gradle-wrapper.properties&search=gradle-([0-9.]%2B)-(?:bin|all).zip&replace=$1&label=Gradle&color=blue&logo=gradle)](https://gradle.org)

### Forked from [gradle-version-catalogs-intellij-plugin](https://github.com/F43nd1r/gradle-version-catalogs-intellij-plugin)

- Support K2 mode
- Added support for custom TOML files
- Added support for `build.gradle` files

---

Improved gradle version catalog support including

- jump to usage/definition from/to versions.toml and build.gradle.kts
- detect unused declarations
- versions.toml autocompletion

# Download

[IntelliJ Marketplace](https://plugins.jetbrains.com/plugin/25647-gradle-version-catalogs)

# Note on Future Development

Android Studio is already rolling out [builtin support for Version Catalogs](https://developer.android.com/studio/preview/features#gradle-version-catalogs). This plugin will be
discontinued once IntelliJ IDEA gets those features as well.

# Acknowledgements

Parts of this project were created during [off-project-time](https://en.wikipedia.org/wiki/20%25_Project) graciously provided by [codecentric](https://codecentric.de/). Thank you!
