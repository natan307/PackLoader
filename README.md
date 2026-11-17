# PackLoader
![PackLoader Banner](https://imgur.com/ZKFEI7G.img) 
![Minecraft](https://img.shields.io/badge/Minecraft-1.21%20--%2026.3-brightgreen)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![Platform](https://img.shields.io/badge/Paper%20%7C%20Spigot-supported-blue)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)

**Host your Minecraft resource pack straight from your server - no external link needed.**

PackLoader merges every resource pack you drop into a folder, hosts the result with its own built-in web server, sends it to players on join, and adds a lightweight ItemsAdder-style toolkit: custom 2D/3D items, placeable 3D blocks and custom GUIs.

## Features

- **Self-hosted pack** - built-in HTTP server, no Dropbox / GitHub / Drive links.
- **Pack merging** - combine several `.zip` files or folders into one pack. Lang, fonts, atlases, sounds and item overrides are merged instead of overwritten.
- **Web upload** - `/packloader upload` creates a temporary, token-protected page to upload a pack `.zip` from your browser (or `curl`).
- **Custom items (2D & 3D)** - build items from a texture or a Blockbench model and give them with `/packloader give`.
- **Custom 3D blocks** - place and break model-based blocks.
- **Custom GUIs** - inventory menus with a texture background, buttons and actions.
- **Pack optimizer** - lossless JSON minify and PNG recompression, deterministic output.
- **CDN support** - serve the pack from your own URL, or let PackLoader upload it after every build.
- **Support report** - `/packloader diag` writes a ready-to-send diagnostic file with secrets removed.
- **No telemetry.** The only outgoing requests are the ones you configure (CDN upload) and a self-test of your own pack URL in `/packloader diag`.

## Compatibility

| | |
|---|---|
| Minecraft | **1.21 - 26.3** (tested by the author) |
| Server | Paper (main target). Spigot works with limits: no GUI backgrounds |
| Java | 21 or newer, full runtime (module `jdk.httpserver`) |
| Not supported | Folia, hybrid servers, Fabric, Forge |

Custom item models and 3D items need Minecraft **1.21.4 or newer** on both server and client. Details: [Compatibility](https://github.com/natan307/PackLoader/wiki/Compatibility).

## Download

Get the latest `.jar` from the [Releases](https://github.com/natan307/PackLoader/releases) page.

## Quick start

> [!IMPORTANT]
> Players download the pack over HTTP from a **separate TCP port**, not through the normal game port. You need an **extra port** (default `8163`) that players can reach. On a hosting panel, create an extra port/allocation and put it in `http.port`. **Do not use the Minecraft port (25565).** If your host gives you only one port, use a [CDN](https://github.com/natan307/PackLoader/wiki/CDN) instead.

1. Put `PackLoader-<version>.jar` in `plugins/` and start the server once.
2. Open `plugins/PackLoader/config.yml` and set:

   ```yaml
   http:
     port: 8163                       # your extra port
     public-host: "play.example.com"  # your domain or public IP, no http://, no port
   ```

3. Restart the server (a port change needs a restart).
4. Drop your resource pack (`.zip` or folder) into `plugins/PackLoader/packs/`.
5. Run `/packloader reload`. Players receive the pack automatically.
6. Test: run `/packloader url` and open the link in a browser on another network. A zip file should download.

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

## Documentation

Everything is in the [Wiki](https://github.com/natan307/PackLoader/wiki):

[Installation](https://github.com/natan307/PackLoader/wiki/Installation) ·
[Configuration](https://github.com/natan307/PackLoader/wiki/Configuration) ·
[Resource Packs](https://github.com/natan307/PackLoader/wiki/Resource-Packs) ·
[Custom Items](https://github.com/natan307/PackLoader/wiki/Custom-Items) ·
[Custom Blocks](https://github.com/natan307/PackLoader/wiki/Custom-Blocks) ·
[Custom GUIs](https://github.com/natan307/PackLoader/wiki/Custom-GUIs) ·
[CDN](https://github.com/natan307/PackLoader/wiki/CDN) ·
[Commands](https://github.com/natan307/PackLoader/wiki/Commands-and-Permissions) ·
[Compatibility](https://github.com/natan307/PackLoader/wiki/Compatibility) ·
[Troubleshooting](https://github.com/natan307/PackLoader/wiki/Troubleshooting) ·
[FAQ](https://github.com/natan307/PackLoader/wiki/FAQ)

## Support

Open an [issue](https://github.com/natan307/PackLoader/issues) and attach the file created by `/packloader diag`.

## License

Copyright (c) 2026 Natan/Ezra. **All Rights Reserved** - see [LICENSE](LICENSE). Viewing and forking on GitHub is allowed by the GitHub Terms of Service; any other use requires written permission from the author.

---

PackLoader by Natan/Ezra - [github.com/natan307](https://github.com/natan307)
