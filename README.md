# Minecraft monorepo

Multi-mod Fabric workspace for **Minecraft 1.21.4** with a shared LLM integration library. Houses any number of mods (and later: datapacks / resource packs) that can reuse common code.

## Prerequisites

- JDK 21 (verified with Temurin 21.0.6)
- Internet on first build (downloads Minecraft + Fabric deps, ~1–2 GB)

Gradle comes via the wrapper (`./gradlew`).

## Repo layout

```
./
├─ settings.gradle               # lists all subprojects
├─ gradle.properties             # shared version pins (MC, yarn, loom, fabric-api, etc.)
├─ gradle/, gradlew, gradlew.bat # one wrapper for the whole repo
│
├─ shared/
│   └─ llm-bridge/               # pure-Java library (no Minecraft, no Loom)
│       └─ com.majod.llmbridge.* # AnthropicClient, OllamaClient, HttpTransport, LlmConfig…
│
├─ mods/
│   ├─ llmcraft/                 # /ask <prompt> command + Echo Stone item
│   └─ template/                 # skeleton — copy this to start a new mod
│
└─ packs/                        # (not created yet — standalone datapacks / resourcepacks)
```

**Key rule:** Minecraft-specific code lives under `mods/*`. Anything reusable across mods (HTTP clients, LLM adapters, config loading, generic utilities) lives under `shared/*` as plain Java.

## Quick start

```bash
./gradlew test                                  # all subprojects' unit tests, sub-second
./gradlew build                                 # compile + test + remap all mod jars
./gradlew :mods:llmcraft:runClient              # launch a specific mod's dev client
./gradlew :mods:llmcraft:runServer              # launch a specific mod's dev server
./gradlew :mods:llmcraft:runGametest            # in-world @GameTest assertions, ~30-60s
```

Mod jars land in `mods/<name>/build/libs/<name>-<version>.jar`.

## Adding a new mod

1. `cp -r mods/template mods/my-new-mod`
2. Rename the Java package: `mv mods/my-new-mod/src/main/java/com/majod/template mods/my-new-mod/src/main/java/com/majod/mynewmod` and update the `package …;` line at the top of each `.java` file
3. In `mods/my-new-mod/build.gradle`, update `group` and `archivesName` to match
4. In `mods/my-new-mod/src/main/resources/fabric.mod.json`, update `id`, `name`, `description`, and the entrypoint class name
5. In root `settings.gradle`, add `include(':mods:my-new-mod')`
6. `./gradlew :mods:my-new-mod:runClient` to verify

The shared LLM library is already wired into the template's `build.gradle`. Remove those two lines if the new mod doesn't need LLM calls.

## Using the shared LLM library

From any mod:

```java
import com.majod.llmbridge.LlmClient;
import com.majod.llmbridge.LlmClientFactory;
import com.majod.llmbridge.LlmConfig;

LlmConfig cfg = LlmConfig.loadOrCreate(configPath);
LlmClient llm = LlmClientFactory.create(cfg);
llm.complete("hello").thenAccept(System.out::println);
```

Config lives at `run/config/<modid>.json` (dev) or `.minecraft/config/<modid>.json` (prod). Default provider is Ollama; flip `"provider": "anthropic"` and paste an API key to use Claude instead. Example at [mods/llmcraft/](mods/llmcraft/).

```json
{
  "provider": "ollama",
  "anthropic": {
    "apiKey": "",
    "model": "claude-sonnet-4-6",
    "maxTokens": 1024
  },
  "ollama": {
    "baseUrl": "http://localhost:11434",
    "model": "llama3.2"
  }
}
```

## Testing approach

Three layers, fastest to slowest:

| Layer | Command | What it covers | Speed |
|---|---|---|---|
| Unit | `./gradlew test` | LLM clients, JSON parsing, pure-Java logic. No Minecraft, no network (in-memory `HttpTransport` fakes). | sub-second |
| GameTest | `./gradlew :mods:<name>:runGametest` | Mod loads in a real server, registries populate, in-world behaviors don't throw. Headless. | ~30–60s |
| Manual | `./gradlew :mods:<name>:runClient` | Visual checks: item icons, chat output, creative tab. | minutes |

- **Unit tests** live with the code they cover — `shared/llm-bridge/src/test/` for the library, `mods/<name>/src/test/` for mod-specific pure-Java logic.
- **GameTests** live under `mods/<name>/src/main/java/.../gametest/` and are wired via the `fabric-gametest` entrypoint in `fabric.mod.json`. Only invoked under the `-Dfabric-api.gametest` JVM flag, so they don't run in normal play.
- JUnit-XML reports for GameTests land in `mods/<name>/build/gametest-junit.xml` for CI consumption.

## LLMCraft — the first mod

- `/ask <anything>` — sends prompt to the configured LLM, replies in chat.
- Echo Stone item — appears in the Ingredients creative tab, or `/give @s llmcraft:echo_stone`.

See [mods/llmcraft/](mods/llmcraft/) for a worked example of config loading, async command dispatch, item registration, and a GameTest.

## Version notes

When a new Minecraft version drops, update the shared pins at the top of `gradle.properties` (check https://fabricmc.net/develop for current versions). All mods inherit the new version automatically. For supporting multiple MC versions from one mod, see [Stonecutter](https://github.com/kikugie/stonecutter) — not wired up yet; add when porting pain justifies it.
