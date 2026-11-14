# CamaroDiag

Application Android native Kotlin / Jetpack Compose pour diagnostic Chevrolet Camaro Gen 5 (2010–2015) et Gen 6 (2016–2024).

## Architecture modulaire

- `:app` — interface Compose, navigation et ViewModel.
- `:core` — modèles véhicule, générations, motorisations et types de données partagés.
- `:diagnostic` — décodage OBD-II/UDS, DTC, profils ECU et audit kilométrique.
- `:communication` — transport Bluetooth SPP, USB série ELM/STN et Wi-Fi TCP.
- `:control` — catalogue des fonctions carrosserie et garde-fous de validation des commandes GM.
- `:storage` — persistance Room des sessions de diagnostic.

## Compatibilité Android

- Android 10 à Android 16 (API 29 à 36), `minSdk = 29`, `compileSdk = 36`, `targetSdk = 36`.
- Interface adaptative Compose.
- Les permissions Bluetooth sont différenciées avant/après Android 12.

## Interfaces OBD

- ELM327 Bluetooth SPP.
- ELM327 USB série via usb-serial-for-android (permission USB Android requise).
- ELM327 Wi-Fi via TCP (adresse IP et port configurables; ports usuels 35000 ou 23 selon l'adaptateur).
- VXDIAG/J2534 : non déclaré opérationnel sur Android. Il faut le SDK Android officiel ou un protocole documenté propre au modèle exact; aucune compatibilité n'est simulée.

## Pilotage carrosserie

L'écran Pilotage présente les fonctions candidates (vitres, toit ouvrant, verrouillage, éclairage, klaxon, rétroviseurs, coffre). Elles restent verrouillées par défaut. Les commandes GM dépendent du VIN, du BCM, du marché, de la finition, de la calibration et des sessions de sécurité. Ne pas envoyer de trames arbitraires : une fonction ne doit être activée qu'après validation sur un véhicule réel et documentation du DID / service / paramètres.

## Build local

Ouvrir le projet dans Android Studio avec JDK 17 et Android SDK 36, synchroniser Gradle, puis lancer `:app:assembleDebug`.

## GitHub Actions

Le workflow `.github/workflows/release-apk.yml` est conservé et mis à jour. Il compile les APK debug et release, puis publie une GitHub Release et ses APKs lors d'un push sur `main` ou d'un lancement manuel. Les secrets de signature existants doivent être configurés dans GitHub Actions.

## Limites à valider sur véhicule

- La disponibilité des PIDs OBD et l'adressage GM dépendent de l'année, du moteur, du marché et du calculateur.
- L'ELM327 générique ne garantit pas l'accès à tous les réseaux GM (notamment SW-CAN/GMLAN).
- La lecture intermodules du kilométrage et les commandes de carrosserie nécessitent des identifiants et procédures confirmés pour chaque famille ECU.

## Mileage audit (v1.3.0)
- Added the dedicated `:mileageaudit` module for source-aware mileage comparison.
- BCM and IPC are explicitly displayed as unavailable until a VIN/year/calibration-specific GM read profile is verified. The app does not guess ECU addresses or DIDs.
- ECM PID A6/D3 values are treated as powertrain data, not as independent BCM/IPC evidence.
- A discrepancy is flagged only when at least two verified independent sources are available; a mismatch is an investigation lead, not proof of tampering.
- Added unit tests for independent-source discrepancy, non-independent ECM PIDs, and missing data.

## Remote start safety
The control catalog now includes a remote-start entry, but it is intentionally non-executable through generic ELM/OBD commands. Use the vehicle's OEM remote-start system only. No immobilizer, theft-deterrent, transmission interlock, or engine-start security bypass is implemented.

## v1.4.0 – VXDIAG and GM integration foundation

- Added VCX SE USB-C/Wi-Fi and VCX NANO USB/Wi-Fi hardware profiles.
- Added the `VxdiagProvider` integration boundary for a licensed/documented Android vendor SDK.
- Added fail-closed OEM remote-start safety assessment; no direct crank, immobilizer bypass or undocumented CAN command is sent.
- Kept Gen 5 and Gen 6 profiles separate and mileage audit read-only.
- Android API range: minSdk 29 (Android 10), targetSdk 36 (Android 16).
- See `docs_GM_SUPPORT.md` for limitations and the required VXDIAG SDK hand-off.

**Important:** VXDIAG profiles are not equivalent to a working J2534 driver. Actual VCX communication remains disabled until a compatible vendor SDK/API is supplied and tested on hardware.

## v1.5.0 – expanded standards-based OBD-II diagnostics

The `:diagnostic` module now contains `Obd2FeatureEngine`, a read-only SAE J1979 engine wired to the existing Bluetooth/USB/Wi-Fi ELM command manager through `createObd2FeatureEngine()`.

Implemented requests include adapter initialization/protocol/voltage, supported-PID bitmap discovery, common Mode 01 live values (RPM, speed, coolant, intake temperature, MAF, throttle, fuel pressure/level, barometric pressure, module voltage, oil temperature, fuel rate and supported monitor values), Mode 03 confirmed DTCs, Mode 07 pending DTCs, Mode 0A permanent DTCs, Mode 02 freeze-frame values, Mode 09 VIN/calibration/ECU-name records, Mode 01 readiness status and raw Mode 06 monitor-test response.

All queries are read-only. Unsupported PIDs are skipped; raw responses are retained in the scan report. Mode 06 is deliberately kept raw because test-ID scaling and limits are ECU/calibration-specific. Mode 09 multi-frame responses and Mode 02 freeze-frame decoding must be validated against real adapter captures before being treated as complete. This engine does not claim access to GM enhanced modules (BCM/IPC/ABS/SDM/TCM), nor does it send actuator, coding, security-access or engine-start commands. Those require verified vehicle-specific GM service definitions and a capable CAN interface.

The full scan is exposed as `BluetoothObdManager.createObd2FeatureEngine().fullReadOnlyScan()` after a real adapter connection. Demo mode must not be used to represent a vehicle scan.
