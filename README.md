# Relay

A client-side Minecraft mod for sharing [Litematica](https://modrinth.com/mod/litematica) schematics
with your faction or build group: share a schematic, everyone else gets a clickable **Download** in
chat, and the file lands in their own Litematica schematics folder. After that, Relay gets out of
the way and Litematica handles the schematic as if it had always been there.

Relay does not replace any part of Litematica, and it never places a schematic in your world for
you.

> **Status: Phase 2 of 8.** The mod loads next to Litematica and answers `/relay`, and the socket
> server accepts connections, signs players in and survives being spoken to badly. The mod does not
> connect to the server yet, and no schematic has ever been transferred — see [Roadmap](#roadmap).

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
server/   the standalone socket server
client/   the Fabric mod
```

`client` compiles `common`'s classes straight into the mod jar, so there is no extra library for
players to install.

```
common/ dev/relay/common/GroupNames.java      what a group may be called
        dev/relay/common/PlayerNames.java     what a player name may be
        dev/relay/common/protocol/            frames, message types, field encoding

server/ dev/relay/server/Main.java            starts the server and waits
        dev/relay/server/ServerConfig.java    port and optional shared secret
        dev/relay/server/RelayServer.java     accept loop and lifecycle
        dev/relay/server/ClientConnection.java one socket, one reader, one writer
        dev/relay/server/ClientSession.java   a signed-in player
        dev/relay/server/SessionRegistry.java who is online
        dev/relay/server/MessageRouter.java   what each message means
        dev/relay/server/Log.java             stdout logging, no dependencies

client/ dev/relay/ModInfo.java                mod id, display name, shared logger
        dev/relay/RelayClientMod.java         entry point
        dev/relay/chat/ChatMessages.java      every chat line this mod sends
        dev/relay/commands/                   /relay and its subcommands
        dev/relay/litematica/                 the only file that knows about Litematica
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

## Running the server

```sh
./gradlew :server:run                              # straight from the source tree
./gradlew :server:installDist                      # build a launchable copy
./server/build/install/relay-server/bin/relay-server --port 25599
```

| Option | Default | |
| --- | --- | --- |
| `--port <n>` | 25599 | TCP port to listen on |
| `--secret <s>` | none | Shared secret every client must present |

The secret can also come from the `RELAY_SECRET` environment variable, which keeps it out of the
process list. With no secret set, anyone who can reach the port may connect, which is fine on a
private network and not fine on a public IP.

Everything the server knows is in memory. There is no database, no config file and no state
directory, and **a shared schematic is only held for as long as it takes to hand it to the people it
was shared with.** Restarting the server loses all groups; everyone simply makes them again.

Logs go to stdout. Redirect them if you want a file.

## Network protocol

One TCP connection per player, carrying length-prefixed frames:

```
[int32 length][uint8 type][payload ...]
```

`length` counts the type byte plus the payload, so a message with no payload has length 1. Reads
always pull a whole frame before handing it on, because one socket read is not one message. Frames
larger than 32 MiB are refused before anything is allocated, which is what stops a bogus length from
taking the server down.

Payload fields are written in order and read in the same order: `boolean`, `int32`, `int64`,
`UUID` (two longs), `string` (length-prefixed UTF-8, max 32 KiB) and `bytes` (length-prefixed, with
the reader deciding the ceiling).

| Client → server | | Server → client | |
| --- | --- | --- | --- |
| `AUTH` | Sign in | `AUTH_RESULT` | Accepted or refused, with a reason |
| `GROUP_CREATE` | *phase 3* | `GROUP_UPDATE` | *phase 3* |
| `GROUP_INVITE` | *phase 3* | `INVITE` | *phase 3* |
| `GROUP_ACCEPT` | *phase 3* | `SCHEM_SHARED` | *phase 5* |
| `GROUP_DECLINE` | *phase 3* | `SCHEM_TRANSFER` | *phase 6* |
| `GROUP_LEAVE` | *phase 3* | `ERROR` | Something went wrong, in words |
| `GROUP_INFO` | *phase 3* | | |
| `SCHEM_SHARE` | *phase 5* | | |
| `SCHEM_REQUEST` | *phase 6* | | |

`PING`/`PONG` go either way. Message type ids are fixed numbers with gaps left between the groups,
so later additions never renumber existing ones.

`AUTH` carries the protocol version, the player's UUID, their name and the shared secret. Version
mismatches are refused with an explanation rather than left to fail mysteriously later. Identity is
whatever the client claims: this is a drop box for a group that already trusts each other, not an
account system. The secret is what keeps strangers out, and it is compared in constant time.

## Commands

| Command | What it does today |
| --- | --- |
| `/relay`, `/relay status` | Mod version, whether Litematica was found, connection state |
| `/relay group`, `/relay group info` | Your group — currently reports that there is no server connection |
| `/relay group create <name>` | Validates the name, then reports that there is no server connection |

**Everything lives under `/relay` on purpose.** These are client commands, so they are handled
locally and never reach the multiplayer server you are playing on. A bare `/group` would therefore
swallow the `/group` of any faction server that has one, so Relay does not register one — and later
phases will use `/relay schem share <name>` rather than `/schem share <name>` for the same reason.

## Manual test checklist

`./gradlew test` covers everything that is not Minecraft: frame encoding and decoding, frames split
across reads, oversize and malformed frames, field encoding, name rules, argument parsing, and the
server itself driven over real sockets by a plain socket client. It also checks which commands parse,
without starting the game.

What needs a real client is checked by hand:

1. `./gradlew runClient`
2. In the log, confirm `Starting Relay`, `Found Litematica 0.26.16`, and `Relay ready`.
3. Create or open any world.
4. `/relay` prints the mod version, `Litematica 0.26.16 detected`, and a disconnected line.
5. `/relay group` reports that there is no server connection.
6. `/relay group create Alpha` reports that there is no server connection.
7. `/relay group create x` is rejected as too short, and explains the naming rules.
8. `/group` is *not* one of ours — it should behave exactly as the server you are on decides.
9. Litematica itself still works: <kbd>M</kbd> opens its menus as usual.

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
| 2 | TCP socket server, message framing, sessions, connect/disconnect — **done** |
| 3 | Groups: create, invite, accept, decline, leave, info (and the mod's side of the socket) |
| 4 | Finding and reading local Litematica schematics |
| 5 | Sharing a schematic, clickable chat message |
| 6 | Downloading into the local Litematica folder |
| 7 | Transfer IDs, size limits, filename sanitising, failure handling |
| 8 | Polish, protocol documentation, full README |
