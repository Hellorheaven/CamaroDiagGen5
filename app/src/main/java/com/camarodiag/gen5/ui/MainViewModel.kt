package com.camarodiag.gen5.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.camarodiag.gen5.data.db.DiagnosticDatabase
import com.camarodiag.gen5.data.db.SavedSessionEntity
import com.camarodiag.gen5.data.obd.BluetoothObdManager
import com.camarodiag.gen5.data.obd.CamaroUdsProtocol
import com.camarodiag.gen5.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val obdManager = BluetoothObdManager(application)
    private val sessionDao = DiagnosticDatabase.getInstance(application).sessionDao()

    private val _language = MutableStateFlow(AppLanguage.FR)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _unitSystem = MutableStateFlow(UnitSystem.METRIC)
    val unitSystem: StateFlow<UnitSystem> = _unitSystem.asStateFlow()

    private val _vehicle = MutableStateFlow(VehicleProfile())
    val vehicle: StateFlow<VehicleProfile> = _vehicle.asStateFlow()

    private val _adapter = MutableStateFlow(AdapterCapabilities())
    val adapter: StateFlow<AdapterCapabilities> = _adapter.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pairedDevices: StateFlow<List<Pair<String, String>>> = _pairedDevices.asStateFlow()

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _modules = MutableStateFlow<List<EcuModule>>(emptyList())
    val modules: StateFlow<List<EcuModule>> = _modules.asStateFlow()

    private val _dtcs = MutableStateFlow<List<DiagnosticDtc>>(emptyList())
    val dtcs: StateFlow<List<DiagnosticDtc>> = _dtcs.asStateFlow()

    private val _livePids = MutableStateFlow<List<LivePidValue>>(emptyList())
    val livePids: StateFlow<List<LivePidValue>> = _livePids.asStateFlow()

    private val _isStreamingLive = MutableStateFlow(true)
    val isStreamingLive: StateFlow<Boolean> = _isStreamingLive.asStateFlow()

    private val _mileageReport = MutableStateFlow<MileageAuditReport?>(null)
    val mileageReport: StateFlow<MileageAuditReport?> = _mileageReport.asStateFlow()

    val savedSessions: StateFlow<List<SavedSessionEntity>> = sessionDao.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshTopologyAndDefaults()
        startLiveTelemetryLoop()
    }

    fun setLanguage(lang: AppLanguage) { _language.value = lang }
    fun setUnitSystem(units: UnitSystem) { _unitSystem.value = units }

    fun updateVehicle(
        year: Int = _vehicle.value.modelYear,
        engine: CamaroEngine = _vehicle.value.engine,
        transmission: TransmissionType = _vehicle.value.transmission,
        market: MarketRegion = _vehicle.value.market
    ) {
        val safeEngine = if (year in engine.years) engine else CamaroEngine.entries.first { year in it.years }
        val vinPrefix = if (market == MarketRegion.EU_ECE) "2G1FK1EJ3" else "2G1FA1E30"
        val yearCode = when (year) {
            2010 -> "A"; 2011 -> "B"; 2012 -> "C"; 2013 -> "D"; 2014 -> "E"; else -> "F"
        }
        _vehicle.value = VehicleProfile(
            modelYear = year,
            engine = safeEngine,
            transmission = transmission,
            market = market,
            vin = "$vinPrefix${yearCode}9142857"
        )
        refreshTopologyAndDefaults()
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = obdManager.getPairedDevices()
    }

    fun selectSimulatedAdapter(preset: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val caps = obdManager.activateSimulatedAdapter(preset)
            _adapter.value = caps
            _connectionMessage.value = null
            refreshTopologyAndDefaults()
            _isBusy.value = false
        }
    }

    fun connectRealBluetooth(macAddress: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _connectionMessage.value = null
            val res = obdManager.connectAndProbe(macAddress)
            res.onSuccess { caps ->
                _adapter.value = caps
                refreshTopologyAndDefaults()
            }.onFailure { err ->
                _connectionMessage.value = err.localizedMessage ?: "Bluetooth SPP connection failed"
            }
            _isBusy.value = false
        }
    }

    fun toggleLiveStream() {
        _isStreamingLive.value = !_isStreamingLive.value
    }

    fun scanDtcs() {
        viewModelScope.launch {
            _isBusy.value = true
            delay(400)
            val list = mutableListOf(
                DiagnosticDtc(
                    code = "P0171",
                    module = "ECM",
                    statusEn = "Confirmed",
                    statusFr = "Confirmé",
                    descEn = "System Too Lean (Bank 1)",
                    descFr = "Mélange trop pauvre (Banc 1)"
                )
            )
            if (_vehicle.value.engine == CamaroEngine.LFX) {
                list.add(
                    DiagnosticDtc(
                        code = "P0014",
                        module = "ECM",
                        statusEn = "Pending",
                        statusFr = "En attente",
                        descEn = "Exhaust Camshaft Position Timing Over-Advanced (Bank 1)",
                        descFr = "Calage arbre à cames échappement trop avancé (Banc 1)",
                        isPending = true
                    )
                )
            }
            if (_adapter.value.supportsSwCan) {
                list.add(
                    DiagnosticDtc(
                        code = "B2575",
                        module = "BCM",
                        statusEn = "History",
                        statusFr = "Historique",
                        descEn = "Headlamp Control Circuit (EU/ECE DRL Configuration)",
                        descFr = "Circuit commande projecteurs (Configuration feux diurnes EU/ECE)"
                    )
                )
            }
            _dtcs.value = list
            _isBusy.value = false
        }
    }

    fun clearDtcs() {
        viewModelScope.launch {
            _isBusy.value = true
            if (!obdManager.isDemoMode) {
                obdManager.sendAtCommand("04")
            }
            delay(300)
            _dtcs.value = emptyList()
            _isBusy.value = false
        }
    }

    fun runMileageAudit() {
        viewModelScope.launch {
            _isBusy.value = true
            delay(450)
            val baseKm = 118420.0
            val swCanOk = _adapter.value.supportsSwCan
            val absOk = _adapter.value.tier != AdapterTier.CLONE_ELM
            val hasTcm = _vehicle.value.transmission == TransmissionType.AUTOMATIC

            val readings = listOf(
                MileageSourceReading(
                    moduleId = "BCM",
                    moduleNameEn = "Body Control Module (NVM Master)",
                    moduleNameFr = "Calculateur Habitacle BCM (Maître NVM)",
                    didInfo = "SW-CAN 241/641 • DID 0xF190 / 0x0102",
                    kmValue = if (swCanOk) baseKm else null,
                    confidence = if (swCanOk) ConfidenceLevel.HIGH else ConfidenceLevel.UNAVAILABLE,
                    caveatEn = if (swCanOk) "Primary non-volatile vehicle odometer register." else "Blocked: Requires SW-CAN (Pin 1) adapter such as OBDLink MX+.",
                    caveatFr = if (swCanOk) "Registre kilométrique principal non volatil du véhicule." else "Bloqué : Requiert une interface SW-CAN (Broche 1) type OBDLink MX+."
                ),
                MileageSourceReading(
                    moduleId = "IPC",
                    moduleNameEn = "Instrument Panel Cluster",
                    moduleNameFr = "Combiné d'Instruments (IPC)",
                    didInfo = "SW-CAN 24C/64C • DID 0xDD01",
                    kmValue = if (swCanOk) baseKm + 1.5 else null,
                    confidence = if (swCanOk) ConfidenceLevel.HIGH else ConfidenceLevel.UNAVAILABLE,
                    caveatEn = if (swCanOk) "Matches cluster display within normal 1–2 km sync window." else "Blocked: GMLAN 33.3 kbps single-wire bus required.",
                    caveatFr = if (swCanOk) "Correspond à l'affichage compteur (tolérance synchro 1–2 km)." else "Bloqué : Bus monofilaire GMLAN 33.3 kbps requis."
                ),
                MileageSourceReading(
                    moduleId = "EBCM",
                    moduleNameEn = "ABS / Brake Control Module",
                    moduleNameFr = "Calculateur ABS / EBCM",
                    didInfo = "HS-CAN 7E1/7E9 • Snapshot DID",
                    kmValue = if (absOk) baseKm - 2.0 else null,
                    confidence = if (absOk) ConfidenceLevel.HIGH else ConfidenceLevel.UNAVAILABLE,
                    caveatEn = if (absOk) "Secondary HS-CAN odometer snapshot." else "Adapter clone rejected custom CAN header addressing.",
                    caveatFr = if (absOk) "Cliché kilométrique secondaire sur bus HS-CAN." else "Clone ELM327 incapable d'adresser les en-têtes CAN étendus."
                ),
                MileageSourceReading(
                    moduleId = "TCM",
                    moduleNameEn = "Transmission Control Module",
                    moduleNameFr = "Calculateur Boîte Automatique (TCM)",
                    didInfo = "HS-CAN 7E2/7EA • Lifetime Distance",
                    kmValue = if (hasTcm) baseKm - 4.0 else null,
                    confidence = if (hasTcm) ConfidenceLevel.HIGH else ConfidenceLevel.UNAVAILABLE,
                    caveatEn = if (hasTcm) "6L50/6L80 internal shift-adapt distance." else "Vehicle configured with Manual transmission (no TCM).",
                    caveatFr = if (hasTcm) "Distance interne d'adaptation boîte 6L50/6L80." else "Véhicule configuré en boîte manuelle (pas de TCM)."
                ),
                MileageSourceReading(
                    moduleId = "ECM",
                    moduleNameEn = "Engine Control Module (OBD-II PID 0x31)",
                    moduleNameFr = "Calculateur Moteur (PID OBD-II 0x31)",
                    didInfo = "HS-CAN 7E0/7E8 • Mode 01 PID 31",
                    kmValue = 14280.0,
                    confidence = ConfidenceLevel.MODERATE,
                    caveatEn = "VOLATILE: Distance since DTCs cleared or battery disconnect. Never equals total vehicle mileage.",
                    caveatFr = "VOLATILE : Distance depuis effacement défauts ou débranchement batterie. Ne reflète jamais le kilométrage total."
                )
            )

            _mileageReport.value = CamaroUdsProtocol.evaluateMileageConsistency(_vehicle.value.vin, readings)
            _isBusy.value = false
        }
    }

    fun saveCurrentSession() {
        viewModelScope.launch {
            val v = _vehicle.value
            val report = _mileageReport.value
            val entity = SavedSessionEntity(
                timestamp = System.currentTimeMillis(),
                vehicleLabel = "${v.modelYear} Camaro ${v.engine.rpo} (${v.market.code})",
                vin = v.vin,
                adapterName = _adapter.value.deviceName,
                dtcSummary = if (_dtcs.value.isEmpty()) "0 DTC" else _dtcs.value.joinToString(", ") { it.code },
                mileageSummary = report?.let {
                    if (_language.value == AppLanguage.FR) it.summaryFr else it.summaryEn
                } ?: "Audit non exécuté",
                hasMileageDiscrepancy = report?.hasDiscrepancy ?: false,
                maxDeltaKm = report?.maxDeltaKm ?: 0.0
            )
            sessionDao.insertSession(entity)
        }
    }

    fun deleteSavedSession(session: SavedSessionEntity) {
        viewModelScope.launch {
            sessionDao.deleteSession(session)
        }
    }

    private fun refreshTopologyAndDefaults() {
        _modules.value = CamaroUdsProtocol.buildTopology(_vehicle.value, _adapter.value)
        viewModelScope.launch {
            scanDtcs()
            runMileageAudit()
        }
    }

    private fun startLiveTelemetryLoop() {
        viewModelScope.launch {
            var tick = 0.0
            val rpmHist = ArrayDeque<Double>()
            val coolantHist = ArrayDeque<Double>()
            val oilHist = ArrayDeque<Double>()
            val stftHist = ArrayDeque<Double>()

            while (true) {
                if (_isStreamingLive.value) {
                    tick += 0.35
                    val rpm = (740.0 + sin(tick) * 45.0 + Random.nextDouble(-12.0, 12.0)).coerceAtLeast(620.0)
                    val coolantC = (91.0 + sin(tick * 0.2) * 2.5).coerceIn(85.0, 104.0)
                    val oilKpa = (245.0 + sin(tick * 0.8) * 18.0).coerceIn(180.0, 380.0)
                    val stft = (1.6 + sin(tick * 0.6) * 2.1).coerceIn(-6.0, 6.0)
                    val voltage = 14.1 + sin(tick * 0.3) * 0.15

                    fun push(q: ArrayDeque<Double>, v: Double): List<Double> {
                        if (q.size >= 24) q.removeFirst()
                        q.addLast(v)
                        return q.toList()
                    }

                    _livePids.value = listOf(
                        LivePidValue("RPM", "Engine Speed", "Régime Moteur", rpm, "tr/min", rpm, "RPM", 600.0, 7000.0, push(rpmHist, rpm)),
                        LivePidValue("ECT", "Coolant Temp", "Temp. Liquide Refroid.", coolantC, "°C", coolantC * 1.8 + 32.0, "°F", 40.0, 120.0, push(coolantHist, coolantC)),
                        LivePidValue("OIL", "Engine Oil Pressure", "Pression d'Huile", oilKpa, "kPa", oilKpa * 0.145038, "PSI", 100.0, 500.0, push(oilHist, oilKpa)),
                        LivePidValue("STFT", "Short Term Fuel Trim B1", "Correction Carburant CT B1", stft, "%", stft, "%", -15.0, 15.0, push(stftHist, stft)),
                        LivePidValue("BAT", "Control Module Voltage", "Tension Batterie / Alt.", voltage, "V", voltage, "V", 11.5, 15.2, emptyList())
                    )
                }
                delay(450)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        obdManager.disconnect()
    }
}
