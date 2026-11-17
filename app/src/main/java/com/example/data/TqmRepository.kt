package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CloudBackupSnapshot
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.PushNotificationLog
import com.example.model.ServiceOrder
import com.example.model.SupportTicket
import com.example.model.TechStatus
import com.example.model.Technician
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TqmRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext)
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para operar en Ibagué T.Q.M.")
    }

    // --- User Profile & Preferences ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String
    ): Result<UserProfile> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                val parsed = existing.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid, displayName = displayName.ifBlank { "Operador TQM" }, email = email)
                return Result.success(parsed)
            }
            val safeName = displayName.trim().ifBlank { "Operador T.Q.M." }.take(80)
            val safeEmail = email.trim().take(120)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "email" to safeEmail,
                "phone" to "",
                "roleLabel" to "ADMIN",
                "preferredZone" to "Centro",
                "themeMode" to "DARK",
                "twoFactorEnabled" to true,
                "twoFactorPinHash" to "",
                "pushAssignments" to true,
                "pushPayments" to true,
                "pushRouteAlerts" to true,
                "webhookEnabled" to false,
                "webhookUrl" to "",
                "webhookApiKey" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    email = safeEmail,
                    roleLabel = "ADMIN",
                    preferredZone = "Centro",
                    themeMode = "DARK",
                    twoFactorEnabled = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(fields: Map<String, Any>): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val safePayload = fields.toMutableMap().apply {
                remove("id")
                remove("userId")
                remove("createdAt")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(safePayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Technicians ---

    fun observeTechnicians(userId: String): Flow<List<Technician>> = flow {
        val path = "users/$userId/technicians"
        emitAll(
            db.collection("users").document(userId).collection("technicians")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Technician::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.fullName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTechnician(tech: Technician): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/technicians"
        return try {
            val uid = requireUserId()
            require(tech.fullName.trim().length in 2..80) { "El nombre del técnico debe tener entre 2 y 80 caracteres." }
            require(tech.documentNumber.trim().length in 3..30) { "El documento debe tener entre 3 y 30 caracteres." }
            require(tech.phone.trim().length in 5..30) { "El teléfono debe tener entre 5 y 30 caracteres." }
            require(tech.specialties.isNotEmpty() && tech.specialties.size <= 9) { "Selecciona al menos 1 especialidad." }

            val collection = db.collection("users").document(uid).collection("technicians")
            val isNew = tech.id.isBlank()
            val docRef = if (isNew) collection.document() else collection.document(tech.id)
            path = docRef.path

            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "fullName" to tech.fullName.trim().take(80),
                    "documentNumber" to tech.documentNumber.trim().take(30),
                    "phone" to tech.phone.trim().take(30),
                    "email" to tech.email.trim().take(120),
                    "address" to tech.address.trim().take(160),
                    "barrio" to tech.barrio.trim().ifBlank { "Centro" }.take(80),
                    "specialties" to tech.specialties.take(9),
                    "departureTime" to tech.departureTime.trim().ifBlank { "07:30" }.take(20),
                    "workSchedule" to tech.workSchedule.trim().ifBlank { "07:30 - 17:30 L-S" }.take(60),
                    "knowledgeNotes" to tech.knowledgeNotes.trim().take(400),
                    "latitude" to tech.latitude.coerceIn(4.2, 4.7),
                    "longitude" to tech.longitude.coerceIn(-75.5, -75.0),
                    "status" to tech.status,
                    "rating" to tech.rating.coerceIn(0.0, 5.0),
                    "completedCount" to tech.completedCount.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "fullName" to tech.fullName.trim().take(80),
                    "documentNumber" to tech.documentNumber.trim().take(30),
                    "phone" to tech.phone.trim().take(30),
                    "email" to tech.email.trim().take(120),
                    "address" to tech.address.trim().take(160),
                    "barrio" to tech.barrio.trim().ifBlank { "Centro" }.take(80),
                    "specialties" to tech.specialties.take(9),
                    "departureTime" to tech.departureTime.trim().ifBlank { "07:30" }.take(20),
                    "workSchedule" to tech.workSchedule.trim().ifBlank { "07:30 - 17:30 L-S" }.take(60),
                    "knowledgeNotes" to tech.knowledgeNotes.trim().take(400),
                    "latitude" to tech.latitude.coerceIn(4.2, 4.7),
                    "longitude" to tech.longitude.coerceIn(-75.5, -75.0),
                    "status" to tech.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTechnicianStatus(
        techId: String,
        status: TechStatus,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/technicians/$techId"
        return try {
            val uid = requireUserId()
            require(techId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("technicians").document(techId)
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (latitude != null && longitude != null) {
                payload["latitude"] = latitude.coerceIn(4.2, 4.7)
                payload["longitude"] = longitude.coerceIn(-75.5, -75.0)
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTechnician(techId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/technicians/$techId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("technicians").document(techId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Service Orders ---

    fun observeOrders(userId: String): Flow<List<ServiceOrder>> = flow {
        val path = "users/$userId/orders"
        emitAll(
            db.collection("users").document(userId).collection("orders")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ServiceOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createOrder(order: ServiceOrder): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders"
        return try {
            val uid = requireUserId()
            require(order.clientName.trim().length in 2..80) { "Nombre de cliente inválido." }
            require(order.clientPhone.trim().length in 5..30) { "Teléfono de cliente inválido." }
            require(order.address.trim().length in 3..160) { "Dirección inválida." }

            val docRef = db.collection("users").document(uid).collection("orders").document()
            path = docRef.path
            val code = order.orderCode.ifBlank { "TQM-${(1000..9999).random()}" }

            val payload = mapOf(
                "userId" to uid,
                "orderCode" to code.take(30),
                "clientName" to order.clientName.trim().take(80),
                "clientPhone" to order.clientPhone.trim().take(30),
                "address" to order.address.trim().take(160),
                "barrio" to order.barrio.trim().ifBlank { "Centro" }.take(80),
                "latitude" to order.latitude.coerceIn(4.2, 4.7),
                "longitude" to order.longitude.coerceIn(-75.5, -75.0),
                "specialty" to order.specialty,
                "priority" to order.priority,
                "status" to order.status,
                "assignedTechId" to order.assignedTechId.take(128),
                "assignedTechName" to order.assignedTechName.take(80),
                "scheduledTime" to order.scheduledTime.take(30),
                "estimatedCostCop" to order.estimatedCostCop.coerceIn(0L, 500_000_000L),
                "paymentMethod" to order.paymentMethod,
                "paymentReference" to order.paymentReference.take(80),
                "paymentStatus" to order.paymentStatus,
                "description" to order.description.trim().take(500),
                "technicianNotes" to order.technicianNotes.trim().take(500),
                "clientRating" to order.clientRating.coerceIn(0L, 5L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun assignOrderToTechnician(
        orderId: String,
        tech: Technician
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/$orderId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("orders").document(orderId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "assignedTechId" to tech.id,
                    "assignedTechName" to tech.fullName.take(80),
                    "status" to OrderStatus.ASIGNADA.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(uid).collection("technicians").document(tech.id)
                .update(
                    mapOf(
                        "status" to TechStatus.EN_RUTA.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(
        order: ServiceOrder,
        newStatus: OrderStatus,
        technicianNotes: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/${order.id}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("orders").document(order.id)
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (technicianNotes != null) {
                payload["technicianNotes"] = technicianNotes.trim().take(500)
            }
            docRef.update(payload).await()

            if (newStatus == OrderStatus.COMPLETADA && order.assignedTechId.isNotBlank()) {
                val techRef = db.collection("users").document(uid)
                    .collection("technicians").document(order.assignedTechId)
                techRef.update(
                    mapOf(
                        "status" to TechStatus.DISPONIBLE.name,
                        "completedCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun registerOrderPaymentAndRating(
        orderId: String,
        method: PaymentMethod,
        reference: String,
        paymentStatus: PaymentStatus,
        rating: Long? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/$orderId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("orders").document(orderId)
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "paymentMethod" to method.name,
                "paymentReference" to reference.trim().take(80),
                "paymentStatus" to paymentStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (rating != null) {
                payload["clientRating"] = rating.coerceIn(0L, 5L)
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Notifications ---

    fun observeNotifications(userId: String): Flow<List<PushNotificationLog>> = flow {
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PushNotificationLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logNotification(
        title: String,
        message: String,
        notifType: String,
        orderId: String = ""
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notifications").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to title.trim().ifBlank { "Notificación T.Q.M." }.take(100),
                    "message" to message.trim().ifBlank { "Actualización de servicio" }.take(300),
                    "notifType" to notifType,
                    "orderId" to orderId.take(128),
                    "readFlag" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications/$notifId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notifications").document(notifId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "readFlag" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Support Tickets ---

    fun observeSupportTickets(userId: String): Flow<List<SupportTicket>> = flow {
        val path = "users/$userId/supportTickets"
        emitAll(
            db.collection("users").document(userId).collection("supportTickets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSupportTicket(
        subject: String,
        category: String,
        priority: String,
        description: String
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/supportTickets"
        return try {
            val uid = requireUserId()
            require(subject.trim().length in 2..120) { "El asunto debe tener entre 2 y 120 caracteres." }
            require(description.trim().length in 5..800) { "La descripción debe tener al menos 5 caracteres." }
            val docRef = db.collection("users").document(uid).collection("supportTickets").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "subject" to subject.trim().take(120),
                    "category" to category.take(60),
                    "priority" to priority,
                    "description" to description.trim().take(800),
                    "status" to "ABIERTO",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Cloud Backups ---

    fun observeBackups(userId: String): Flow<List<CloudBackupSnapshot>> = flow {
        val path = "users/$userId/backups"
        emitAll(
            db.collection("users").document(userId).collection("backups")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudBackupSnapshot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createCloudBackup(
        label: String,
        techs: List<Technician>,
        orders: List<ServiceOrder>
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/backups"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("backups").document()
            path = docRef.path
            val totalRevenue = orders
                .filter { it.paymentStatus == PaymentStatus.PAGADO.name || it.status == OrderStatus.COMPLETADA.name }
                .sumOf { it.estimatedCostCop }
            val rawSummary = "${techs.size}:${orders.size}:$totalRevenue:${System.currentTimeMillis()}"
            val checksum = PreferencesManager.sha256Hex(rawSummary).take(16)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "label" to label.trim().ifBlank { "Respaldo Nube T.Q.M." }.take(100),
                    "techCount" to techs.size.toLong(),
                    "orderCount" to orders.size.toLong(),
                    "totalRevenueCop" to totalRevenue.coerceAtLeast(0L),
                    "checksum" to checksum,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Third-Party Webhook API Integration ---

    suspend fun dispatchWebhookEvent(
        webhookUrl: String,
        apiKey: String,
        eventType: String,
        payloadJson: JSONObject
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(webhookUrl.startsWith("https://") || webhookUrl.startsWith("http://")) {
                "La URL del webhook debe iniciar con https:// o http://"
            }
            val bodyJson = JSONObject().apply {
                put("source", "IBAGUE_TQM_MULTISERVICIOS")
                put("event", eventType)
                put("timestamp", System.currentTimeMillis())
                put("data", payloadJson)
            }
            val body = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val reqBuilder = Request.Builder()
                .url(webhookUrl.trim())
                .post(body)
                .addHeader("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("HTTP ${response.code} OK")
                } else {
                    Result.failure(IllegalStateException("Error HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun createConfiguredFirestore(context: Context): FirebaseFirestore {
            val dbId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
                db.firestoreSettings = settings
            } catch (_: Exception) {
                // Settings already initialized for this instance
            }
            return db
        }
    }
}
