# libreforge-folia upstream sync (2026.29) — progress ledger

Branch `sync/upstream-2026.29` from master. Merged upstream Auxilor/libreforge 2026.29.
Version: 2026.29-folia (CalVer). eco-version: 2026.29-folia. gradle-plugin kept 2.0.1-folia.

## MERGE COMPLETE — build -x test GREEN (needs eco 2026.29-folia in mavenLocal or GH Packages).
- 118 conflicts. 89 mechanical (caffeine->EcoCache) = keep_theirs. 20 Folia-critical = manual.
- HolderProvider.kt: preserved Blast Mining fix (mainhandRefreshPending/disableMainhandEffects/pollEffects
  guard) + fork's synchronized updateEffects; migrated my Caffeine cache -> EcoCache; took upstream EcoCache
  migration. Fixed merge corruption (duplicate computeHolders, getIfPresent->get).
- LibreforgeSpigotPlugin.kt: kept fork's Folia poll scheduling (global region scheduler + per-player hop);
  took upstream new integration imports/loaders (ItemsAdder, CraftEngine) + pyrofishingpro package fix.
- 18 Folia effects/triggers: recovered fork's pre-merge Folia versions (correct entity/region schedulers) —
  they compile against new eco API. (EffectAntigravityProjectile, InfiniteBucket, Homing, Shockwave, Vortex,
  TimeBomb, Stun, Silence, SpawnMobs, SetGlowing, PlaceBlock, PermanentPotionEffect, SwapPositions, Traceback,
  TriggerUnclaim, TriggerGroupStatic/GlobalStatic, TriggerTakeEntityDamage).
- Compile fixes: removed duplicate onRespawn (HolderUpdates); deleted fork typo dups (oxaren/ dir,
  TriggerShopkeepersTrad.kt); deleted stale effects/EffectHarvestCrop+EffectKill (upstream moved to impl/).
- Infra: build.yml/README/.gitignore=ours; publish-release/CODEOWNERS removed; root build.gradle.kts keeps
  MrNickax/eco-folia repo; core/common took upstream deps (dropped caffeine dep).

## FOLIA FOLLOW-UP (flagged — not a regression to core paths)
Two effects use eco's global scheduler abstraction for entity-bound work; should move to entity/region schedulers:
- effects/impl/EffectParticleAnimation.kt — took upstream (fork Folia version didn't compile: upstream changed
  getParticleLocations/shouldStopTicking signatures with new Float3/entity params). REGRESSED fork Folia adaptation.
- effects/impl/EffectItemMagnet.kt — NEW upstream effect (runnableFactory.runTaskTimer), never Folia-adapted.
Both only affect servers using those specific effects.

## Local build note
GitHub Packages needs auth; local resolves eco from mavenLocal (eco `publishToMavenLocal`) OR set
GITHUB_ACTOR=MrNickax GITHUB_TOKEN=$(gh auth token). CI has creds.

## TODO
- Commit merge, run tests, bump already done (2026.29-folia), merge to master, push, CI publishes.
- Then EcoEnchants sync (120 behind), bump its libreforge-version + eco-version to 2026.29-folia.
