# CamaroDiag – intégration GM et VXDIAG (v1.4.0)

## Matériel visé

| Interface | Liaison | État de l'intégration Android |
|---|---|---|
| ELM327 | Bluetooth | Transport existant ; commandes ELM classiques uniquement |
| ELM327 | USB série | Transport existant via usb-serial-for-android |
| ELM327 | Wi-Fi TCP | Transport existant ; hôte/port configurables |
| VXDIAG VCX SE | USB-C | Profil matériel déclaré ; SDK/pilote Android constructeur requis |
| VXDIAG VCX SE | Wi-Fi | Profil matériel déclaré ; SDK/protocole constructeur requis |
| VXDIAG VCX NANO | USB | Variante matérielle distincte ; SDK/pilote Android constructeur requis |
| VXDIAG VCX NANO | Wi-Fi | Variante matérielle distincte ; SDK/protocole constructeur requis |

Le NANO USB et le NANO Wi-Fi sont traités comme deux variantes matérielles, pas comme deux liens garantis sur un seul boîtier. Les profils VXDIAG sont prêts à être sélectionnés, mais aucune implémentation J2534 Android propriétaire n'est incluse. `VxdiagProvider` est le point d'injection réservé au SDK redistribuable fourni par VXDIAG ou à une API documentée.

## GM Gen 5 et Gen 6

Les profils véhicule restent séparés par génération, année, motorisation, transmission et marché. Le balayage doit identifier les calculateurs par réponses réellement reçues ; aucune adresse BCM/IPC ni DID kilométrique ne doit être déduite uniquement de l'année ou de la génération.

### Audit kilométrique

Sources prévues : BCM, IPC, ECM et historique local. Une lecture n'est considérée indépendante que si l'identité ECU, la réponse brute, l'adresse de réponse et le profil véhicule sont vérifiés. Une divergence est un indice à investiguer (remplacement de module, reprogrammation, configuration, réparation), pas une preuve automatique de fraude. Ne jamais substituer un PID « distance depuis effacement » à un kilométrage total.

### Pilotage BCM et démarrage

Le catalogue de commandes reste fermé tant qu'une routine OEM/documentée n'est pas validée pour le VIN, l'année, le BCM et la version logicielle. Le démarrage à distance n'est pas une commande CAN générique : sur le système OEM documenté du Camaro 2012, la demande passe par le transmetteur RKE ; le BCM contrôle notamment capot fermé, portes verrouillées, feux de détresse éteints et antivol, puis transmet une demande à l'ECM. L'ECM applique ses propres conditions (P, vitesse nulle, défauts et autres paramètres). `RemoteStartSafetyGate` ne fait qu'évaluer les conditions connues ; il ne démarre pas le moteur et ne contourne jamais l'antidémarrage.

## Compatibilité Android

`minSdk = 29` (Android 10) et `compileSdk/targetSdk = 36` (Android 16). USB Host, réseau TCP et permissions Bluetooth doivent être testés sur appareil réel, notamment les variantes Android 12+ et 14+.

## Pour terminer le vrai pilote VXDIAG

Obtenir auprès de VXDIAG le SDK Android/NDK ou une spécification d'API officiellement utilisable, les droits de redistribution, les IDs USB, les versions firmware testées et la documentation J2534/PDU. Sans ces éléments, la couche ne doit pas prétendre exécuter des transactions J2534.

## Standard OBD-II coverage added in CamaroDiag 1.5.0

`Obd2FeatureEngine` adds a reusable read-only SAE J1979 scanner over the existing ELM command channel. It discovers supported Mode 01 PIDs and reads common live data, confirmed/pending/permanent powertrain DTCs (Modes 03/07/0A), freeze-frame data (Mode 02), VIN/calibration/ECU-name records (Mode 09), readiness (Mode 01 PID 01), module voltage and raw Mode 06 monitor results. The engine is connected to the existing Bluetooth/USB/Wi-Fi manager; it is not a simulation.

The result is limited to what the connected adapter and ECU actually answer. Generic ELM access normally exposes the legislated powertrain OBD services, not all GM body/chassis modules. GM BCM/IPC/TCM/EBCM/SDM discovery, mileage DID reads, actuator tests, coding and remote start remain disabled until exact VIN/year/calibration-specific GM service definitions and safe procedures are verified. VXDIAG remains behind the provider boundary pending its licensed Android SDK/API.
