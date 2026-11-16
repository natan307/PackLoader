# PackLoader

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![Platform](https://img.shields.io/badge/Paper%20%7C%20Spigot-supported-blue)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)

**Host your resource pack straight from your Minecraft server - no external link needed.**

PackLoader merges every resource pack you drop into a folder, hosts the result with its own built-in web server, sends it to players on join, and gives you a lightweight ItemsAdder-style toolkit on top: custom 2D/3D items, placeable 3D blocks and custom GUIs.

> **Status: early release (v1.1.0).** It has not been tested on a wide range of servers and versions yet. Please report problems with the output of `/packloader diag`.

## Features

- **Self-hosted pack** - built-in HTTP server, no Dropbox/GitHub/Drive links.
- **Pack merging** - several `.zip` files or folders are combined into one pack; common JSON files (lang, fonts, atlases, sounds, item overrides) are merged instead of overwritten.
- **Web upload** - `/packloader upload` gives a temporary, token-protected page to upload a pack `.zip` from your browser (or `curl`).
- **Custom items (2D & 3D)** - generate item models from a texture or from a Blockbench model, given with `/packloader give`.
- **Custom 3D blocks** - place and break model-based blocks (barrier collision + ItemDisplay).
- **Custom GUIs** - inventories with a texture background, buttons and actions.
- **Pack optimizer** - lossless JSON minify and PNG recompression, zip level 9, deterministic output.
- **CDN support** - serve the pack from your own URL, or let PackLoader upload it automatically after every build.
- **Support report** - `/packloader diag` writes a ready-to-send diagnostic file (secrets removed).
- No telemetry. The only network requests are the ones you configure (CDN upload) and the `diag` self-test against your own pack URL.

## Requirements

| | |
|---|---|
| Server | Paper 1.21.4+ (recommended), Spigot 1.21+ (limited, see below) |
| Java | 21 or newer, full JRE (module `jdk.httpserver`) |
| Network | TCP port `8163` reachable by players, **or** a CDN |

Details: [Compatibility](wiki/Compatibility.md).

## Quick start

1. Download or build `PackLoader-1.1.0.jar` and put it in `plugins/`.
2. Start the server once. `plugins/PackLoader/` is created with working examples.
3. Open `plugins/PackLoader/config.yml` and set `http.public-host` to your server's public IP or domain.
4. Make sure TCP port `8163` is open (or change `http.port`).
5. Drop your resource pack (`.zip` or folder) into `plugins/PackLoader/packs/`.
6. Run `/packloader reload`. Players receive the pack automatically.

Try the built-in examples:

```
/packloader give <you> ruby_flat     # 2D item
/packloader give <you> ruby_crystal  # 3D item
/packloader give <you> ruby_block    # placeable 3D block
/packloader gui example_menu         # custom GUI
/packloader browser                  # item browser
```

## Commands

Permission for everything: `packloader.admin` (default: op). Aliases: `/ploader`, `/pkl`.

| Command | Description |
|---|---|
| `/packloader reload` | Rebuild the pack and resend it if it changed |
| `/packloader send [player\|all]` | Send the pack |
| `/packloader give <player> <item> [amount]` | Give a custom item |
| `/packloader list` | List custom items |
| `/packloader gui <id> [player]` | Open a custom GUI |
| `/packloader browser` | Item browser (click = take, shift-click = stack) |
| `/packloader upload` | Temporary web page to upload a pack `.zip` |
| `/packloader url` / `info` | Pack URL / pack information |
| `/packloader diag` | Write `diag.txt` for support |

## Building from source

```bash
mvn clean package
```

The jar is created at `target/PackLoader-1.1.0.jar`. Requires JDK 21 and Maven. It compiles against `paper-api 1.21.4`.

Building on Android (Termux):

```bash
pkg update -y && pkg install -y openjdk-21 maven
mvn clean package
```

## Documentation

Full documentation is in the [wiki](../../wiki) (source files in [`wiki/`](wiki/)):

- [Installation](wiki/Installation.md)
- [Configuration](wiki/Configuration.md)
- [Resource packs](wiki/Resource-Packs.md)
- [Custom items](wiki/Custom-Items.md)
- [Custom blocks](wiki/Custom-Blocks.md)
- [Custom GUIs](wiki/Custom-GUIs.md)
- [CDN](wiki/CDN.md)
- [Commands & permissions](wiki/Commands-and-Permissions.md)
- [Compatibility](wiki/Compatibility.md)
- [Troubleshooting](wiki/Troubleshooting.md)
- [FAQ](wiki/FAQ.md)

## Support

Open an issue and attach the file created by `/packloader diag`.

## License

Copyright (c) 2026 Natan/Ezra. **All Rights Reserved** - see [LICENSE](LICENSE). Viewing and forking on GitHub is allowed by the GitHub Terms of Service; any other use requires written permission from the author.

---

PackLoader by Natan/Ezra - [github.com/natan307](https://github.com/natan307)
