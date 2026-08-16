# Smart Phantom Control

Server-side-only Fabric mod for MC 26.2. Replaces vanilla's Phantom
("insomnia") spawner with a fully tunable version - custom eligibility
threshold, per-eligible-night spawn-chance caps and group-size caps,
falling back to plain, uncapped vanilla behavior once the configured
tiers run out.

Current build: **A0.2**, **MC 26.2 only**. Source:
[github.com/StefanBraun2001/smart-phantom-control](https://github.com/StefanBraun2001/smart-phantom-control).

**Distribution: GitHub only.** Following a Modrinth rules change, this mod
is no longer distributed there - grab jars from the
[Releases](https://github.com/StefanBraun2001/smart-phantom-control/releases)
page instead.

## Install

Server-side only, needs Fabric Loader + **Fabric API**. No client-side
component and no client mod required for players joining a server that
has this installed - works the same way in Singleplayer, since SP just
runs a local server.

## Default preset

Eligibility threshold unchanged from vanilla (night 3). From there:

| Night           | Chance cap | Max group size |
|-----------------|-----------|-----------------|
| 1st eligible    | 10%       | 1               |
| 2nd eligible    | 30%       | 2               |
| 3rd eligible    | 40%       | 2               |
| 4th eligible    | 50%       | 3               |
| 5th eligible on | vanilla (uncapped) | vanilla (1-4, difficulty-based) |

## What it does

Vanilla spawns a Phantom above a player once their "time since last rest"
stat passes 72,000 ticks (3 in-game days), with a chance that climbs the
longer they go without sleeping, and a group size of 1-4 depending on
difficulty. This mod keeps every other vanilla gate identical (the
`spawn_phantoms` gamerule, sky-darken check, sky-access check, local
difficulty roll, spawn-placement validity) and replaces only:

- **The eligibility threshold** - how many ticks of "no sleep" are required
  before Phantoms can spawn at all (vanilla: 72,000 = night 3).
- **The spawn-chance cap** for each of the first few eligible nights.
- **The max group size** for each of the first few eligible nights.

Once you've gone past the last night you've configured, spawning falls back
to fully vanilla behavior (uncapped chance, 1-4 group size by difficulty).

Optionally, it can also change **who a spawned Phantom is willing to
attack** - vanilla Phantoms target any nearby player regardless of that
player's own insomnia state; see "Neutral targeting" further down for the
opt-in fix (off by default, so this is a pure add-on, not a default
behavior change).

## Master switch

The `spawn_phantoms` gamerule (`/gamerule spawn_phantoms true|false`) is
still the master on/off switch, exactly like vanilla. If it's `false`,
nothing in this mod runs either - Phantoms just don't spawn.

## Global vs. per-world config

`config/smartphantomcontrol.json` (the **global file**) always exists and
is always read first. Its `configScope` field decides where the *active*
tuning values actually come from:

- **`"global"`** (default): this same global file's `thresholdTicks`/
  `tiers`/`logToConsole`/`logToChat` fields are used directly, for every world/server
  that loads this mod.
- **`"per_world"`**: instead, `<world save folder>/smartphantomcontrol/config.json`
  is used. The first time a given world is loaded while this scope is
  active, that per-world file is created as a copy of whatever the global
  file's tuning values are *at that moment* - after that, editing the
  global file's tuning fields has no further effect on that world; you
  edit the per-world file directly instead.

Switching `configScope` back to `"global"` later does **not** delete or
overwrite an existing per-world file - it's simply ignored while scope is
`"global"`, so switching back to `"per_world"` afterward resumes that
world's file exactly where it was left.

Each world gets its own per-world file independently - opening a second
world under `"per_world"` scope creates *its own* copy, seeded from the
global file's values at the time *that* world is first loaded (which may
differ from what the first world was seeded with, if you'd edited the
global file in between).

## Config file

Global file, created with defaults on first run:

```json
{
  "configScope": "global",
  "thresholdTicks": 72000,
  "tiers": [
    { "chanceCap": 0.1, "maxGroupSize": 1 },
    { "chanceCap": 0.3, "maxGroupSize": 2 },
    { "chanceCap": 0.4, "maxGroupSize": 2 },
    { "chanceCap": 0.5, "maxGroupSize": 3 }
  ],
  "logToConsole": false,
  "logToChat": false,
  "neutralUntilEligible": false,
  "dropTargetOnceIneligible": false,
  "retaliateWhenAttacked": false
}
```

- **`configScope`** (default `"global"`): `"global"` or `"per_world"` -
  see "Global vs. per-world config" above. Only meaningful in the global
  file; ignored if present in a per-world file.
- **`logToConsole`** / **`logToChat`** (both default `false`): testing aids,
  independent of each other. On every insomnia roll, each one that's `true`
  writes a line with the threshold, current night number, vanilla chance,
  the tier's cap, whether the cap actually applied, the final chance, the
  roll result, and - if the roll succeeded - the group size rolled and
  whether it was capped:
  - `logToConsole` writes it to the server log.
  - `logToChat` sends the same line as an in-game chat message to the
    affected player.
  Both off by default since they'll spam their respective output at the
  mod's normal 1-2 minute spawn-attempt cadence per player once enabled -
  turn on whichever channel you actually want while tuning, then back off.
- **`neutralUntilEligible`** / **`dropTargetOnceIneligible`** /
  **`retaliateWhenAttacked`** (all default `false`): see "Neutral
  targeting" below.

### Neutral targeting

By default, vanilla Phantoms don't care whose insomnia caused them to
spawn - once spawned, they'll happily attack *any* nearby player, even one
who sleeps every night. `neutralUntilEligible` fixes that:

- **`neutralUntilEligible`** (default `false`) - the master switch for
  this feature. When `false`, all Phantom targeting is 100% unmodified
  vanilla and the two fields below are inert no matter what they're set
  to. When `true`, a Phantom will only pick a player as a target if that
  specific player has themselves crossed the configured `thresholdTicks` -
  players who've been sleeping fine are simply invisible to its targeting
  scan.
- **`dropTargetOnceIneligible`** (default `false`, only meaningful when
  `neutralUntilEligible` is `true`) - what happens if a Phantom is
  mid-fight against an eligible player who then sleeps (resetting their
  `timeSinceRest`)? `false` = it keeps fighting until the fight naturally
  ends (target acquisition is a one-time check). `true` = it re-checks
  eligibility every tick and disengages the instant the target becomes
  ineligible.
- **`retaliateWhenAttacked`** (default `false`, only meaningful when
  `neutralUntilEligible` is `true`) - vanilla Phantoms have **no
  hit-triggered retaliation at all** (unlike most hostile mobs, they have
  no `HurtByTargetGoal` equivalent registered), so hitting one that isn't
  already targeting you normally does nothing. With this on, a Phantom
  that gets hit by an otherwise-ineligible player and doesn't currently
  have a target will fight back - so "neutral" means defensive-neutral,
  not "consequence-free punching bag." Only applies to Phantoms spawned
  *after* this is turned on - goal registration happens once, when the
  Phantom is created, so already-spawned Phantoms won't retroactively
  pick it up.

If you turn on both `dropTargetOnceIneligible` and `retaliateWhenAttacked`
together: a Phantom that's actively retaliating against the specific
player who hit it is exempted from the ineligibility drop-check, so
retaliation doesn't immediately cancel itself out one tick after starting.
Any *other*, opportunistically-scanned target is still dropped normally if
they become ineligible.

**Old config files keep working as-is.** These three fields (and any
future ones) simply take their Java-side default (`false`) when absent
from an existing JSON file, so upgrading the mod without touching your
config reproduces exactly the same, unmodified vanilla-adjacent behavior
you already had - nothing about existing setups changes unless you
explicitly opt in.

### Getting plain vanilla behavior back

Don't just empty or delete the config file - if it's missing/unparseable
the mod falls back to its own tiered defaults (10%/30%/40%/50%), not
vanilla. To actually reproduce vanilla's spawn distribution exactly, set:

```json
{ "thresholdTicks": 72000, "tiers": [] }
```

`72000` matches vanilla's own threshold constant, and an empty `tiers`
list means every night falls straight through to the uncapped,
difficulty-based-group-size branch - i.e. vanilla behavior, just still
running through this mod's code path instead of the original vanilla one.

- **`thresholdTicks`**: ticks of "time since last rest" required before a
  player becomes eligible for Phantom spawns. Vanilla default is `72000`
  (night 3). One in-game day/night cycle is `24000` ticks, so e.g. `96000`
  = night 4, `24000` = night 1.
- **`tiers`**: an ordered list. `tiers[0]` applies on the player's 1st
  eligible night, `tiers[1]` on the 2nd, and so on. Add or remove entries
  freely - an empty list (`"tiers": []`) means "fully vanilla from the very
  first eligible night". Once the player's current night number exceeds the
  list length, behavior falls back to vanilla for both chance and group
  size.
  - **`chanceCap`**: the maximum spawn-chance roll for that night, as a
    fraction (`0.1` = 10%, `1.0` = 100%). Set to `-1` to leave that night's
    chance uncapped (same asymptotic vanilla-style formula, just not
    clamped).
  - **`maxGroupSize`**: the maximum number of Phantoms that can spawn in a
    single successful roll that night (uniformly rolled between 1 and this
    number). Set to `-1` to use vanilla's difficulty-based group size
    (1-4) for that night instead.

### How the chance is actually computed

Every eligible night, the mod first computes the same value vanilla would:

```
computedChance = (timeSinceRest - thresholdTicks) / timeSinceRest
```

This starts at 0% right when a player becomes eligible and climbs the
longer they go without sleeping. The tier's `chanceCap` (if not `-1`) is
then applied as a ceiling on top:

```
finalChance = min(computedChance, chanceCap)
```

So a low cap (like the default 10%/30%/40%/50%) will bind almost
immediately and hold roughly steady around that value for most of the
night. A cap set higher than `computedChance` ever reaches simply never
binds, and that tier behaves like an uncapped one.

### Examples

**Push first spawns back to night 4, keep the same 4-tier ramp:**
```json
{
  "thresholdTicks": 96000,
  "tiers": [
    { "chanceCap": 0.1, "maxGroupSize": 1 },
    { "chanceCap": 0.3, "maxGroupSize": 2 },
    { "chanceCap": 0.4, "maxGroupSize": 2 },
    { "chanceCap": 0.5, "maxGroupSize": 3 }
  ]
}
```

**Let Phantoms spawn from night 1, but only a single Phantom, 5% capped,
through night 10:**
```json
{
  "thresholdTicks": 24000,
  "tiers": [
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 },
    { "chanceCap": 0.05, "maxGroupSize": 1 }
  ]
}
```

**Only cap group size, let chance ramp naturally from night 1 onward:**
```json
{
  "thresholdTicks": 24000,
  "tiers": [
    { "chanceCap": -1, "maxGroupSize": 1 },
    { "chanceCap": -1, "maxGroupSize": 2 }
  ]
}
```

## Reloading config without a restart

`/phantomtuner reload` (requires operator/gamemaster permission) re-reads
the global file and re-resolves scope (global vs. per-world) immediately -
no server restart needed while you're tuning values, including toggling
`configScope` itself. The config is also re-loaded automatically on every
server start.

## Opening the config file in-game (Singleplayer only)

`/phantomtuner edit` opens the currently active config file (whichever one
actually applies right now - global or per-world) in your system's default
text editor - the same as double-clicking it in a file browser.

This only works in **Singleplayer** - the command checks
`server.isSingleplayer()` and refuses on a real dedicated server (a remote
server has no local desktop session to open a file in for you; instead it
replies with the file's path so you can edit it directly there).

Tries `java.awt.Desktop` first, but falls back to shelling out to the OS's
own opener (`start` on Windows, `open` on macOS, `xdg-open` on Linux) if
that's unavailable - several Minecraft launchers run the client JVM in AWT
headless mode, which makes `Desktop` entirely unusable even on a normal
desktop session, so the fallback is the common case in practice, not an
edge case. If even that fails, it prints the file's path instead of
failing silently.

## Local difficulty's role (not modified by this mod)

Before the insomnia chance is even rolled, vanilla (and this mod, unchanged)
requires a separate roll to pass first:

```
difficulty.isHarderThan(random.nextFloat() * 3.0f)
```

"Local difficulty" is a per-chunk value vanilla computes from the world's
difficulty setting (Peaceful/Easy/Normal/Hard) plus "regional difficulty" -
which itself climbs the longer a chunk has been loaded/played in
("inhabited time") and with the current moon phase. It is **not** about how
often a spawn attempt happens - the attempt cadence is a fixed 60-120
real-life seconds per level, unrelated to difficulty. What local difficulty
actually gates:

- **Whether a given player's attempt is allowed to proceed at all** this
  cycle - a higher game difficulty and a longer-inhabited chunk both raise
  the odds of passing this roll, so two players in otherwise identical
  insomnia states can still see very different real-world spawn rates.
- **The vanilla group-size range** on any night past your configured tiers
  (`1 + random(difficulty.getId() + 1)`) - Easy tops out at 2, Normal at 3,
  Hard at 4. Your `maxGroupSize` tiers override this while they're active;
  once you're past them, this vanilla difficulty-based range comes back.

Net effect: your `chanceCap` values only control the roll that happens
*after* this gate already passed - actual observed spawn frequency is the
product of both probabilities, and this mod doesn't touch the difficulty
one.

Note: as in vanilla, Creative-mode players are still fully eligible for
insomnia-based Phantom spawns - only Spectator is excluded. This is a
deliberate match to vanilla, not an oversight.

## Performance

Negligible. The custom logic only replaces vanilla's own per-tick math for
the same spawn-attempt cadence vanilla already uses (roughly once every
1-2 minutes per player) - no extra spawn attempts, no extra world queries.
Placement/legality checks are the unmodified vanilla routines.

## Troubleshooting

- **Nothing seems to spawn at all**: check `/gamerule spawn_phantoms` is
  `true`, and that players are actually above sea level with open sky
  (Overworld only) - both are unmodified vanilla requirements this mod
  doesn't touch.
- **Changed the config but nothing changed in-game**: run
  `/phantomtuner reload`, or check the server log for a
  "[SmartPhantomControl] Using GLOBAL config: ..." or "...Using PER-WORLD
  config: ..." line to confirm which file actually got parsed and used.
- **Edited the global file's tiers but a world under `"per_world"` scope
  didn't change**: expected - once a world has its own per-world file,
  the global file's tuning fields (aside from `configScope` itself) no
  longer affect it. Edit that world's own
  `<world save>/smartphantomcontrol/config.json` instead.

## Building from source

```
cd 26.2
./gradlew build
```

Built jar lands in `26.2/build/libs/`. Needs JDK 25.

## AI disclosure

This mod's code was generated by Claude (Sonnet 5), based on feature
requests and iterative design discussion with the repository owner, ported
against decompiled MC 26.2 sources to match vanilla's actual Phantom-spawn
algorithm.

## License

MIT - see [LICENSE](https://github.com/StefanBraun2001/smart-phantom-control/blob/master/LICENSE)
in the repo.
