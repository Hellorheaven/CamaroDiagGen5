package com.camarodiag.gen5.data.obd

import com.camarodiag.gen5.domain.model.*
import kotlin.math.abs

object CamaroUdsProtocol {

    fun evaluateAdapterProbe(
        deviceName: String,
        macAddress: String,
        atzResp: String,
        stiResp: String,
        atalResp: String,
        stp61Resp: String
    ): AdapterCapabilities {
        val logs = listOf(
            "> ATZ  -> $atzResp",
            "> STI  -> $stiResp",
            "> ATAL -> $atalResp",
            "> STP 61 -> $stp61Resp"
        )

        val isStn = stiResp.contains("STN", ignoreCase = true) ||
            atzResp.contains("OBDLink", ignoreCase = true) ||
            deviceName.contains("OBDLink", ignoreCase = true) ||
            deviceName.contains("vLinker", ignoreCase = true)

        val supportsSwCan = isStn && (stp61Resp.contains("OK", ignoreCase = true) || stiResp.contains("STN2255") || stiResp.contains("STN1170"))
        val supportsLong = atalResp.contains("OK", ignoreCase = true)

        val tier = when {
            isStn && supportsSwCan -> AdapterTier.PRO_STN
            supportsLong && !atzResp.contains("v2.1", ignoreCase = true) -> AdapterTier.GENUINE_ELM
            else -> AdapterTier.CLONE_ELM
        }

        return AdapterCapabilities(
            deviceName = deviceName,
            macAddress = macAddress,
            tier = tier,
            firmwareId = if (stiResp.isNotBlank() && !stiResp.contains("?")) stiResp else atzResp,
            supportsLongFrames = supportsLong,
            supportsSwCan = supportsSwCan,
            supportsCanHeaders = true,
            rawProbeLog = logs
        )
    }

    fun buildTopology(vehicle: VehicleProfile, adapter: AdapterCapabilities): List<EcuModule> {
        val swCanStatus = if (adapter.supportsSwCan) ModuleStatus.ACCESSIBLE else ModuleStatus.REQUIRES_SWCAN
        val tcmStatus = if (vehicle.transmission == TransmissionType.AUTOMATIC) ModuleStatus.ACCESSIBLE else ModuleStatus.NOT_EQUIPPED

        return listOf(
            EcuModule(
                id = "ECM",
                shortName = "ECM",
                nameEn = "Engine Control Module (${vehicle.engine.ecmType})",
                nameFr = "Calculateur Moteur (${vehicle.engine.ecmType})",
                txHeader = "7E0",
                rxHeader = "7E8",
                bus = CanBusType.HS_CAN,
                status = ModuleStatus.ACCESSIBLE,
                dtcCount = 1,
                notesEn = "Standard OBD-II + GM UDS (Mode 22). Volatile distance counters.",
                notesFr = "OBD-II standard + GM UDS (Mode 22). Compteurs distance volatils."
            ),
            EcuModule(
                id = "TCM",
                shortName = "TCM",
                nameEn = "Transmission Control Module (T43)",
                nameFr = "Calculateur Boîte Auto (T43)",
                txHeader = "7E2",
                rxHeader = "7EA",
                bus = CanBusType.HS_CAN,
                status = tcmStatus,
                dtcCount = 0,
                notesEn = if (tcmStatus == ModuleStatus.NOT_EQUIPPED) "Not fitted on Manual transmission models." else "Accessible over HS-CAN (7E2/7EA).",
                notesFr = if (tcmStatus == ModuleStatus.NOT_EQUIPPED) "Absent sur boîte manuelle." else "Accessible sur HS-CAN (7E2/7EA)."
            ),
            EcuModule(
                id = "EBCM",
                shortName = "ABS / EBCM",
                nameEn = "Electronic Brake Control Module",
                nameFr = "Calculateur ABS / StabiliTrak",
                txHeader = "7E1 / 243",
                rxHeader = "7E9 / 643",
                bus = CanBusType.HS_CAN,
                status = if (adapter.tier == AdapterTier.CLONE_ELM) ModuleStatus.UNSUPPORTED_BY_ADAPTER else ModuleStatus.ACCESSIBLE,
                dtcCount = 0,
                notesEn = "Requires CAN header addressing (ATSH) and ISO-TP flow control.",
                notesFr = "Requiert adressage d'en-tête CAN (ATSH) et contrôle de flux ISO-TP."
            ),
            EcuModule(
                id = "BCM",
                shortName = "BCM",
                nameEn = "Body Control Module (Primary Odometer)",
                nameFr = "Calculateur Habitacle (Odomètre Principal)",
                txHeader = "241",
                rxHeader = "641",
                bus = CanBusType.SW_CAN,
                status = swCanStatus,
                dtcCount = 1,
                notesEn = "Authoritative NVM odometer store on GMLAN SW-CAN (Pin 1, 33.3 kbps).",
                notesFr = "Stockage odomètre NVM de référence sur SW-CAN GMLAN (Broche 1, 33.3 kbps)."
            ),
            EcuModule(
                id = "IPC",
                shortName = "IPC",
                nameEn = "Instrument Panel Cluster",
                nameFr = "Combiné d'Instruments (Compteur)",
                txHeader = "24C",
                rxHeader = "64C",
                bus = CanBusType.SW_CAN,
                status = swCanStatus,
                dtcCount = 0,
                notesEn = "Displays cluster mileage synced from BCM over GMLAN.",
                notesFr = "Affiche le kilométrage synchronisé depuis le BCM via GMLAN."
            ),
            EcuModule(
                id = "SDM",
                shortName = "SDM (Airbag)",
                nameEn = "Sensing & Diagnostic Module",
                nameFr = "Calculateur Airbag / Prétensionneurs",
                txHeader = "247",
                rxHeader = "647",
                bus = CanBusType.SW_CAN,
                status = swCanStatus,
                dtcCount = 0,
                notesEn = "Stores crash event & ignition cycle counters on SW-CAN.",
                notesFr = "Enregistre les événements choc et cycles d'allumage sur SW-CAN."
            )
        )
    }

    fun parseGmMileageHex(hexResponse: String, resolutionKmPerBit: Double = 1.0 / 64.0): Double? {
        val cleaned = hexResponse.replace(" ", "").uppercase()
        val idx = cleaned.indexOf("62")
        if (idx == -1 || cleaned.length < idx + 12) return null
        val payloadHex = cleaned.substring(idx + 6)
        return try {
            val rawTicks = payloadHex.take(8).toLong(16)
            rawTicks * resolutionKmPerBit
        } catch (_: Exception) {
            null
        }
    }

    fun evaluateMileageConsistency(
        vin: String,
        readings: List<MileageSourceReading>
    ): MileageAuditReport {
        val highConfidenceValues = readings
            .filter { it.confidence == ConfidenceLevel.HIGH && it.kmValue != null }
            .mapNotNull { it.kmValue }

        val maxDelta = if (highConfidenceValues.size >= 2) {
            (highConfidenceValues.max() - highConfidenceValues.min())
        } else {
            0.0
        }

        val hasDiscrepancy = maxDelta > 15.0

        val summaryEn = when {
            highConfidenceValues.isEmpty() ->
                "Insufficient primary NVM module access. Connect an SW-CAN capable adapter (e.g., OBDLink MX+) to read BCM and IPC odometer registers."
            highConfidenceValues.size == 1 ->
                "Only one authoritative NVM source (${String.format("%.0f", highConfidenceValues.first())} km) was accessible. Cross-module comparison requires at least two NVM sources."
            hasDiscrepancy ->
                "Discrepancy of ${String.format("%.1f", maxDelta)} km detected between primary NVM modules. This may indicate a replaced IPC/BCM module without mileage programming or odometer alteration."
            else ->
                "Primary accessible NVM modules agree within ${String.format("%.1f", maxDelta)} km (normal synchronization tolerance). Note: this confirms consistency across read modules, not an absolute tamper guarantee."
        }

        val summaryFr = when {
            highConfidenceValues.isEmpty() ->
                "Accès insuffisant aux modules NVM principaux. Connectez une interface compatible SW-CAN (ex. OBDLink MX+) pour lire le BCM et l'IPC."
            highConfidenceValues.size == 1 ->
                "Une seule source NVM fiable (${String.format("%.0f", highConfidenceValues.first())} km) a pu être lue. La comparaison croisée nécessite au moins deux modules NVM."
            hasDiscrepancy ->
                "Écart de ${String.format("%.1f", maxDelta)} km détecté entre les modules NVM principaux. Peut indiquer un remplacement de compteur/BCM non reprogrammé ou une incohérence kilométrique."
            else ->
                "Les modules NVM principaux concordent à ${String.format("%.1f", maxDelta)} km près (tolérance normale). Note : atteste de la cohérence entre modules lus, sans garantie absolue d'historique."
        }

        return MileageAuditReport(
            timestamp = System.currentTimeMillis(),
            vin = vin,
            readings = readings,
            maxDeltaKm = abs(maxDelta),
            hasDiscrepancy = hasDiscrepancy,
            summaryEn = summaryEn,
            summaryFr = summaryFr
        )
    }
}
