# PackLoader

Plugin Bukkit / Spigot / Paper (1.21.x - 26.x) yang meng-host resource pack **langsung dari plugin** (tanpa link eksternal / Dropbox / GitHub), menggabungkan banyak pack jadi satu, dan menyediakan item custom 2D/3D ala ItemsAdder (versi ringan).

## Build

```bash
mvn clean package
```

Hasil: `target/PackLoader-1.1.0.jar` -> taruh di folder `plugins/`. Butuh Java 21+ (server 26.x memakai Java yang lebih baru, tetap kompatibel).

## Cara pakai

1. Jalankan server sekali. Folder `plugins/PackLoader/` dibuat beserta contoh.
2. Taruh resource pack di `plugins/PackLoader/packs/`:
   - file `.zip` (boleh yang di dalamnya ada 1 folder pembungkus), atau
   - folder yang berisi `assets/`.
   - Atau pakai `/packloader upload` -> buka link sementara di browser, pilih `.zip`.
3. `/packloader reload` -> pack digabung, di-host di port bawaan (8163), dan dikirim ke pemain.
4. Isi `public-host` di `config.yml` dengan IP / domain server.

> **Penting:** pemain mengunduh pack lewat HTTP dari plugin, jadi **port 8163 (TCP) harus terbuka / di-forward** di host (di panel seperti Pterodactyl, buat allocation tambahan). Minecraft tidak bisa menerima resource pack lewat koneksi game biasa.

### Menggabungkan pack
Semua pack di `packs/` digabung berurutan (alfabet). File yang sama ditimpa pack berikutnya, kecuali file JSON berikut yang di-*merge*: `lang/*.json`, `sounds.json`, `font/*.json`, `atlases/*.json`, `models/item/*.json` (overrides).

### Item custom (2D & 3D)
Buat file di `plugins/PackLoader/items/*.yml` (contoh ada di `items/example.yml`):

```yaml
items:
  ruby_flat:            # 2D dari texture
    display_name: "&cRuby"
    material: PAPER
    texture: "packloader:item/ruby"     # assets/packloader/textures/item/ruby.png

  ruby_crystal:         # 3D dari model Blockbench
    display_name: "&cRuby Crystal"
    material: PAPER
    model: "packloader:item/ruby_crystal"   # assets/packloader/models/item/ruby_crystal.json
```

Untuk 3D: export model dari Blockbench (Java Block/Item), taruh JSON di `assets/<namespace>/models/...` dan texture di `assets/<namespace>/textures/...` di dalam pack kamu. PackLoader otomatis membuat item definition (`assets/<ns>/items/<id>.json`) dan memberi item komponen `item_model`.

Beri item: `/packloader give <player> <id> [jumlah]`

Key lain: `item_model` (pakai definition buatan sendiri), `custom_model_data`, `glint`, `unbreakable`, `hide_flags`, `lore`, `parent` (`generated`/`handheld`).

## Fitur tambahan (v1.1.0)

### Blok custom 3D
Tambah `block:` pada item di `items/*.yml` (lihat `ruby_block` di contoh). Klik kanan = pasang, klik kiri = hancurkan (instan, drop item aslinya). Collision memakai blok BARRIER tak terlihat, modelnya adalah entity ItemDisplay, jadi tidak perlu mengubah pack. Event `BlockPlaceEvent` / `BlockBreakEvent` dipanggil sehingga plugin proteksi (WorldGuard dll) tetap bekerja.

Batasan: kecepatan menghancurkan tidak bisa diatur (selalu instan), model tidak punya hitbox bentuk khusus (selalu 1 blok penuh), dan blok yang dihapus lewat `/fill` atau WorldEdit bisa menyisakan entity (dibersihkan saat chunk di-load ulang).

### GUI custom
File di `plugins/PackLoader/guis/*.yml`, buka dengan `/packloader gui <id> [player]`. Background berupa PNG dari pack yang digambar lewat font di judul inventory (butuh Paper/Adventure; di Spigot murni GUI tetap jalan tapi tanpa background). Tombol bisa menjalankan command, membuka GUI lain, atau memberi item. Lihat `guis/example.yml`.

Background digambar di atas GUI vanilla. Buat area slot **transparan** di PNG supaya item tetap terlihat (contoh sudah begitu). Saya belum bisa mengetes tampilannya di semua versi klien, jadi atur `offset_x` / `offset_y` kalau posisinya bergeser.

`/packloader browser` membuka daftar semua item custom (klik = ambil, shift+klik = satu stack).

### Optimasi pack
Aktif otomatis (`optimize:` di config): JSON di-minify, data PNG dikompres ulang level maksimum tanpa mengubah piksel, metadata PNG dibuang, zip level 9. Hasil build tetap deterministik. Ukuran yang dihemat tampil di log dan `/packloader info`.

### Dukungan CDN
`cdn.mode`:
- `none` — pakai web server bawaan (default).
- `url` — pemain download dari `public-url`, kamu upload sendiri.
- `upload` — setiap build, pack di-upload otomatis (HTTP PUT/POST, cocok untuk Bunny Storage, WebDAV, URL pre-signed S3/R2). Kalau upload gagal, otomatis fallback ke server bawaan.

Placeholder `{hash}` dan `{file}` bisa dipakai di URL agar cache CDN tidak menyajikan pack lama.

### Diagnostik support
`/packloader diag` menulis `plugins/PackLoader/diag.txt` (versi server/Java, status pack, tes koneksi ke URL pack, config tanpa rahasia CDN, daftar plugin). Kirim file itu saat meminta bantuan.

## Command (permission `packloader.admin`)

| Command | Fungsi |
|---|---|
| `/packloader reload` | build ulang pack + kirim ulang |
| `/packloader send [player\|all]` | kirim pack |
| `/packloader give <player> <item> [n]` | beri item |
| `/packloader list` | daftar item |
| `/packloader upload` | link upload sementara (token) |
| `/packloader gui <id> [player]` | buka GUI custom |
| `/packloader browser` | browser item |
| `/packloader diag` | laporan untuk support |
| `/packloader url` / `info` | URL & info pack |

Alias: `/ploader`, `/pkl`.

## Catatan kompatibilitas

- Hosting & penggabungan pack: 1.21+.
- Item custom yang di-generate (item definition) butuh server **1.21.4+** (`item_model` ada sejak 1.21.2, folder `items/` sejak 1.21.4).
- `pack_format` dideteksi otomatis; `pack.mcmeta` memakai rentang format lebar. Kalau mau manual: isi `resource-pack.pack-format` atau taruh `plugins/PackLoader/pack.mcmeta` sendiri.
- Folia tidak didukung.

## Troubleshooting

- Pemain gagal download: cek port terbuka, `http.public-host` benar, buka `http://HOST:8163/pack.zip` dari browser.
- `NoClassDefFoundError com.sun.net.httpserver`: pakai JRE lengkap (modul `jdk.httpserver`).
- Peringatan "texture/model not found" di console: path di `items/*.yml` tidak cocok dengan file di pack.

---
PackLoader by ezra/natan - github.com/natan307
