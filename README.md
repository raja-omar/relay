# Relay

A client-side Minecraft mod for sharing [Litematica](https://modrinth.com/mod/litematica)
placements with your faction or build group. Click **Share** on a placement; everyone else in the
group gets that same placement loaded in Litematica — same origin, rotation and mirror — without a
file appearing on their disk.

Relay does not replace any part of Litematica, and it never places a schematic in the world for you.

> **Status: Phase 8 of 8, v0.2.0.** Players can connect, form a group, and share a loaded
> Litematica placement. The schematic socket is TLS with a packed cert pin; issued ids are the
> license. See [Selling copies](#selling-copies).

## Architecture

```
Minecraft client (Relay mod)
        |
        | TLS, cert pin
        v
   Relay server  (small Java process, in-memory state only)
        |
        | TLS, cert pin
        v
Other Minecraft clients (Relay mod)
```

The server is a relay and a group registry, nothing more. Groups, members and recent transfer ids
live in memory: **no database and no storage of schematic files.** If the server restarts, groups
are gone and that is fine. Issued player ids are the exception — they are hashed into a local
file so a restart does not lock everyone out.

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

Players need Fabric Loader and Fabric API. MaLiLib and Litematica are nested inside the Relay jar.

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
        dev/relay/common/PlacementNames.java  what a shared placement may be called
        dev/relay/common/RecentIds.java       short memory of transfer ids
        dev/relay/common/SchematicLimits.java size and gzip checks
        dev/relay/common/AccessTokens.java    issued player ids (format, hash)
        dev/relay/common/Tls.java             self-signed certificate and client pin
        dev/relay/common/protocol/            frames, message types, field encoding

server/ dev/relay/server/Main.java            starts the server, or invite/revoke/list
        dev/relay/server/ServerConfig.java    port, allowlist file, TLS keystore
        dev/relay/server/AbuseLimits.java     per-IP, auth, and share caps
        dev/relay/server/RelayServer.java     accept loop and lifecycle
        dev/relay/server/ClientConnection.java one socket, one reader, one writer
        dev/relay/server/ClientSession.java   a signed-in player
        dev/relay/server/SessionRegistry.java who is online
        dev/relay/server/MessageRouter.java   what each message means
        dev/relay/server/player/              issued ids, hashed on disk
        dev/relay/server/group/               create, invite, accept, leave, info
        dev/relay/server/share/               relay a placement to the rest of the group
        dev/relay/server/Log.java             stdout logging, no dependencies

client/ dev/relay/ModInfo.java                mod id, display name, shared logger
        dev/relay/RelayClientMod.java         entry point
        dev/relay/RelayClient.java            connection + group, as the game sees them
        dev/relay/RelayConfig.java            host / port / TLS pin / player id
        dev/relay/network/                    socket, identity, reconnect, keepalive
        dev/relay/group/                      last GROUP_UPDATE the server sent
        dev/relay/chat/ChatText.java          Lunar Adventure components for every chat line
        dev/relay/chat/ChatMessages.java      convert those components onto Minecraft
        dev/relay/chat/RelayAdventure.java    audiences and native conversion
        dev/relay/commands/                   /relay and its subcommands
        dev/relay/gui/                        the Relay window (Right Shift or /relay gui)
        dev/relay/schematic/                  local Litematica files: list, find, read
        dev/relay/litematica/                 the only file that knows about Litematica
        dev/relay/place/                      cant-miss, shop, refill, fast place
        dev/relay/patchcrumbs/                last TNT or sand shot, marked in the world
        dev/relay/mixin/                      Share button on Litematica's placements list
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

Its game directory is `client/run/`. A second client, for trying invitations between two players:

```sh
./gradlew runClientTwo
```

That one logs in as `Beta` and uses `client/run2/`. Start the schematic server first, copy the TLS
pin from its log, and issue an id for each client (`relay-server invite Alice`, `relay-server
invite Beta`). Then `/relay login` and `/relay connect localhost 25599 <pin>` in both clients.

## Running the server

```sh
./gradlew :server:run                              # straight from the source tree
./gradlew :server:installDist                      # build a launchable copy
./server/build/install/relay-server/bin/relay-server invite Alice
./server/build/install/relay-server/bin/relay-server --port 25599
```

| | Default | |
| --- | --- | --- |
| `--port <n>` | 25599 | TCP port to listen on |
| `--players <file>` | `relay-players` | Hashed allowlist of issued ids |
| `--tls <file>` | `relay-tls.p12` | Self-signed certificate; created on first listen |
| `invite <name>` | | Issue (or replace) a player id and print it once |
| `revoke <name>` | | Forget that player; their id stops working |
| `list` | | Names that currently have an id |

On first listen the server writes `relay-tls.p12` and prints a **TLS pin**. Pack that pin in the
client jar as `tlsPin=` next to `host` and `port`. Keep the `.p12` file; a new one is a new pin
and every sold jar would have to be rebuilt.

`invite` prints a line the player pastes as `/relay login rly_…`. The id is not stored in the
clear — only a hash, the name, and a stable UUID go in the allowlist file. Re-inviting the same
name keeps the UUID (so groups still recognise them) and kills the old id. An invite takes effect
on the next sign-in; the running server does not need a restart.

`invite` and the listening process must share the same allowlist file. Use the same working
directory, or pass the same `--players` path to both.

Nobody can sign in without an issued id. The public mod jar must not contain one. Sell the jar,
then invite buyers one name at a time. `revoke` is the kill switch.

The socket is TLS. Unsigned ids cannot be read off the wire by a network observer. Shares are
rate-limited (a short burst, then about a dozen per minute and 48 MiB per minute per player).
Each IP may hold 32 connections; the process as a whole 200. Frames before sign-in are capped
at 4 KiB.

Everything else the server knows is in memory. **A shared schematic is only held for as long as it
takes to hand it to the people it was shared with.** Restarting the server loses all groups;
everyone simply makes them again. Issued ids survive in `relay-players`. TLS keys survive in
`relay-tls.p12`.

Logs go to stdout. Redirect them if you want a file.

### Putting this on the internet

Run Relay on its **own small VM**. Open only port 25599. SSH through IAP or your own IP, not the
world. Set a GCP budget alert on that project so a noisy client cannot be a surprise invoice.

The address in a sold jar is public. Safety is the issued id, TLS pin, rate limits, and that
isolated VM — not hoping nobody finds the host.

### Selling copies

1. Run the server once, copy the TLS pin from the log, and keep `relay-tls.p12` on that VM.
   Do not put the `.p12` in the mod jar.
2. Pack `host`, `port`, and `tlsPin` in `relay-defaults.properties` inside the client jar.
   Never pack a player id.
3. Hand over the jar. After payment, `invite` that player and send them the `/relay login`
   line once (not in a public Discord channel).
4. `revoke` is how a copy stops working. Re-`invite` the same name if they leak their id.
5. Set a GCP budget alert. Rate limits cap a noisy client; they do not cap a stolen VM.

## Network protocol

Protocol version **4**, always over TLS. One connection per player, carrying length-prefixed frames:

```
[int32 length][uint8 type][payload ...]
```

`length` counts the type byte plus the payload, so a message with no payload has length 1. Reads
always pull a whole frame before handing it on, because one socket read is not one message. Frames
larger than 32 MiB are refused before anything is allocated, which is what stops a bogus length from
taking the server down. Before sign-in the ceiling is 4 KiB, so an unsigned socket cannot push a
schematic-sized frame.

Payload fields are written in order and read in the same order: `boolean`, `int32`, `int64`,
`UUID` (two longs), `string` (length-prefixed UTF-8, max 32 KiB) and `bytes` (length-prefixed, with
the reader deciding the ceiling).

| Client → server | | Server → client | |
| --- | --- | --- | --- |
| `AUTH` | Sign in | `AUTH_RESULT` | Accepted or refused, with a reason |
| `GROUP_CREATE` | Make a group | `GROUP_UPDATE` | Current members and who is online |
| `GROUP_INVITE` | Invite a connected player | `INVITE` | Invitation, shown as [Accept] [Decline] |
| `GROUP_ACCEPT` | Accept an invitation | `NOTICE` | Something happened, in words |
| `GROUP_DECLINE` | Decline an invitation | `ERROR` | Something went wrong, in words |
| `GROUP_LEAVE` | Leave the current group | `SCHEM_SHARED` | Placement loaded in memory |
| `GROUP_INFO` | Ask for a fresh member list | `SCHEM_TRANSFER` | unused (shares are not files) |
| `SCHEM_SHARE` | Placement as compressed NBT | | |
| `SCHEM_REQUEST` | unused | | |
| `BLOCK_PING` | Block a player marked | `BLOCK_PINGED` | That mark, for the rest of the group |

`PING`/`PONG` go either way. Message type ids are fixed numbers with gaps left between the groups,
so later additions never renumber existing ones.

`AUTH` carries the protocol version and the issued player id. Version mismatches are refused with
an explanation rather than left to fail mysteriously later. Identity is the name and UUID bound to
that id on the server — the client does not get to pick who it is. An unknown or blank id is
refused and the socket is closed.

### Share payloads

A placement is gzip-compressed NBT of the schematic plus its origin, rotation and mirror. The
ceiling is 16 MiB. The name is 1–64 characters with no control characters (no line breaks).

```
SCHEM_SHARE   uuid id, string name, bytes nbt
SCHEM_SHARED  uuid id, string sender, string name, bytes nbt
```

The id is generated by the sender. The server and each client remember recent ids so the same
share is not forwarded or loaded twice. Recipients call Litematica's in-memory load; nothing is
written to the schematics folder.

A block ping is the block position, the face that was aimed at (`0`–`5`), and the dimension id.
The sender's color and timer are not on the wire. Each client draws the ping with its own settings.
The sender is not sent their own ping back.

```
BLOCK_PING    int32 x, int32 y, int32 z, int32 face, string dimension
BLOCK_PINGED  string sender, int32 x, int32 y, int32 z, int32 face, string dimension
```

## Configuring the client

A release jar can pack `relay-defaults.properties` (host, port, tlsPin) so a player only drops the
mod in and logs in. That file is not committed, and it must never contain a player id. Copy the
TLS pin from the server log.

`config/relay.properties` next to Minecraft's other config overlays those defaults. The player id
lives only there, written by `/relay login` — never typed as a normal chat message. For a jar with
no baked address, or for the dev clients:

```
host=127.0.0.1
port=25599
tlsPin=
token=
```

`token` is the id `relay-server invite` printed. An empty `relay.properties` from an older install
will override baked defaults; delete it if the packed address should win. An old `secret=` line is
ignored.

`/relay login <id>` writes `token` and connects if a host and TLS pin are already set.
`/relay connect <host> [port] [pin]` writes `host`, `port`, and optionally `tlsPin`. The mod also
connects on startup whenever host, pin, and id are already set.

## Commands

Every command also lives in the Relay window: **Right Shift** (remappable) or `/relay gui`. The
overlay is a compact Lunar-style menu. Chat, action-bar hints, labels and tooltips are
[Lunar Adventure](https://github.com/LunarClient/lunar-adventure) (Kyori Adventure) components;
owo-ui only does layout. The sidebar is organised by category — **Litematica**, **Fast Place**,
**Patchcrumbs**, **Groups**, **Hotkeys**. Litematica keeps Placements, Library, Shares
and Place helpers as chips inside that category. Enabling Easy Place also turns on Litematica's
hold-to-place; Easy Place First is its own toggle. Placement nudge buttons move the schematic
relative to the way you are facing. Chat buttons still run the same commands.

| Command | What it does |
| --- | --- |
| `/relay`, `/relay status` | Mod version, Litematica, connection, current group |
| `/relay gui` | Open the Relay window |
| `/relay connect` | Connect using the saved address |
| `/relay connect <host> [port] [pin]` | Point at a server, remember it (and the TLS pin), and connect |
| `/relay login <id>` | Store the issued player id and connect if a host and pin are set |
| `/relay disconnect` | Hang up and stop retrying |
| `/relay group`, `/relay group info` | Group name, owner, members, online/offline |
| `/relay group create <name>` | Create a group and become its owner |
| `/relay group invite <player>` | Invite a player who is connected to the schematic server |
| `/relay group accept <name>` | Accept an invitation (also on the chat button) |
| `/relay group decline <name>` | Decline an invitation (also on the chat button) |
| `/relay group leave` | Leave. The longest-standing remaining member becomes owner |
| `/relay schem`, `/relay schem list` | Local Litematica schematics on disk, with sizes |
| `/relay schem info <name>` | Confirm a name, and that the file is a usable schematic |
| `/relay schem download <id>` | Load a pending share in Litematica (also on the chat button) |
| `/relay cantmiss on` / `off` | Right-click only places when it matches the schematic |
| `/relay shop auto on` / `off` | Buy a missing schematic block with `/shop` |
| `/relay shop pre on` / `off` | Top the stack up after a successful place |
| `/relay refill on` / `off` | Restock emptied build-hotbar slots from the backpack |
| `/relay patchcrumbs on` / `off` | Mark the last TNT or falling-sand shot so you can patch the wall |

Sharing is not a command. Select a loaded placement in Relay **Litematica → Placements** and click
**Share**, or open Litematica's **Schematic Placements** list and click **Share** there. Other
members get a **[Download]** chat button; nothing is written to their schematics folder.

Cant-miss, Easy Place, auto-purchase, pre-purchase, refill, Fast Place and Patchcrumbs can also be
bound under **Controls → Relay**, or from the Relay **Hotkeys** page. Those hotkeys start unbound so they do
not steal keys from Litematica.

**Everything lives under `/relay` on purpose.** These are client commands, so they are handled
locally and never reach the multiplayer server you are playing on. A bare `/group` would therefore
swallow the `/group` of any faction server that has one, so Relay does not register one.

A player is in at most one group. Any member may invite. Invitations last five minutes and only
reach players who are currently connected to the schematic server. Membership survives a disconnect:
reconnecting puts you back in the same group. Restarting the *schematic server* forgets every group.

## Manual test checklist

`./gradlew test` covers everything that is not Minecraft: frame encoding and decoding, frames split
across reads, oversize and malformed frames, field encoding, name rules, issued player ids, group
rules (create, invite, expire, accept, decline, leave, ownership), argument parsing, the server
driven over real sockets by two plain clients, and the mod's socket talking to that same server. It
also checks which commands parse, without starting the game, and that a folder of schematic files
can be listed, identified by name, read and rejected when empty, oversize or not actually a
schematic.

What needs a real client is checked by hand:

1. `./gradlew :server:run` in one terminal. In another: `relay-server invite Alice` and
   `relay-server invite Beta` (or the `installDist` binary).
2. `./gradlew runClient` and, in another, `./gradlew runClientTwo`.
3. In both clients, create or open any world, `/relay login` with the matching id, then
   `/relay connect localhost 25599 <pin>` if the address is not already saved. Copy the pin
   from the server log.
4. Client one: `/relay group create Alpha`.
5. Client one: `/relay group invite Beta`.
6. Client two: the invitation appears with **[Accept] [Decline]**. Click Accept.
7. Both clients: `/relay group` lists Alice (owner) and Beta, both online.
8. Close client two. Client one should see Beta go offline, still in the group.
9. Reopen client two and `/relay connect localhost` again (pin already saved). Both should show
   Beta online.
10. `/relay group leave` on the owner hands ownership to Beta.
11. `/group` is *not* one of ours — it should behave exactly as the Minecraft server you are on decides.
12. Litematica itself still works: <kbd>M</kbd> opens its menus as usual.
13. Save any schematic with Litematica (or drop a `.litematic` into `client/run/schematics/`).
14. `/relay schem` lists it. `/relay schem info <name>` shows the size. A made-up name is an error.
15. Client one: load the schematic in Litematica, create a placement, rotate or move it.
16. Client one: <kbd>M</kbd> → Schematic Placements → **Share**.
17. Client two: a **[Download]** button appears (or open Relay with Right Shift → Litematica → Shares).
    After download, the same placement is in Loaded Schematics and in the world, same origin and
    rotation. No new file in `client/run2/schematics/`.
18. Right Shift opens the Relay window. Group, Litematica and Hotkeys match the `/relay` commands.
    Litematica → Placements can turn a placement on or off, move it relative to your facing, unload
    it, and share it. ESC or Right Shift again closes it.
19. Share the same placement again: client two loads a second copy only if it is a new transfer.
    A replay of the same id is ignored.

## Renaming the mod

Relay is a placeholder name. To change it:

1. `mod_id`, `mod_name`, `mod_author`, `mod_license`, `mod_description` and `maven_group` in
   `gradle.properties`.
2. `ID` and `NAME` in `client/src/main/java/dev/relay/ModInfo.java` (they must match `mod_id` and
   `mod_name`).
3. The `dev.relay` package name, and the entry point path in
   `client/src/main/resources/fabric.mod.json`.
4. The keybind strings in `client/src/main/resources/assets/relay/lang/en_us.json`.

Nothing else spells the name out: chat prefixes, log names and the `/relay` command all read it
from `ModInfo`.

## Roadmap

| Phase | |
| --- | --- |
| 1 | Project foundation, mod loads beside Litematica, basic commands — **done** |
| 2 | TCP socket server, message framing, sessions, connect/disconnect — **done** |
| 3 | Groups: create, invite, accept, decline, leave, info, and the mod's socket — **done** |
| 4 | Finding and reading local Litematica schematics — **done** |
| 5 | Share button on a Litematica placement, loaded in memory on the other client — **done** |
| 6 | Downloading into the local Litematica folder — skipped (shares stay in memory) |
| 7 | Transfer IDs, size limits, name sanitising, failure handling — **done** |
| 8 | Polish, protocol documentation, full README — **done** |
