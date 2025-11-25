# Repository Guidelines

## Project Structure & Module Organization
Kotlin source lives in `src/main/kotlin/cloud/coffeesystems/auctionmaster`, grouped by concern (`ui` menus, `database` managers, etc.), while plugin descriptors, configs, and locale bundles (`paper-plugin.yml`, `config.yml`, `messages_en.yml`) reside in `src/main/resources`. Tests live in `src/test/kotlin` (fixtures in `src/test/resources`). Gradle scripts (`build.gradle.kts`, `gradle/`) define the toolchain, shading rules, and dependency overrides; adjust them when adding APIs or relocating packages.

## Build, Test, and Development Commands
Use `./gradlew clean build` for a full rebuild: runs unit tests, creates the shaded plugin JAR, and triggers the `copyJar` helper. `./gradlew test` executes the Kotlin/JUnit test suite without producing artifacts—run it before opening a PR. `./gradlew shadowJar` generates `build/libs/AuctionMaster-<version>.jar` without copying it to a server, useful for CI or manual installs. `./gradlew copyJar` mirrors the latest shaded JAR into `/Users/cancelcloud/Developer/Minecraft/purpur21-4/plugins/`; update that path if your local server differs.

## Coding Style & Naming Conventions
Follow Kotlin style (4-space indentation, trailing commas where helpful, prefer `val` and expression functions) and keep files under `cloud.coffeesystems.auctionmaster.<module>` packages. GUI builders should continue the Adventure text patterns used in `ui/` (no italics by default, localized via `messageManager`). New configuration keys go in snake-case YAML (`gui.main-menu.title`) with matching entries in each locale file, and remember to register or unregister Bukkit listeners just like existing GUIs.

## Testing Guidelines
`kotlin("test")` with JUnit Platform is configured—name test classes `<Feature>Test` and place shared fakes in `src/test/kotlin/support`. Target behavioral coverage for `AuctionManager`, database adapters, and utility extensions; mock Bukkit objects or wrap them so logic can be exercised off-server. Before shipping gameplay changes, manually verify GUIs, persistence, and localization on a Purpur dev server.

## Commit & Pull Request Guidelines
Recent history follows `type: summary` (e.g., `feature: buildable and working project`, `refactor: Remove Sparkle...`). Keep subjects in sentence case, under ~60 chars, and group changes logically. Pull requests need: clear description of the feature/fix, linked issue (if any), instructions for verifying on a server, screenshots/GIFs for new UI, and notes about config or migration impacts. Rebase before requesting review and ensure CI (or at least `./gradlew test`) is green.

## Security & Configuration Tips
Never commit real database credentials; instead, document overrides in `config.yml` comments or share them through private channels. When adding dependencies that bundle shaded libraries, update the `shadowJar` relocation/exclusion rules to prevent classpath conflicts. Keep locale files synchronized (`messages_en.yml`, `messages_de.yml`) so message keys never fail at runtime, and validate `paper-plugin.yml` every time you touch metadata or permissions.
