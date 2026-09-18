# Relay

A client-side Minecraft mod for sharing [Litematica](https://modrinth.com/mod/litematica) schematics
with your faction or build group: share a schematic, everyone else gets a clickable **Download** in
chat, and the file lands in their own Litematica schematics folder. After that, Relay gets out of
the way and Litematica handles the schematic as if it had always been there.

Relay does not replace any part of Litematica, and it never places a schematic in your world for
you.

> **Status: Phase 1 of 8.** The project builds, the mod loads next to Litematica, and `/relay` and
> `/group` respond. There is no socket server or schematic transfer yet — see
> [Roadmap](#roadmap).

## Architecture

```
Minecraft client (Relay mod)
        |
        | TCP
        v
   Relay server  (small Java process, in-memory state only)
        |
        | TCP
        v
Other Minecraft clients (Relay mod)
```

The server is a relay and a group registry, nothing more. It holds groups, members and in-flight
transfers in memory: **no database, no persistence, and no permanent storage of schematic files.**
If the server restarts, groups are gone and that is fine.

## Requirements

| | Version | Why this one |
| --- | --- | --- |
| Minecraft | 1.21.11 | Target version |
| Java | 21 | Minecraft 1.21.11 ships `java-runtime-delta` (Java 21) |
| Fabric Loader | 0.19.5 | Matches `fabric-example-mod` branch `1.21.11` |
| Fabric API | 0.141.6+1.21.11 | Same |
| Fabric Loom | 1.17.21 | Same (pinned to a release instead of `1.17-SNAPSHOT`) |
| Mappings | Official Mojang mappings | Same |
| Litematica | 0.26.16 (Fabric) | Only 0.26.x builds target 1.21.11 |
| MaLiLib | 0.27.20 | Litematica 0.26.16 requires `>=0.27.19- <0.28.0-` |

Players need Fabric Loader, Fabric API, MaLiLib and Litematica installed. Relay bundles none of
them.

Versions live in [`gradle.properties`](gradle.properties) and are expanded into `fabric.mod.json` at
build time, so there is one place to change them.

## Project layout

```
common/   plain Java shared by the mod and the server (no Minecraft, no Fabric)
client/   the Fabric mod
```

`client` compiles `common`'s classes straight into the mod jar, so there is no extra library for
players to install. The `server` module arrives in Phase 2.

Inside `client`:

```
dev/relay/ModInfo.java              mod id, display name, shared logger
dev/relay/RelayClientMod.java       entry point
dev/relay/chat/ChatMessages.java    every chat line this mod sends
dev/relay/commands/                 /relay and /group
dev/relay/litematica/               the only file that knows about Litematica
```

## Building

```sh
./gradlew build
```

Gradle downloads a Java 21 toolchain itself, so no matching JDK needs to be installed by hand. The
mod jar ends up in `client/build/libs/relay-<version>.jar`.

To run a development client with Litematica and MaLiLib already installed:

```sh
./gradlew runClient
```

Its game directory is `client/run/`, and Litematica's schematics folder inside it is
`client/run/schematics/`.

## Commands

| Command | What it does today |
| --- | --- |
| `/relay`, `/relay status` | Mod version, whether Litematica was found, connection state |
| `/group`, `/group info` | Your group — currently reports that there is no server connection |
| `/group create <name>` | Validates the name, then reports that there is no server connection |
| `/relay group ...` | Same as `/group ...` |

`/group` is registered as a *client* command, which means it takes precedence over a `/group`
command belonging to the multiplayer server you are on. Use `/relay group ...` if that gets in your
way.

## Manual test checklist

`./gradlew test` covers the plain-Java logic in `common` and the shape of the command tree (which
commands parse, including the `/relay group` redirect) without starting the game. What needs a real
client is checked by hand:

1. `./gradlew runClient`
2. In the log, confirm `Starting Relay`, `Found Litematica 0.26.16`, and `Relay ready`.
3. Create or open any world.
4. `/relay` prints the mod version, `Litematica 0.26.16 detected`, and a disconnected line.
5. `/group` reports that there is no server connection.
6. `/group create Alpha` reports that there is no server connection.
7. `/group create x` is rejected as too short, and explains the naming rules.
8. Litematica itself still works: <kbd>M</kbd> opens its menus as usual.

## Renaming the mod

Relay is a placeholder name. To change it:

1. `mod_id`, `mod_name` and `maven_group` in `gradle.properties`.
2. `ID` and `NAME` in `client/src/main/java/dev/relay/ModInfo.java` (they must match `mod_id` and
   `mod_name`).
3. The `dev.relay` package name, and the entry point path in
   `client/src/main/resources/fabric.mod.json`.

Nothing else spells the name out: chat prefixes, log names and the `/relay` command all read it
from `ModInfo`.

## Roadmap

| Phase | |
| --- | --- |
| 1 | Project foundation, mod loads beside Litematica, basic commands — **done** |
| 2 | TCP socket server, message framing, connect/disconnect |
| 3 | Groups: create, invite, accept, decline, leave, info |
| 4 | Finding and reading local Litematica schematics |
| 5 | Sharing a schematic, clickable chat message |
| 6 | Downloading into the local Litematica folder |
| 7 | Transfer IDs, size limits, filename sanitising, failure handling |
| 8 | Polish, protocol documentation, full README |
