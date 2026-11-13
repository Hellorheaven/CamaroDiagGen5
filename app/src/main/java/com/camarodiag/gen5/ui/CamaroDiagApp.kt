package com.camarodiag.gen5.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camarodiag.gen5.domain.model.*
import com.camarodiag.gen5.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class DiagTab(val labelEn: String, val labelFr: String) {
    SETUP("Vehicle", "Véhicule"),
    ADAPTER("Adapter", "Interface"),
    MODULES("Modules", "Modules"),
    LIVE("Live", "Direct"),
    DTCS("DTCs", "Défauts"),
    MILEAGE("Mileage", "Odomètre"),
    GUIDE("Guide", "Guide"),
    SETTINGS("Settings", "Réglages")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamaroDiagApp(viewModel: MainViewModel) {
    val lang by viewModel.language.collectAsState()
    val units by viewModel.unitSystem.collectAsState()
    val vehicle by viewModel.vehicle.collectAsState()
    val adapter by viewModel.adapter.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val connError by viewModel.connectionMessage.collectAsState()
    val modules by viewModel.modules.collectAsState()
    val dtcs by viewModel.dtcs.collectAsState()
    val livePids by viewModel.livePids.collectAsState()
    val isStreaming by viewModel.isStreamingLive.collectAsState()
    val mileageReport by viewModel.mileageReport.collectAsState()
    val savedSessions by viewModel.savedSessions.collectAsState()

    var currentTab by remember { mutableStateOf(DiagTab.SETUP) }
    val isFr = lang == AppLanguage.FR
    fun t(en: String, fr: String) = if (isFr) fr else en

    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPairedDevices()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        } else {
            viewModel.refreshPairedDevices()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                title = {
                    Column {
                        Text(
                            text = t("CamaroDiag Gen5", "CamaroDiag 5G"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = CamaroAmber
                        )
                        Text(
                            text = "${vehicle.modelYear} • ${vehicle.engine.rpo} • ${vehicle.market.code}",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.setLanguage(if (isFr) AppLanguage.EN else AppLanguage.FR)
                    }) {
                        Text(if (isFr) "FR 🇫🇷" else "EN 🇺🇸", color = CamaroBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DiagTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    FilterChip(
                        selected = selected,
                        onClick = { currentTab = tab },
                        label = {
                            Text(
                                text = if (isFr) tab.labelFr else tab.labelEn,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CamaroAmber,
                            selectedLabelColor = BgDark
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
                .padding(14.dp)
        ) {
            when (currentTab) {
                DiagTab.SETUP -> VehicleSetupScreen(vehicle, isFr) { y, e, tr, m ->
                    viewModel.updateVehicle(y, e, tr, m)
                }
                DiagTab.ADAPTER -> AdapterScreen(
                    adapter = adapter,
                    pairedDevices = pairedDevices,
                    errorMsg = connError,
                    isFr = isFr,
                    onRefreshDevices = { viewModel.refreshPairedDevices() },
                    onConnectMac = { viewModel.connectRealBluetooth(it) },
                    onSelectSim = { viewModel.selectSimulatedAdapter(it) }
                )
                DiagTab.MODULES -> ModulesScreen(modules, isFr)
                DiagTab.LIVE -> LiveDataScreen(livePids, units, isStreaming, isFr, { viewModel.toggleLiveStream() }, {
                    viewModel.setUnitSystem(if (units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC)
                })
                DiagTab.DTCS -> DtcScreen(dtcs, isFr, { viewModel.scanDtcs() }, { viewModel.clearDtcs() })
                DiagTab.MILEAGE -> MileageScreen(mileageReport, isFr, { viewModel.runMileageAudit() }, { viewModel.saveCurrentSession() })
                DiagTab.GUIDE -> HardwareGuideScreen(isFr)
                DiagTab.SETTINGS -> SettingsScreen(lang, units, savedSessions, isFr,
                    onLang = { viewModel.setLanguage(it) },
                    onUnits = { viewModel.setUnitSystem(it) },
                    onDeleteSession = { viewModel.deleteSavedSession(it) }
                )
            }
        }
    }
}

@Composable
fun VehicleSetupScreen(
    vehicle: VehicleProfile,
    isFr: Boolean,
    onChange: (Int, CamaroEngine, TransmissionType, MarketRegion) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle(if (isFr) "Configuration Chevrolet Camaro Gen 5 (2010–2015)" else "Chevrolet Camaro Gen 5 Setup (2010–2015)")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isFr) "Année Modèle" else "Model Year", color = TextMuted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        (2010..2015).forEach { yr ->
                            FilterChip(
                                selected = vehicle.modelYear == yr,
                                onClick = { onChange(yr, vehicle.engine, vehicle.transmission, vehicle.market) },
                                label = { Text("$yr") }
                            )
                        }
                    }

                    Text(if (isFr) "Motorisation (Code RPO & Calculateur)" else "Engine (RPO Code & ECM)", color = TextMuted, fontSize = 13.sp)
                    CamaroEngine.entries.filter { vehicle.modelYear in it.years }.forEach { eng ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChange(vehicle.modelYear, eng, vehicle.transmission, vehicle.market) }
                                .background(if (vehicle.engine == eng) SurfaceElevated else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${eng.rpo} — ${if (isFr) eng.nameFr else eng.nameEn}", fontWeight = FontWeight.SemiBold)
                                Text("ECM: ${eng.ecmType}", fontSize = 12.sp, color = CamaroBlue)
                            }
                            if (vehicle.engine == eng) Icon(Icons.Default.CheckCircle, null, tint = CamaroAmber)
                        }
                    }

                    Text(if (isFr) "Transmission" else "Transmission", color = TextMuted, fontSize = 13.sp)
                    TransmissionType.entries.forEach { tr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChange(vehicle.modelYear, vehicle.engine, tr, vehicle.market) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = vehicle.transmission == tr, onClick = { onChange(vehicle.modelYear, vehicle.engine, tr, vehicle.market) })
                            Text(if (isFr) tr.labelFr else tr.labelEn)
                        }
                    }

                    Text(if (isFr) "Marché / Homologation" else "Market Region", color = TextMuted, fontSize = 13.sp)
                    MarketRegion.entries.forEach { mk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChange(vehicle.modelYear, vehicle.engine, vehicle.transmission, mk) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = vehicle.market == mk, onClick = { onChange(vehicle.modelYear, vehicle.engine, vehicle.transmission, mk) })
                            Text(if (isFr) mk.labelFr else mk.labelEn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdapterScreen(
    adapter: AdapterCapabilities,
    pairedDevices: List<Pair<String, String>>,
    errorMsg: String?,
    isFr: Boolean,
    onRefreshDevices: () -> Unit,
    onConnectMac: (String) -> Unit,
    onSelectSim: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle(if (isFr) "Interface OBD-II Bluetooth & Capacités CAN" else "Bluetooth OBD-II Adapter & CAN Probe")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(adapter.deviceName, fontWeight = FontWeight.Bold, color = CamaroAmber, fontSize = 16.sp)
                    Text(if (isFr) adapter.tier.titleFr else adapter.tier.titleEn, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BadgeChip("HS-CAN 500k", true)
                        BadgeChip("SW-CAN GMLAN (Pin 1)", adapter.supportsSwCan)
                        BadgeChip("ISO-TP Long Frames", adapter.supportsLongFrames)
                    }
                    if (adapter.rawProbeLog.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        adapter.rawProbeLog.forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isFr) "Appareils Bluetooth Appairés" else "Paired Bluetooth Devices", fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefreshDevices) { Text(if (isFr) "Actualiser" else "Refresh") }
            }
            if (pairedDevices.isEmpty()) {
                Text(
                    if (isFr) "Aucun périphérique Bluetooth appairé détecté. Appairez votre OBDLink MX+ ou ELM327 dans les paramètres Android."
                    else "No paired Bluetooth devices found. Pair your OBDLink MX+ or ELM327 in Android Bluetooth settings.",
                    color = TextMuted, fontSize = 13.sp
                )
            } else {
                pairedDevices.forEach { (name, mac) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onConnectMac(mac) },
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text(mac, fontSize = 12.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            }
                            Text(if (isFr) "Connecter" else "Connect", color = CamaroBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            errorMsg?.let { Text(it, color = WarnRed, fontSize = 12.sp) }
        }
        item {
            Text(if (isFr) "Profils de Simulation (Test sans véhicule)" else "Simulated Hardware Profiles (Bench Test)", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSelectSim("MX_PLUS") }) { Text("OBDLink MX+") }
                OutlinedButton(onClick = { onSelectSim("ELM_15") }) { Text("ELM327 v1.5") }
                OutlinedButton(onClick = { onSelectSim("CLONE") }) { Text("Clone v2.1") }
            }
        }
    }
}

@Composable
fun ModulesScreen(modules: List<EcuModule>, isFr: Boolean) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionTitle(if (isFr) "Topologie Calculateurs Camaro Gen 5" else "Camaro Gen 5 ECU Module Topology")
        }
        items(modules) { mod ->
            val statusColor = when (mod.status) {
                ModuleStatus.ACCESSIBLE -> OkGreen
                ModuleStatus.REQUIRES_SWCAN -> CamaroAmber
                ModuleStatus.UNSUPPORTED_BY_ADAPTER -> WarnRed
                ModuleStatus.NOT_EQUIPPED -> TextMuted
            }
            val statusLabel = when (mod.status) {
                ModuleStatus.ACCESSIBLE -> if (isFr) "Accessible" else "Accessible"
                ModuleStatus.REQUIRES_SWCAN -> if (isFr) "Requiert SW-CAN (Pin 1)" else "Requires SW-CAN (Pin 1)"
                ModuleStatus.UNSUPPORTED_BY_ADAPTER -> if (isFr) "Bloqué par adaptateur" else "Blocked by adapter"
                ModuleStatus.NOT_EQUIPPED -> if (isFr) "Non équipé" else "Not equipped"
            }
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${mod.shortName} • ${mod.bus.label}", fontWeight = FontWeight.Bold, color = CamaroBlue)
                        Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text(if (isFr) mod.nameFr else mod.nameEn, fontWeight = FontWeight.SemiBold)
                    Text("TX: 0x${mod.txHeader} | RX: 0x${mod.rxHeader} (${mod.bus.pinout})", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted)
                    Text(if (isFr) mod.notesFr else mod.notesEn, fontSize = 12.sp, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun LiveDataScreen(
    pids: List<LivePidValue>,
    units: UnitSystem,
    isStreaming: Boolean,
    isFr: Boolean,
    onToggleStream: () -> Unit,
    onToggleUnits: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(if (isFr) "Données Moteur Temps Réel" else "Live Engine Telemetry")
                Row {
                    TextButton(onClick = onToggleUnits) {
                        Text(if (units == UnitSystem.METRIC) "SI / Metric" else "Imperial", color = CamaroBlue)
                    }
                    TextButton(onClick = onToggleStream) {
                        Text(if (isStreaming) (if (isFr) "Pause" else "Pause") else (if (isFr) "Reprendre" else "Resume"), color = CamaroAmber)
                    }
                }
            }
        }
        items(pids) { pid ->
            val displayVal = if (units == UnitSystem.METRIC) pid.metricValue else pid.imperialValue
            val displayUnit = if (units == UnitSystem.METRIC) pid.metricUnit else pid.imperialUnit
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(if (isFr) pid.labelFr else pid.labelEn, color = TextMuted, fontSize = 13.sp)
                        Text(
                            text = "${String.format("%.1f", displayVal)} $displayUnit",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (pid.history.size > 2) {
                        Spacer(Modifier.height(8.dp))
                        Sparkline(pid.history, pid.minMetric, pid.maxMetric)
                    }
                }
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, minVal: Double, maxVal: Double) {
    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
        val span = (maxVal - minVal).coerceAtLeast(1.0)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val norm = ((v - minVal) / span).coerceIn(0.0, 1.0).toFloat()
            val y = size.height * (1f - norm)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = CamaroAmber, style = Stroke(width = 3f))
    }
}

@Composable
fun DtcScreen(dtcs: List<DiagnosticDtc>, isFr: Boolean, onScan: () -> Unit, onClear: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(if (isFr) "Codes Défaut (DTC)" else "Diagnostic Trouble Codes")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan) { Text(if (isFr) "Scanner" else "Scan") }
                    Button(onClick = onClear, colors = ButtonDefaults.buttonColors(containerColor = WarnRed)) {
                        Text(if (isFr) "Effacer" else "Clear", color = Color.White)
                    }
                }
            }
        }
        if (dtcs.isEmpty()) {
            item {
                Text(if (isFr) "Aucun code défaut actif détecté." else "No active fault codes detected.", color = OkGreen)
            }
        } else {
            items(dtcs) { dtc ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${dtc.code} (${dtc.module})", fontWeight = FontWeight.Bold, color = if (dtc.isPending) CamaroAmber else WarnRed, fontSize = 16.sp)
                            Text(if (isFr) dtc.statusFr else dtc.statusEn, fontSize = 12.sp, color = TextMuted)
                        }
                        Text(if (isFr) dtc.descFr else dtc.descEn)
                    }
                }
            }
        }
    }
}

@Composable
fun MileageScreen(
    report: MileageAuditReport?,
    isFr: Boolean,
    onRefreshAudit: () -> Unit,
    onSaveReport: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(if (isFr) "Audit Cohérence Kilométrique" else "Mileage Consistency Audit")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onRefreshAudit) { Text(if (isFr) "Relire" else "Re-read") }
                    Button(onClick = onSaveReport) { Text(if (isFr) "Sauver" else "Save") }
                }
            }
        }
        report?.let { rep ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = if (rep.hasDiscrepancy) Color(0xFF3B1518) else SurfaceElevated)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isFr) "Synthèse Technique (VIN: ${rep.vin})" else "Engineering Summary (VIN: ${rep.vin})",
                            fontWeight = FontWeight.Bold,
                            color = if (rep.hasDiscrepancy) WarnRed else OkGreen
                        )
                        Text(if (isFr) rep.summaryFr else rep.summaryEn, fontSize = 13.sp)
                    }
                }
            }
            items(rep.readings) { item ->
                val confColor = when (item.confidence) {
                    ConfidenceLevel.HIGH -> OkGreen
                    ConfidenceLevel.MODERATE -> CamaroAmber
                    ConfidenceLevel.UNAVAILABLE -> TextMuted
                }
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isFr) item.moduleNameFr else item.moduleNameEn, fontWeight = FontWeight.Bold)
                            Text(
                                text = item.kmValue?.let { "${String.format("%,.1f", it)} km" } ?: "N/A",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (item.kmValue != null) CamaroBlue else TextMuted
                            )
                        }
                        Text(item.didInfo, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted)
                        Text(if (isFr) item.confidence.labelFr else item.confidence.labelEn, color = confColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (isFr) item.caveatFr else item.caveatEn, fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareGuideScreen(isFr: Boolean) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle(if (isFr) "Guide d'Achat & Compatibilité OBD-II GM" else "OBD-II Hardware & GM GMLAN Buying Guide")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isFr) "1. Recommandé : OBDLink MX+ (Puce STN2255)" else "1. Recommended: OBDLink MX+ (STN2255 Chip)",
                        fontWeight = FontWeight.Bold, color = OkGreen
                    )
                    Text(
                        if (isFr) "Supporte nativement le bus monofilaire GM SW-CAN (Broche 1, 33.3 kbps) sans câble adaptateur, les trames longues ISO-TP et la commutation automatique HS-CAN / SW-CAN. Indispensable pour interroger le BCM, l'IPC et l'Airbag sur Camaro 2010–2015."
                        else "Natively supports GM Single-Wire CAN (Pin 1, 33.3 kbps) without pin-swap adapters, long ISO-TP frames, and automatic HS-CAN/SW-CAN switching. Required to access BCM, IPC, and SDM modules on 2010–2015 Camaro.",
                        fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isFr) "2. Alternative HS-CAN : vLinker MC+ / OBDLink LX" else "2. HS-CAN Alternative: vLinker MC+ / OBDLink LX",
                        fontWeight = FontWeight.Bold, color = CamaroAmber
                    )
                    Text(
                        if (isFr) "Excellente fiabilité sur le bus moteur/boîte/ABS (HS-CAN 500 kbps). Le vLinker MC+ gère également le SW-CAN GMLAN."
                        else "High-throughput reliability on powertrain/ABS HS-CAN (500 kbps). The vLinker MC+ also supports GMLAN SW-CAN.",
                        fontSize = 13.sp
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isFr) "3. Limites des Clones ELM327 v2.1 Génériques" else "3. Limitations of Generic ELM327 v2.1 Clones",
                        fontWeight = FontWeight.Bold, color = WarnRed
                    )
                    Text(
                        if (isFr) "Les boîtiers bleus ou mini ELM327 à bas coût n'ont aucun circuit physique sur la broche 1 (SW-CAN), possèdent des mémoires tampon tronquées et rejettent souvent les commandes d'adressage CAN étendu. L'accès aux modules dépend toujours de l'équipement du véhicule, de la puce de l'interface et du protocole supporté."
                        else "Low-cost blue/mini ELM327 clones lack physical transceivers on Pin 1 (SW-CAN), suffer from truncated RX buffers, and often fail extended CAN header addressing. Module access always depends on vehicle equipment, adapter hardware, and supported protocols.",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    lang: AppLanguage,
    units: UnitSystem,
    sessions: List<com.camarodiag.gen5.data.db.SavedSessionEntity>,
    isFr: Boolean,
    onLang: (AppLanguage) -> Unit,
    onUnits: (UnitSystem) -> Unit,
    onDeleteSession: (com.camarodiag.gen5.data.db.SavedSessionEntity) -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle(if (isFr) "Paramètres & Rapports Enregistrés" else "Settings & Saved Reports")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isFr) "Langue de l'application" else "Application Language", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { l ->
                            FilterChip(selected = lang == l, onClick = { onLang(l) }, label = { Text(l.label) })
                        }
                    }
                    Text(if (isFr) "Système d'unités" else "Measurement Units", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = units == UnitSystem.METRIC, onClick = { onUnits(UnitSystem.METRIC) }, label = { Text("Metric (km, °C, kPa)") })
                        FilterChip(selected = units == UnitSystem.IMPERIAL, onClick = { onUnits(UnitSystem.IMPERIAL) }, label = { Text("Imperial (mi, °F, PSI)") })
                    }
                }
            }
        }
        item {
            Text(if (isFr) "Sessions Sauvegardées (Base Locale Room)" else "Saved Diagnostic Sessions (Local Room DB)", fontWeight = FontWeight.Bold)
        }
        if (sessions.isEmpty()) {
            item {
                Text(if (isFr) "Aucune session enregistrée. Utilisez le bouton 'Sauver' dans l'onglet Odomètre." else "No saved sessions yet. Use 'Save' in the Mileage tab.", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            items(sessions) { s ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.vehicleLabel, fontWeight = FontWeight.Bold, color = CamaroAmber)
                            IconButton(onClick = { onDeleteSession(s) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, tint = WarnRed)
                            }
                        }
                        Text("${sdf.format(Date(s.timestamp))} • VIN: ${s.vin}", fontSize = 11.sp, color = TextMuted)
                        Text("DTCs: ${s.dtcSummary}", fontSize = 12.sp, color = CamaroBlue)
                        Text(s.mileageSummary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
}

@Composable
fun BadgeChip(label: String, active: Boolean) {
    Surface(
        color = if (active) OkGreen.copy(alpha = 0.2f) else WarnRed.copy(alpha = 0.2f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = if (active) OkGreen else WarnRed,
            fontWeight = FontWeight.Bold
        )
    }
}
