package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.data.TqmRepository
import com.example.model.CloudBackupSnapshot
import com.example.model.IbagueGeo
import com.example.model.OrderPriority
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.PushNotificationLog
import com.example.model.ServiceOrder
import com.example.model.SupportTicket
import com.example.model.TechSpecialty
import com.example.model.TechStatus
import com.example.model.Technician
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.util.NotificationDispatcher
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class TqmViewModel(
    private val repository: TqmRepository,
    private val prefs: PreferencesManager,
    private val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String
) : ViewModel() {

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing profile", root)
            emit(UiState.Error(root.message ?: "Error al cargar perfil"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val techniciansState: StateFlow<UiState<List<Technician>>> = repository.observeTechnicians(currentUserId)
        .map<List<Technician>, UiState<List<Technician>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing technicians", root)
            emit(UiState.Error(root.message ?: "Error al sincronizar técnicos"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val ordersState: StateFlow<UiState<List<ServiceOrder>>> = repository.observeOrders(currentUserId)
        .map<List<ServiceOrder>, UiState<List<ServiceOrder>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing orders", root)
            emit(UiState.Error(root.message ?: "Error al sincronizar órdenes"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<PushNotificationLog>>> = repository.observeNotifications(currentUserId)
        .map<List<PushNotificationLog>, UiState<List<PushNotificationLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Error al cargar notificaciones"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val ticketsState: StateFlow<UiState<List<SupportTicket>>> = repository.observeSupportTickets(currentUserId)
        .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Error al cargar soporte"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val backupsState: StateFlow<UiState<List<CloudBackupSnapshot>>> = repository.observeBackups(currentUserId)
        .map<List<CloudBackupSnapshot>, UiState<List<CloudBackupSnapshot>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Error al cargar respaldos"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val localThemeMode = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "DARK")

    val compactDensity = prefs.compactDensityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _twoFactorVerified = MutableStateFlow(false)
    val twoFactorVerified: StateFlow<Boolean> = _twoFactorVerified.asStateFlow()

    private val _generatedOtp = MutableStateFlow<String?>(null)
    val generatedOtp: StateFlow<String?> = _generatedOtp.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _e2eFingerprint = MutableStateFlow("")
    val e2eFingerprint: StateFlow<String> = _e2eFingerprint.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialEmail)
            val keyHex = prefs.getOrCreateWorkspaceKeyHex()
            _e2eFingerprint.value = PreferencesManager.sha256Hex(keyHex).take(12).uppercase()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun requestTwoFactorOtp(context: Context) {
        val code = PreferencesManager.generateOneTimeCode()
        _generatedOtp.value = code
        NotificationDispatcher.sendTwoFactorCodeNotification(context, code)
    }

    fun verifyTwoFactorCode(inputCode: String, profile: UserProfile?): Boolean {
        val clean = inputCode.trim()
        if (clean.length != 6) return false
        val otp = _generatedOtp.value
        val storedPinHash = profile?.twoFactorPinHash.orEmpty()
        val matchesOtp = otp != null && clean == otp
        val matchesPin = storedPinHash.isNotBlank() && PreferencesManager.sha256Hex(clean) == storedPinHash
        return if (matchesOtp || matchesPin) {
            _twoFactorVerified.value = true
            _generatedOtp.value = null
            true
        } else {
            false
        }
    }

    fun bypassTwoFactorIfDisabled(profile: UserProfile?) {
        if (profile != null && !profile.twoFactorEnabled) {
            _twoFactorVerified.value = true
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
            repository.updateUserProfile(mapOf("themeMode" to mode))
        }
    }

    fun setCompactDensity(compact: Boolean) {
        viewModelScope.launch {
            prefs.setCompactDensity(compact)
        }
    }

    fun updateProfileSettings(
        displayName: String,
        phone: String,
        roleLabel: String,
        preferredZone: String,
        twoFactorEnabled: Boolean,
        newPinSixDigits: String?,
        pushAssignments: Boolean,
        pushPayments: Boolean,
        pushRouteAlerts: Boolean,
        webhookEnabled: Boolean,
        webhookUrl: String,
        webhookApiKey: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val fields = mutableMapOf<String, Any>(
                "displayName" to displayName.trim().ifBlank { "Operador T.Q.M." }.take(80),
                "phone" to phone.trim().take(30),
                "roleLabel" to roleLabel,
                "preferredZone" to preferredZone.take(80),
                "twoFactorEnabled" to twoFactorEnabled,
                "pushAssignments" to pushAssignments,
                "pushPayments" to pushPayments,
                "pushRouteAlerts" to pushRouteAlerts,
                "webhookEnabled" to webhookEnabled,
                "webhookUrl" to webhookUrl.trim().take(300),
                "webhookApiKey" to webhookApiKey.trim().take(150)
            )
            if (!newPinSixDigits.isNullOrBlank() && newPinSixDigits.length == 6 && newPinSixDigits.all { it.isDigit() }) {
                fields["twoFactorPinHash"] = PreferencesManager.sha256Hex(newPinSixDigits)
            }
            val result = repository.updateUserProfile(fields)
            _isBusy.value = false
            _statusMessage.value = result.fold(
                onSuccess = { "Perfil y preferencias guardados en la nube" },
                onFailure = { it.localizedMessage ?: "No se pudo actualizar el perfil" }
            )
        }
    }

    fun saveTechnician(tech: Technician, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveTechnician(tech)
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    repository.logNotification(
                        title = if (tech.id.isBlank()) "Técnico registrado" else "Técnico actualizado",
                        message = "${tech.fullName} • Salida ${tech.departureTime} • ${tech.barrio}",
                        notifType = "SISTEMA"
                    )
                    _statusMessage.value = "Técnico guardado correctamente"
                    onDone()
                },
                onFailure = {
                    _statusMessage.value = it.localizedMessage ?: "Error al guardar técnico"
                }
            )
        }
    }

    fun updateTechnicianStatus(tech: Technician, newStatus: TechStatus, zoneName: String? = null) {
        viewModelScope.launch {
            val zone = zoneName?.let { name -> IbagueGeo.zones.firstOrNull { it.name == name } }
            val res = repository.updateTechnicianStatus(
                techId = tech.id,
                status = newStatus,
                latitude = zone?.latitude,
                longitude = zone?.longitude
            )
            res.onSuccess {
                _statusMessage.value = "${tech.fullName}: ${newStatus.label}"
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Error actualizando estado"
            }
        }
    }

    fun deleteTechnician(techId: String) {
        viewModelScope.launch {
            val res = repository.deleteTechnician(techId)
            _statusMessage.value = res.fold(
                onSuccess = { "Técnico eliminado del equipo" },
                onFailure = { it.localizedMessage ?: "No se pudo eliminar" }
            )
        }
    }

    fun createAndMaybeAssignOrder(
        context: Context,
        order: ServiceOrder,
        autoAssignTech: Technician?,
        profile: UserProfile?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val prepared = if (autoAssignTech != null) {
                order.copy(
                    status = OrderStatus.ASIGNADA.name,
                    assignedTechId = autoAssignTech.id,
                    assignedTechName = autoAssignTech.fullName
                )
            } else {
                order
            }
            val res = repository.createOrder(prepared)
            _isBusy.value = false
            res.fold(
                onSuccess = { orderId ->
                    if (autoAssignTech != null) {
                        repository.updateTechnicianStatus(autoAssignTech.id, TechStatus.EN_RUTA)
                    }
                    val title = if (autoAssignTech != null) {
                        "Servicio ${prepared.orderCode} asignado"
                    } else {
                        "Nueva orden ${prepared.orderCode}"
                    }
                    val body = if (autoAssignTech != null) {
                        "${TechSpecialty.fromRaw(prepared.specialty).label} en ${prepared.barrio} -> ${autoAssignTech.fullName}"
                    } else {
                        "${TechSpecialty.fromRaw(prepared.specialty).label} para ${prepared.clientName} (${prepared.barrio})"
                    }
                    repository.logNotification(title, body, "ASIGNACION", orderId)
                    if (profile?.pushAssignments != false) {
                        NotificationDispatcher.sendDispatchNotification(context, title, body)
                    }
                    if (profile?.webhookEnabled == true && profile.webhookUrl.isNotBlank()) {
                        repository.dispatchWebhookEvent(
                            webhookUrl = profile.webhookUrl,
                            apiKey = profile.webhookApiKey,
                            eventType = "service_order.created",
                            payloadJson = JSONObject().apply {
                                put("orderId", orderId)
                                put("orderCode", prepared.orderCode)
                                put("specialty", prepared.specialty)
                                put("barrio", prepared.barrio)
                                put("assignedTechName", prepared.assignedTechName)
                                put("estimatedCostCop", prepared.estimatedCostCop)
                            }
                        )
                    }
                    _statusMessage.value = title
                    onDone()
                },
                onFailure = {
                    _statusMessage.value = it.localizedMessage ?: "Error al crear orden"
                }
            )
        }
    }

    fun assignOrder(
        context: Context,
        order: ServiceOrder,
        tech: Technician,
        profile: UserProfile?
    ) {
        viewModelScope.launch {
            val res = repository.assignOrderToTechnician(order.id, tech)
            res.fold(
                onSuccess = {
                    val dist = IbagueGeo.distanceKm(tech.latitude, tech.longitude, order.latitude, order.longitude)
                    val eta = IbagueGeo.estimateMinutes(dist)
                    val title = "Ruta asignada: ${order.orderCode}"
                    val body = "${tech.fullName} en camino a ${order.barrio} (~$eta min, ${"%.1f".format(dist)} km)"
                    repository.logNotification(title, body, "RUTA", order.id)
                    if (profile?.pushRouteAlerts != false) {
                        NotificationDispatcher.sendDispatchNotification(context, title, body)
                    }
                    _statusMessage.value = body
                },
                onFailure = {
                    _statusMessage.value = it.localizedMessage ?: "No se pudo asignar"
                }
            )
        }
    }

    fun advanceOrderStatus(
        context: Context,
        order: ServiceOrder,
        nextStatus: OrderStatus,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val res = repository.updateOrderStatus(order, nextStatus, notes)
            res.fold(
                onSuccess = {
                    val title = "Orden ${order.orderCode}: ${nextStatus.label}"
                    val body = "${order.clientName} (${order.barrio})"
                    repository.logNotification(title, body, "RUTA", order.id)
                    if (nextStatus == OrderStatus.COMPLETADA) {
                        NotificationDispatcher.sendDispatchNotification(context, title, "Servicio completado y reporte listo.")
                    }
                    _statusMessage.value = title
                },
                onFailure = {
                    _statusMessage.value = it.localizedMessage ?: "Error actualizando orden"
                }
            )
        }
    }

    fun registerPayment(
        context: Context,
        order: ServiceOrder,
        method: PaymentMethod,
        reference: String,
        paymentStatus: PaymentStatus,
        rating: Long?,
        profile: UserProfile?
    ) {
        viewModelScope.launch {
            val res = repository.registerOrderPaymentAndRating(
                orderId = order.id,
                method = method,
                reference = reference,
                paymentStatus = paymentStatus,
                rating = rating
            )
            res.fold(
                onSuccess = {
                    val title = "Pago ${paymentStatus.label}: ${order.orderCode}"
                    val body = "${method.label} • Ref: ${reference.ifBlank { "Directo" }}"
                    repository.logNotification(title, body, "PAGO", order.id)
                    if (profile?.pushPayments != false) {
                        NotificationDispatcher.sendDispatchNotification(context, title, body)
                    }
                    _statusMessage.value = "Transacción registrada en pasarela segura"
                },
                onFailure = {
                    _statusMessage.value = it.localizedMessage ?: "Error registrando pago"
                }
            )
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notifId)
        }
    }

    fun submitSupportTicket(subject: String, category: String, priority: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createSupportTicket(subject, category, priority, description)
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    repository.logNotification("Ticket de soporte", subject, "SOPORTE")
                    _statusMessage.value = "Solicitud de soporte técnico enviada"
                    onDone()
                },
                onFailure = {
                    _statusMessage.value = it.localizedMessage ?: "Error al enviar ticket"
                }
            )
        }
    }

    fun createCloudBackup(label: String, techs: List<Technician>, orders: List<ServiceOrder>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createCloudBackup(label, techs, orders)
            _isBusy.value = false
            _statusMessage.value = res.fold(
                onSuccess = { "Respaldo cifrado guardado en la nube" },
                onFailure = { it.localizedMessage ?: "Error al crear respaldo" }
            )
        }
    }

    fun testWebhook(url: String, apiKey: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.dispatchWebhookEvent(
                webhookUrl = url,
                apiKey = apiKey,
                eventType = "webhook.ping",
                payloadJson = JSONObject().apply {
                    put("message", "Prueba de conexión desde Ibagué T.Q.M. Multiservicios")
                    put("userId", currentUserId)
                }
            )
            _isBusy.value = false
            _statusMessage.value = res.fold(
                onSuccess = { "Webhook verificado: $it" },
                onFailure = { "Fallo en webhook: ${it.localizedMessage}" }
            )
        }
    }

    companion object {
        private const val TAG = "TqmViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
    }
}
