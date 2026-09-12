# Latent ChemLib

Latent ChemLib is a Forge `1.20.1` bridge for the parts of ChemLib matter that
need world consequences: gas escape, radioactive decay, neutron-driven fission,
and radioactive-form emission profiles.

The design goal is emergent behavior from numeric traits and curve intercepts,
not hard-coded per-element special cases. Pack authors can tune chemical
identity through datapack JSON while the mod derives fallback traits from
ChemLib registry data.

## Current Features

- A one-way atmospheric boundary that turns escaped ChemLib gases into native
  AdPother pollutant blocks.
- A conserved bridge ratio of 16 Latent mass per AdPother unit. Release is
  preflighted atomically, while AdPother remains the sole authority for ambient
  density, movement, wind, spreading, impacts, protection, detection,
  explosions, filtering, chimney routing, and cleanup.
- Gas item escape handling for item entities and player inventories.
- A narrow `AirtightInventory` API for owning machines to suppress gas escape
  while their sealed state is active. Block-entity mutations are queued for the
  next budgeted escape scan, so newly inserted gases do not wait for chunk reload.
- Heavy element neutron flux simulation for ChemLib element stacks.
- Data-driven fixed radioactive-family profiles selected by exact item/block ID
  or item/block tag, with independent radiation and radiogenic heat strengths.
- Persistent disturbance sidecars which keep natural hosted uranium/thorium ore
  inert while making mined, carried, dropped, contained, and replaced forms active.
- A read-only `LatentEmissionProfiles` API for stack and placed-form consumers;
  fixed Realistic Ores profiles do not create or require isotope NBT.
- No blocks, items, fluid stores, chemical capabilities, or generic reaction
  machines. Owning mods remain authoritative for installing and persisting
  containment and for processing.
- File-based datapack reload support for:
  - `data/latent_chemlib/chemical_traits/*.json`
  - `data/latent_chemlib/scheduler_profiles/default.json`
- Server tick budgets for gas-escape and nuclear workloads.
- Unit tests for numeric curves and emergent simulation math.

## Tech Stack

- Minecraft `1.20.1`
- Forge `47.4.13`
- Java `17`
- ChemLib `2.0.19`
- Heat Sync (mandatory typed thermal API)
- AdPother (mandatory atmospheric authority)

## Development

Common tasks:

```bash
./gradlew verifyFast
./gradlew verifyFull
./gradlew runClient
./gradlew runServer
```

The JVM unit coverage gate is intentionally focused on pure isotope, decay,
fission, gas-boundary, and scheduling logic. Forge event handlers are thin
integration boundaries. `verifyFast` runs the JVM coverage gate; `verifyFull`
also builds the runtime JAR and runs three headless Forge GameTests. They exercise native
ChemLib block placement through Forge's event path, isotope-preserving native loot and
sidecar consumption, and chunk reconciliation that retains enriched blocks, removes
stale entries, and discovers untracked blocks without duplicating material records.
The chunk case posts the real Forge lifecycle event for a loaded fixture chunk and
roundtrips SavedData; it does not claim a disk unload/reload while fixture tickets remain.

`gametest/profiles/full.txt` lists the required runtime IDs. Every invocation retains an
isolated fixture under `build/gametest/<run-token>/` with world files, logs, and
`execution.json`. The gate requires that run's token, a finished report, and exactly the
expected discovered and successfully executed tests. Failed fixtures remain available
until explicitly removed; `clean` removes build artifacts. `verifyFast` also exercises
the execution-evidence parser against missing, incomplete, stale, malformed, duplicate,
and failed results without starting additional servers.

## Pack Configuration

Pack-side datapack examples are expected under:

```text
data/latent_chemlib/chemical_traits/
data/latent_chemlib/nuclear_forms/
data/latent_chemlib/nuclear_decay/
data/latent_chemlib/nuclear_phenomena/
data/latent_chemlib/scheduler_profiles/
```

Traits expose volatility, thermal, instability, absorption, and scattering
levers. Scheduler profiles cap per-dimension gas and nuclear work. Nuclear form,
decay, and phenomena files define radioactive identity and consequences without
creating another processing system.

## Notes

- Mod metadata is sourced from `gradle.properties`.
- The mod deliberately has no block/item registry. Pack-specific processing and
  progression live in the consuming pack and the owning technology mods.
- Atmospheric conversion uses 16 Latent mass per whole AdPother unit and hands
  accepted matter off atomically; after that boundary AdPother is authoritative.
- Nuclear heat is offered only through Heat Sync. Heat that Heat Sync cannot
  accept remains in the simulated material's energy state.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Identity

The canonical identity is repository/artifact `latent-chemlib`, mod ID and resource namespace `latent_chemlib`, and Maven group `com.bettercontent`. Latent has a mandatory loader and typed binary dependency on Heat Sync, which owns the pack's thermal transport API; other consumers may use Latent's read-only emission API.
