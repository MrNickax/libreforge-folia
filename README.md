# libreforge (Folia)

> ⚠️ **Unofficial fork.** This is an independent fork of
> [libreforge](https://github.com/Auxilor/libreforge), patched to run on **Folia**. It is not
> affiliated with, endorsed by, or supported by Auxilor or the original libreforge team.

**libreforge** is a YAML-based "scripting" engine for config-driven effects and automations,
used by EcoEnchants and many other plugins. This fork ports it to **Folia** (and Folia-based
servers); all other functionality comes from the upstream project.

## Disclaimer

This software is provided **as is, without any warranty**. It is an unofficial adaptation
maintained on my own, and **I take no responsibility for any bugs, crashes, data loss or damage**
resulting from its use. Use it at your own risk, and always test in a controlled environment
before deploying to production.

Issues that also occur in the official version should be reported upstream — not blamed on this
fork.

## For developers

This fork publishes the `libreforge`, `libreforge-loader` and `libreforge-gradle-plugin`
artifacts to **GitHub Packages**, and depends on the Folia fork of
[eco](https://github.com/MrNickax/eco-folia). GitHub requires authentication with a
`read:packages` token even for public packages, so add your credentials to
`~/.gradle/gradle.properties` (`gpr.user` / `gpr.key`) or the `GITHUB_ACTOR` / `GITHUB_TOKEN`
environment variables.

```kotlin
// settings.gradle.kts — for the gradle plugin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.github.com/MrNickax/libreforge-folia") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

## Building

Publish the [eco fork](https://github.com/MrNickax/eco-folia) first, then:

```bash
git clone https://github.com/MrNickax/libreforge-folia
cd libreforge-folia
./gradlew build
```

The standalone plugin jar is produced in `bin/` and attached to each
[release](https://github.com/MrNickax/libreforge-folia/releases).

## Credits & license

Based on [Auxilor/libreforge](https://github.com/Auxilor/libreforge). The original license terms
are preserved — see [LICENSE.md](LICENSE.md).
