# LoRa Scanner — Android App

Monitora reti LoRaWAN (TTN, Helium, Meshtastic) via BLE dal tuo telefono Android.

## ✅ Setup — tutto su GitHub, zero installazioni locali

### 1. Crea il repository

1. Vai su [github.com/new](https://github.com/new)
2. Nome: `lora-scanner` — lascia tutto il resto di default
3. Clicca **Create repository**

### 2. Carica i file

Nel repository appena creato:
- Clicca **Add file → Upload files**
- Trascina **tutti** i file e cartelle dello zip (mantenendo la struttura)
- Clicca **Commit changes**

### 3. Il build parte automaticamente

Vai sulla tab **Actions** → vedrai il workflow `Build LoRa Scanner APK` avviarsi.
Dopo ~4-6 minuti, clicca sul workflow completato → sezione **Artifacts** → scarica l'APK.

---

## 📱 Come installare l'APK sul telefono

1. Sul telefono: **Impostazioni → Sicurezza → Origini sconosciute** (attiva)
2. Scarica l'APK da GitHub Actions
3. Apri il file APK → installa

---

## 🔑 Firma APK (opzionale, per distribuzione)

Per firmare l'APK aggiungi questi secrets in **Settings → Secrets and variables → Actions**:

| Secret | Come ottenerlo |
|--------|---------------|
| `KEYSTORE_BASE64` | `keytool -genkey ...` poi `base64 lora.jks` |
| `KEY_ALIAS` | l'alias scelto con keytool |
| `KEY_PASSWORD` | password della chiave |
| `STORE_PASSWORD` | password del keystore |

```bash
# Genera keystore (una volta sola, dal tuo PC)
keytool -genkey -v -keystore lora.jks -alias lora-scanner \
        -keyalg RSA -keysize 2048 -validity 10000

# Converti in base64 e copia
base64 lora.jks   # Linux/Mac
```

---

## 🏷️ Creare una Release

```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions creerà automaticamente una Release con gli APK allegati.

---

## 🛠️ Struttura del progetto

```
LoRaScanner/
├── .github/workflows/build.yml     ← CI/CD GitHub Actions
├── app/
│   ├── build.gradle                ← dipendenze Android
│   └── src/main/
│       ├── AndroidManifest.xml     ← permessi BLE
│       ├── java/com/lorascanner/app/
│       │   ├── ble/BleManager.kt       ← connessione BLE + Meshtastic
│       │   ├── model/LoRaPacket.kt     ← dati pacchetto LoRa
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── ScanFragment.kt     ← lista pacchetti real-time
│       │   │   ├── ChartFragment.kt    ← grafici RSSI/SNR
│       │   │   ├── ScannerViewModel.kt
│       │   │   ├── DevicePickerDialog.kt
│       │   │   └── adapter/PacketAdapter.kt
│       │   └── utils/ExportUtils.kt    ← export CSV/JSON
│       └── res/                        ← layout, colori, temi
├── build.gradle
├── settings.gradle
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

## 📡 Hardware consigliato

| Dispositivo | Chip LoRa | Prezzo |
|-------------|-----------|--------|
| LilyGO TTGO T-Beam V1.2 (868 MHz) | SX1276 | ~€25–30 |
| Heltec WiFi LoRa 32 V3 (868 MHz) | SX1262 | ~€20–28 |

Flash firmware Meshtastic da [meshtastic.org](https://meshtastic.org/docs/getting-started/).

## 📊 Funzionalità

- **Lista pacchetti** in tempo reale: RSSI, SNR, frequenza, Node ID, hops, payload hex
- **3 grafici live**: RSSI trend, SNR trend, distribuzione frequenze
- **Qualità segnale** con codice colore (Excellent / Good / Fair / Poor)
- **Demo Mode** — funziona senza hardware per testare l'app
- **Export CSV/JSON** — condividi i log via qualsiasi app Android
- **Android 16 (API 36)** — supporto predictive back gesture

## Licenza

MIT
