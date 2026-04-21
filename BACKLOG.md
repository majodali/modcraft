# Backlog

Working backlog for the modcraft monorepo. Updated as we ship, discover, or descope. Estimates are "an unhurried weekend of focused work"; treat them as rough.

## Decisions driving the shape

- **Mob-shaped sidekicks first; no player-shaped.** ~10× less complexity and mobs give more personality space. Mineflayer (Node.js bot framework) was considered for player-shaped and rejected because of cross-process/cross-language ops overhead, loss of engine-level integration (no mixins, no tick hooks on mod-only blocks), and no singleplayer story. Revisit Mineflayer only if a feature genuinely needs human-shaped behavior (ladder climbing, precise PvP hitboxes).
- **Ramps before sidekicks.** MC API fundamentals first (blocks, blockstates, models, recipes, datagen), then entity AI, then LLM layer. Each phase is a commit-worthy stopping point.
- **Villages lite is the Phase 5 target.** Informs what primitives Phases 3 and 4 must support (conversation memory, disposition state, structured LLM output).
- **No speculative library work.** Cross-cutting features below get built when the first consumer needs them, not before.

## Cross-cutting library work (`shared/llm-bridge`)

- [ ] **`Conversation`** — rolling message history per session with truncation. Consumers: sidekicks, villagers, bosses.
- [ ] **Structured output** — request JSON matching a schema, validate, recover on malformed. Anthropic via tool-use; Ollama via JSON mode. Consumers: action selection, blueprints, trade dialog.
- [ ] **Cancel / timeout** — interruptible `complete()`; abort on player logout, entity death, world unload.
- [ ] **Throttle + budget** — per-entity rate limit + server-wide daily cap, both configurable. Prevents "20 sidekicks × 1 call/5s × 24h" runaway cost.

---

## Phase 1 — Ramps — `mods/ramps`  *(no LLM, ~weekend)* ← **next**

Build from `mods/template/`. Teaches MC fundamentals (blockstates, voxel shapes, models, recipes, datagen).

**Scope**
- Grades: 1:2, 1:3, 1:4 (skip 1:1 — vanilla stairs cover it)
- Starter materials: oak plank, stone, cobblestone. Others follow the same pattern once the first three work.
- Sideways-placement variant for curved-wall construction
- Recipes: craft from N full blocks of the source material
- Lang file + item/block models

**Exit criteria**
- All ramp types render, place, break, rotate (F3+B shows correct collision)
- JUnit test for recipe outputs
- GameTest places one of each grade + a sideways variant, asserts collision shape and BlockState after rotation

**Won't-do here** — redstone interactions, glass/ice/snow/leaf materials, waterlogging, stacking half-ramps atop half-ramps.

---

## Phase 2 — Sidekick v0 non-LLM — `mods/sidekick`  *(~weekend)*

Build from template. Tameable wolf-variant mob with deterministic commands.

**Scope**
- Custom mob entity (wolf variant; retexture)
- Tame by feeding X item
- Commands via chat `/sidekick <name> <cmd>` or right-click wheel: *follow, stay, attack target, patrol to block*
- Persistence across save/load
- **Action abstraction layer** — records defining the discrete action space. The LLM will plug into this unchanged in Phase 3.

**Exit criteria**
- All four commands work in-game
- GameTest: spawn + issue "follow", assert entity paths toward player within N ticks
- Action abstraction has unit-testable dispatch (no MC classes in the abstraction itself)

**Key design note**: the action abstraction is the most important artifact of this phase. Better to ship fewer actions that are well-modeled than many actions with a hand-wavy interface, because the LLM layer (Phase 3) inherits this surface.

---

## Phase 3 — Library work + Sidekick v1 LLM-driven — extend `mods/sidekick`  *(~weekend)*

**Library first.** Implement `Conversation` + structured output in `shared/llm-bridge`. Land with tests before touching sidekick code.

**Mod work**
- Periodic LLM call (~5s throttle) with current world state summary + action list
- LLM returns JSON matching action schema; sidekick executes
- Per-sidekick `Conversation` tracks decision history
- Mockable `LlmClient` in gametests (seam already exists via `HttpTransport`)

**Exit criteria**
- Unit test: fake LLM returning `{"action":"follow"}` causes sidekick to follow
- GameTest with mocked LLM executes a scripted multi-step scenario end-to-end
- Manual test: real LLM drives sidekick through 3+ varied decisions in dev client

---

## Phase 4 — Blueprint engine — `mods/blueprint`  *(~weekend)*

`/imagine <description>` → LLM → JSON blueprint → blocks placed. Standalone; no entity dependencies.

**Scope**
- JSON schema for a blueprint (bounded box, block palette, per-position id)
- LLM prompt engineering to produce valid blueprints
- Safe placement: undo on failure, player permission check, size cap
- `/blueprint save <name>` to capture an existing structure for the LLM to learn from

**Exit criteria**
- `/imagine small cottage` produces a placed structure in ~10s on Anthropic
- GameTest with mocked LLM returning a fixed 5×5×3 blueprint places exactly the expected blocks
- Malformed LLM output is caught and surfaced to the player without crashing

---

## Tooling — `dev-client` subproject  *(~30 min, when Phase 3 ships)*

Thin subproject with `modLocalRuntime` for every local mod + `modRuntimeOnly` for a curated list of public mods (Sodium, Lithium, JEI, WorldEdit). `./gradlew :dev-client:runClient` launches a combined dev world.

Not needed until 2+ local mods exist. Revisit at Phase 3.

---

## Phase 5+ — Ambitious stack (pick per appetite)

Ordered by current interest:

- [ ] **Villages lite** — ONE new profession (Miner: workstation block, tool trades, disposition). Uses `Conversation` for dialogue, uses disposition state machine for consequences. Full village rework is NOT in scope yet.
- [ ] **Dungeons + LLM boss** — composes Phase 2–3 sidekick AI (inverted for hostile mob) + Phase 4 blueprint for the arena.
- [ ] **Sidekick builder** — sidekick executes a Phase 4 blueprint step by step. Starts the "complex plans" thread.
- [ ] **Wheeled carts** — standalone, no LLM. Palate cleanser.
- [ ] **LLM world-gen** — structures placed during world gen (not just on command). World-gen API is finicky; do this late.

---

## Phase 6 — Playable bosses — own phase

After Phases 2–5 have produced ≥2 stable mods with custom entity AI.

- **v1 (no UI)** — play as large boss mob, command minions via chat commands. Uses the sidekick action-abstraction machinery, inverted (you issue commands; minions execute).
- **v2 (with UI)** — RTS-style in-game HUD for minion command. Significant custom GUI work.

---

## Icebox (explicitly deferred)

- **Player-shaped sidekicks** — use mob-shaped. Mineflayer considered; see decision at top.
- **Full functional villages rework** — break into 5+ smaller phases over time, starting with Villages lite above.
- **DSL-based mod compiler** — only when 4+ mods reveal stable patterns worth templating.
- **Mineflayer integration** — not ruled out permanently. Revisit if a specific feature genuinely needs real-player-shaped behavior.

---

## Open questions

- **Ramp starter materials** — proposed: oak plank, stone, cobblestone. Confirm at Phase 1 start.
- **Sidekick species** — wolf-variant is easiest (tame-by-feed + follow AI already exist in vanilla to reference). Fox, cat, villager, or fully custom are alternatives. Decide at Phase 2 start.
- **Blueprint size cap** — an absolute ceiling (e.g. 16×16×16) to bound LLM token usage and player-grief potential. Decide at Phase 4 start.
- **LLM provider default for gameplay features** — Ollama (free, local, slower, model-dependent quality) vs Anthropic (cost, best quality). Players pick via config. No action needed until Phase 3.

---

## Changelog

- **2026-04-21** — Initial backlog. Monorepo scaffold, first mod, and three-layer test harness shipped in `3f20c25`. Phase 1 (ramps) is next.
