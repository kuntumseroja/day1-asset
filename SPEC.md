# D-ETP Day-One Assets — Cursor Build Spec & Sprint Instructions

**Project Garuda / SATRIA · wholesale Rupiah Digital (wRD) · IBM Consulting as SI (Non-DLT scope)**
Scope: assets **1–6** dari slide "Assets on day one — not blank pages" (asset 7 planning estate & 8 benchmark fluency = dokumen, bukan software — tidak termasuk).

**Cara pakai file ini di Cursor:** buka repo kosong, taruh file ini sebagai `SPEC.md`, lalu kerjakan per-asset: paste blok **Sprint instruction** asset tersebut ke chat Cursor sebagai prompt, dengan file ini di context. Setiap asset punya *Definition of Done* berupa demo yang bisa dijalankan — sprint belum selesai sebelum demo-nya jalan.

---

## 0 · Global conventions (baca dulu, berlaku untuk semua asset)

### Monorepo layout

```
detp-dayone/
├── SPEC.md                     # file ini
├── docker-compose.yml          # dev stack (lihat bawah)
├── contracts/                  # SATU sumber kebenaran antar-asset
│   ├── openapi/                #   portal-bff.yaml, policy.yaml, recon.yaml
│   ├── avro/                   #   settlement-events.avsc, config-events.avsc
│   └── pact/                   #   pact broker artefacts (asset 6)
├── portal/                     # Asset 1 — React 18 + TS + Carbon
├── saga-lib/                   # Asset 2 — Java 21 / Spring Boot 3 / Temporal
├── policy/                     # Asset 3 — Drools 8 service + samples
├── recon/                      # Asset 4 — three-way reconciliation service
├── ceremonies/                 # Asset 5 — runbooks + SoftHSM dry-run scripts
├── firefly-kit/                # Asset 6 — FireFly client + listener + Pact
└── sim/                        # shared simulators: rtgs-sim, firefly-stub
```

### Stack (versi dikunci — jangan upgrade tanpa alasan)

| Layer | Pilihan |
|---|---|
| Frontend | React 18, TypeScript 5, **@carbon/react**, Vite |
| BFF | Spring WebFlux (Java 21) — atau Node/Express untuk prototype, tapi kontrak OpenAPI sama |
| Backend | Java 21, Spring Boot 3.3, Temporal SDK (workflow) |
| Rules | Drools 8 (KieContainer, DRL as data) |
| Data | PostgreSQL 16, Debezium (CDC), Kafka (Redpanda di dev), Avro + schema registry |
| ISO 20022 | Prowide (pw-iso20022) untuk parse pacs.009 / camt.053 |
| DLT seam | FireFly REST + WebSocket (stub di dev; `ff init ethereum` kalau mau full lokal) |
| HSM | SoftHSM2 + pkcs11-tool (dry-run); produksi = FIPS 140-2 L3 via PKCS#11 |
| Contract tests | Pact (JVM + JS) |
| Identity | Keycloak 24 (realm lokal `detp`, klaim meniru BI IdP) |

### docker-compose dev stack

Services: `postgres:16`, `redpanda` (kafka API + schema registry), `keycloak:24` (import `detp-realm.json`), `temporal` (auto-setup), `pact-broker`, `rtgs-sim` (dari `sim/`), `firefly-stub` (dari `sim/`). Semua asset harus bisa `docker compose up` lalu jalan tanpa dependensi eksternal.

### Aturan lintas-asset (ini yang membuat prototype "survive to production")

1. **Contract-first.** UI dan service hanya bicara lewat file di `contracts/`. Simulator mengimplementasi kontrak yang sama dengan service produksi nanti.
2. **UETR adalah kunci idempotency** di semua alur uang. Dedup window 48 jam.
3. **GATE semantics:** mint hanya setelah `RTGS debit confirmed`; state hanya setelah `DLT finality` (BR-012: finality ≠ receipt).
4. **Outbox pattern** untuk semua event: tulis ke tabel outbox dalam transaksi yang sama dengan state, Debezium yang publish ke Kafka. Tidak ada dual-write.
5. **Uang tidak pernah hilang diam-diam:** kegagalan setelah funding → compensating record eksplisit, bukan retry membabi-buta.

---

## Asset 1 · Working portal prototype (`portal/`)

### Apa ini
Portal partisipan yang bisa diklik — kelanjutan PoC Garuda: entry permintaan issuance, antrean settlement FAFO live, gauge konsumsi limit, drill-down pesan ISO 20022. Bukan mockup Figma — jalan di atas simulator, semua layar berperilaku.

### Cara kerjanya
1. User login sebagai bank pilot (mis. "Bank A") via Keycloak OIDC → token membawa klaim `participant_id`, `role`, `tier` (klaim yang sama dengan BI IdP nanti).
2. Submit issuance → BFF → simulator memvalidasi limit (memanggil kontrak policy yang sama dengan Asset 3) → masuk antrean FAFO.
3. Simulator memutar "satu hari ter-skrip": antrean bergerak, status transisi (`QUEUED → FUNDING → MINTING → SETTLED`), gauge limit terkuras — semua dipush ke UI via WebSocket dengan skema Avro yang sama dengan Kafka produksi.
4. Klik transaksi mana pun → render pacs.009 / camt yang mendasarinya.

### Sprint instruction (paste ke Cursor)

> Build `portal/` + `sim/portal-sim`: React 18 + TS + @carbon/react (Vite). Screens: (S1) Login via Keycloak OIDC (realm `detp`, users bankA/bankB/operator); (S2) Issuance request form — amount Rp, value date, funding reference; validasi client-side dari OpenAPI `contracts/openapi/portal-bff.yaml` (kamu yang tulis, contract-first); (S3) FAFO queue — tabel live, posisi antrean, status chip per state, urutan strictly FIFO; (S4) Limits dashboard — per-issuance cap & daily cumulative sebagai Carbon gauge/progress, angka dari sim; (S5) Transaction detail — timeline status + tab "ISO 20022" menampilkan pacs.009 XML pretty-printed. BFF: Node/Express tipis yang mengimplementasi `portal-bff.yaml`, di belakangnya `portal-sim`: state machine in-memory yang me-replay `sim/scripted-day.json` (≥20 event: 3 issuance sukses, 1 ditolak limit, 1 duplikat UETR, redemption, transfer). Push perubahan via WebSocket (payload = JSON hasil skema `contracts/avro/settlement-events.avsc`). Setiap screen diberi komentar `// KF: BC-xx.yy` penanda traceability.

**Definition of Done:** login sebagai Bank A → submit Rp 500 M → terlihat antre di belakang 2 item pending → gauge limit bergerak → buka rendering pacs.009. Duplikat UETR di scripted day muncul sebagai satu transaksi dengan badge "duplicate suppressed".

**Integrasi (dev → prod):** Keycloak lokal → federasi BI IdP (klaim tidak berubah) · sim di belakang BFF → service BC riil (kontrak OpenAPI tidak berubah) · WS lokal → Kafka→WS gateway (skema Avro tidak berubah).

---

## Asset 2 · Saga pattern library (`saga-lib/`)

### Apa ini
Kerangka eksekusi tiga alur uang — **fund→mint** (issuance), **burn→release** (redemption), **transfer** — dengan ordering, idempotency, dan kompensasi sebagai kode, bukan konvensi.

### Cara kerjanya
1. Pesan masuk (pacs.009) → `checkIdempotency(UETR)`: sudah pernah? → kembalikan respons orisinal, selesai.
2. **GATE 1:** tunggu `RtgsDebitConfirmed` dari adapter RTGS (di dev: `rtgs-sim`). Belum confirmed = belum ada mint, titik.
3. `firefly.mint(amount, idempotencyKey=UETR)` → **GATE 2:** tunggu event finality dari listener (Asset 6).
4. `applyStateAndSupply()` (PostgreSQL, satu transaksi dengan insert outbox) → Debezium publish `SettlementCompleted` ke Kafka.
5. Gagal di langkah ≥3 padahal sudah funded → jalur kompensasi: emit `CompensatingRefundInstruction`, status saga `COMPENSATED`, tidak pernah retry diam-diam. Redemption = cermin: **burn dulu, release kemudian**.

### Sprint instruction (paste ke Cursor)

> Build `saga-lib/`: Java 21 + Spring Boot 3.3 + Temporal SDK. Tiga workflow: `IssuanceSaga`, `RedemptionSaga`, `TransferSaga`. Activities (masing-masing idempotent, keyed UETR): `checkIdempotency`, `awaitRtgsDebitConfirmed` (Temporal signal dari rtgs-sim webhook), `mintTokens` / `burnTokens` (panggil firefly-stub, teruskan UETR sebagai idempotency key), `awaitFinality` (signal dari listener Asset 6 / firefly-stub WS), `applyStateAndSupply` (PostgreSQL: update `wallet_balances`, increment `supply_ledger`, insert `outbox` — SATU transaksi), `compensateRefund`. Tabel: `saga_instances`, `idempotency_registry` (UETR unik, TTL 48h, simpan respons orisinal), `wallet_balances`, `supply_ledger`, `outbox`. Debezium connector config untuk outbox→Kafka topic `settlement.events` (Avro). Test wajib (Testcontainers): (a) duplicate-UETR replay → satu mint, respons kedua == respons pertama; (b) crash setelah mint sebelum apply → Temporal replay → state akhir sama, tidak ada double-apply; (c) mint gagal deterministik setelah funding → `CompensatingRefundInstruction` diemit, saga `COMPENSATED`; (d) redemption: release tidak pernah terjadi sebelum burn confirmed.

**Definition of Done:** `./demo-duplicate.sh` mengirim pacs.009 yang sama dua kali ke endpoint intake → log menunjukkan satu mint, satu dedup-hit, `wallet_balances` == `supply_ledger`, dan satu audit event duplikat.

**Integrasi:** rtgs-sim (pacs.009 in / debit-confirmed out) · firefly-stub (Asset 6 kit sebagai client) · PostgreSQL+Debezium+Kafka · policy service (Asset 3) dipanggil sinkron di GATE.

---

## Asset 3 · Policy-as-data samples (`policy/`)

### Apa ini
Limit System-Policy sebagai rule Drools ±15 baris yang teruji — per-issuance cap, daily cumulative cap, tier partisipan, cut-off window. Poinnya: perubahan kebijakan BI = perubahan **data** yang ter-governance, bukan release kode.

### Cara kerjanya
1. Rule = teks DRL berversi di tabel `policy_rules` (PostgreSQL) dengan `effective_from` dan status (`DRAFT → TESTED → APPROVED → ACTIVE`).
2. Perubahan: author → uji di sandbox KieContainer terhadap replay trafik kemarin → approve maker-checker (dua user berbeda, dicatat) → publish.
3. Publish menulis event `config.changed` (outbox→Kafka) → setiap enforcement point me-rebuild KieContainer-nya — **hot reload, tanpa restart**.
4. Saga (Asset 2) memanggil `POST /policy/evaluate` sinkron di GATE; portal (Asset 1) membaca rule yang sama untuk gauge — layar dan enforcement tidak mungkin beda.

### Sprint instruction (paste ke Cursor)

> Build `policy/`: Spring Boot 3.3 + Drools 8. Endpoint per `contracts/openapi/policy.yaml`: `POST /evaluate` (fakta: participantId, tier, amount, dailyCumulative, timestamp → ALLOW/DENY + reason + rule id/version), CRUD `/rules` dengan transisi status maker-checker (author ≠ approver, ditolak kalau sama), `POST /rules/{id}/test` (jalankan terhadap `sim/yesterday-traffic.json`). Empat DRL sample di `policy/samples/`: `per_issuance_cap.drl` (default Rp 500 M), `daily_cumulative_cap.drl`, `tier_limit.drl`, `cutoff_window.drl` — masing-masing ≤ ~15 baris + JUnit boundary test (pas di bawah / pas di atas / tepat di limit / di luar window). Hot reload: subscribe topic `config.events` → rebuild KieContainer; rule ber-`effective_from` masa depan baru aktif pada saatnya (uji dengan Clock yang bisa digeser). Sediakan `demo-limit-change.sh`: naikkan cap 500 M → 750 M via API (author lalu approver), tunjukkan evaluasi Rp 600 M berubah DENY → ALLOW tanpa restart proses.

**Definition of Done:** `demo-limit-change.sh` hijau; suite JUnit boundary lulus < 10 detik; percobaan approve oleh author yang sama tertolak 403 dengan audit record.

**Integrasi:** dipanggil sinkron oleh saga-lib di GATE · Kafka `config.events` untuk hot-deploy · portal membaca `/rules` aktif untuk gauge.

---

## Asset 4 · Three-way reconciliation (`recon/`)

### Apa ini
Kontrol harian atas invarian inti platform: **saldo omnibus RTGS = total ledger platform = total supply on-chain**, toleransi nol, dengan break workflow otomatis. Direkayasa sebagai kontrol, bukan laporan.

### Cara kerjanya
1. EOD (dan intraday on-demand) ambil tiga bacaan **pada satu potongan logis yang sama**: (a) camt.053 omnibus (parse Prowide) ↔ (b) `SELECT SUM(balance) FROM wallet_balances` di-snapshot pada LSN CDC yang konsisten ↔ (c) `firefly.totalSupply('wRD')` dipaku pada block height yang sudah final. Penyelarasan waktu ini yang membedakan desain benar dari desain naif — tanpa itu, saga in-flight menghasilkan false break tiap hari.
2. Item in-flight dihitung eksplisit sebagai bucket keempat "pipeline", sehingga persamaan tetap tertutup di tengah alur: `omnibus = platform + pipeline_in = chain + pipeline_out`.
3. Selisih ≠ 0 → auto-create `DiscrepancyCase`: tiga saldo, delta, kandidat UETR yang terisolasi ke gap, timer SLA → tile merah di dashboard + alert.
4. Resolusi (mis. replay konfirmasi yang hilang) → run berikutnya hijau, case ditutup dengan evidence.

### Sprint instruction (paste ke Cursor)

> Build `recon/`: Spring Boot 3.3. Job `ReconRun` (scheduled EOD + `POST /recon/run` on-demand): (1) fetch camt.053 dari rtgs-sim, parse closing balance dengan Prowide; (2) baca `wallet_balances` dan `supply_ledger` dalam repeatable-read transaction, catat LSN; (3) `GET /tokens/wRD/supply?blockHeight=finalized` ke firefly-stub; (4) hitung pipeline bucket dari `saga_instances` yang berstatus antara FUNDED dan APPLIED; (5) evaluasi persamaan, toleransi 0. Break → insert `discrepancy_cases` (tiga saldo, delta, daftar UETR kandidat = saga yang menyentuh window, SLA deadline) + publish `recon.break` ke Kafka. Endpoint `GET /cases`, `POST /cases/{id}/resolve`. UI mini (boleh satu halaman React di portal): tile hijau/merah + daftar case. Test skenario wajib: `demo-break.sh` — suppress satu konfirmasi mint di firefly-stub → run recon → case terbuka dengan delta persis sebesar mint itu dan UETR-nya teridentifikasi → replay konfirmasi → run lagi → hijau, case closed.

**Definition of Done:** `demo-break.sh` end-to-end hijau; recon dengan 3 saga in-flight yang sehat menghasilkan **NOL** false break (ini test paling penting).

**Integrasi:** rtgs-sim camt.053 · Debezium/LSN dari data store saga-lib · firefly-stub query API · Kafka + case store (BC-12) + tile dashboard.

---

## Asset 5 · Ceremony runbooks (`ceremonies/`)

### Apa ini
Runbook upacara kunci HSM — KC-01 generate root/issuer key, rotasi, rekonstitusi DR — ditulis ke standar produksi sistem pembayaran: peran bernama, dual control, saksi, kriteria abort, dan evidence pack yang dihasilkan sambil jalan. Asset ini = **dokumen + skrip dry-run**, bukan service.

### Cara kerjanya
1. Peran: ceremony master, 2 key custodian, security officer, witness, scribe. Pre-condition ter-skrip: atestasi firmware HSM, review tamper-log, inventaris amplop tersegel.
2. Urutan perintah bernomor terhadap HSM via PKCS#11; setiap aksi sensitif = dual control (dua orang, dua kredensial).
3. Backup: Shamir 3-of-5, share didistribusi ke custodian terpisah (situs/brankas berbeda).
4. Abort criteria eksplisit: respons HSM tak terduga atau deviasi dari skrip → stop, log, jadwalkan ulang. Tidak ada improvisasi.
5. Setiap langkah menghasilkan bukti (foto, atestasi bertanda tangan, ekstrak audit-log HSM) → dirakit jadi evidence pack, diarsip ke BC-12.

### Sprint instruction (paste ke Cursor)

> Build `ceremonies/`: (1) `runbooks/KC-01_key_generation.md`, `KC-02_rotation.md`, `KC-03_dr_reconstitution.md` — format: header (tujuan, peran+nama kosong untuk diisi, prasyarat, abort criteria), lalu tabel langkah bernomor [No | Aktor | Perintah/Aksi | Output diharapkan | Bukti | Paraf 1 | Paraf 2]. KC-02 mencakup protokol koordinasi rotasi lintas entitas validator (BR-011). (2) `scripts/`: dry-run terhadap **SoftHSM2** — `00_init_softhsm.sh` (init token, PIN terpisah SO/user), `01_generate_keypair.sh` (pkcs11-tool gen EC secp256k1, label `wrd-issuer-1`), `02_backup_shamir.sh` (ssss-split 3-of-5 atas wrapped key), `03_restore_from_shares.sh` (ssss-combine → unwrap → verifikasi public key identik), `04_sign_test_mint.sh` (sign payload uji, verifikasi signature), `05_evidence_pack.sh` (kumpulkan semua output + checksum + timestamp → `evidence/KC-01_<date>/` + `manifest.json` + render `evidence_pack.pdf` via pandoc). Setiap skrip berhenti dengan exit≠0 dan pesan ABORT bila output tak sesuai ekspektasi — cermin kriteria abort runbook.

**Definition of Done:** `make dry-run` menjalankan 00→05 di container bersih: keypair dibuat, di-backup 3-of-5, direstorasi dari 3 share sembarang, menandatangani test mint, dan `evidence/` berisi pack lengkap dengan manifest checksum.

**Integrasi:** SoftHSM2 dev → HSM FIPS L3 produksi (skrip sama, modul PKCS#11 beda) · public key hasil ceremony = signing identity yang dikonsumsi key-manager FireFly/Besu (Asset 6) · evidence pack → BC-12.

---

## Asset 6 · FireFly integration kit (`firefly-kit/`)

### Apa ini
Pola panggilan FireFly dari PoC — submit mint + listener konfirmasi — dianotasi ke tingkat produksi, plus **Pact contract test dua arah** yang jalan di CI IBM *dan* CI Kaleido: tidak ada pihak yang bisa merusak antarmuka tanpa build pihak lain merah.

### Cara kerjanya
1. **Submit:** `POST /api/v1/tokens/mint` via SDK, idempotency key = UETR diteruskan; taksonomi error terpeta — transient → retry backoff; deterministic reject → jalur kompensasi (Asset 2).
2. **Listen:** WebSocket subscribe `token_mint_confirmed` dengan **offset durable & replay-safe**: reconnect → resume dari offset ter-ack terakhir. Tidak ada konfirmasi hilang atau diterapkan dua kali — inilah yang membuat GATE saga boleh percaya pada event.
3. **Finality ≠ receipt (BR-012):** kit hanya meneruskan event yang sudah final menurut QBFT, bukan sekadar diterima.
4. **Kontrak dua arah:** IBM publish consumer contract (request mint + ekspektasi event) ke Pact broker; Kaleido verifikasi sebagai provider di CI mereka — dan sebaliknya untuk skema event.

### Sprint instruction (paste ke Cursor)

> Build `firefly-kit/` (Java 21 library + `sim/firefly-stub`): (1) `FireflyMintClient` — POST `/api/v1/tokens/mint` (body: pool `wRD`, amount, key, `idempotencyKey`), error taxonomy enum {TRANSIENT_RETRY, DETERMINISTIC_REJECT, DUPLICATE_ACCEPTED} dengan mapping HTTP/error-code → aksi; retry exponential backoff hanya untuk TRANSIENT. (2) `ConfirmationListener` — WS client subscribe `token_mint_confirmed` & `token_burn_confirmed`; offset store di PostgreSQL (`listener_offsets`); ack setelah handler commit; on reconnect kirim resume-from-offset; dedup event-id. (3) `sim/firefly-stub`: Express/Spring stub yang mengimplementasi kedua sisi + endpoint kontrol untuk test (`/control/suppress-next-confirmation`, `/control/disconnect-ws`, `/control/supply`). (4) Pact: consumer test IBM (mint request/response + event schema) publish ke pact-broker di compose; provider verification test yang menjalankan verifikasi terhadap firefly-stub — susun agar file provider-verification bisa diserahkan ke CI Kaleido apa adanya. (5) Anotasi produksi: file `PRODUCTION_DELTAS.md` — mTLS + operator identity, semantik event multi-node QBFT, back-pressure, definisi finality.

**Definition of Done (dua demo):** (a) `demo-replay.sh` — matikan listener, biarkan 3 mint terkonfirmasi di stub, hidupkan lagi → tepat 3 event di-replay sekali masing-masing, saga maju, recon (Asset 4) hijau. (b) `demo-contract-break.sh` — ubah satu field di skema event stub → provider verification Pact **gagal merah** sebelum menyentuh environment mana pun.

**Integrasi:** dikonsumsi saga-lib (Asset 2) · firefly-stub dev → Kaleido-managed FireFly di atas Besu QBFT ×4 (BR-011) produksi · key dari ceremony (Asset 5) sebagai signing identity · pact-broker di-wire ke dua pipeline CI.

---

## Urutan sprint yang disarankan (build cepat, 2 minggu per gelombang)

| Sprint | Isi | Kenapa duluan |
|---|---|---|
| **S0 (3 hari)** | Repo, docker-compose, `contracts/` v0 (OpenAPI + Avro), rtgs-sim & firefly-stub kerangka | Semua asset bergantung pada kontrak & simulator |
| **S1** | Asset 2 (saga) + Asset 6 (firefly-kit) bersama — mereka satu tarikan napas | GATE + idempotency + listener = jantung platform; demo duplicate & replay paling meyakinkan |
| **S2** | Asset 3 (policy) + Asset 4 (recon) | Keduanya konsumen data S1; demo limit-change & break-case |
| **S3** | Asset 1 (portal) di atas semua yang sudah jalan + Asset 5 (ceremonies, paralel — tidak ada dependensi kode) | Portal paling berkesan kalau di belakangnya sudah hidup |

**Aturan demo:** setiap sprint ditutup dengan menjalankan `demo-*.sh` asset terkait di depan tim — skrip demo adalah acceptance test, bukan hiasan.
