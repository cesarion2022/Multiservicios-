package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TqmConstants {
    const val BRAND_NAME = "Ibagué T.Q.M. Multiservicios"
    const val BRAND_SUBTITLE = "Servicios para la Ciudad Musical"
    const val NEQUI_NUMBER = "3014203319"
    const val BANCOLOMBIA_AHORROS = "71800000434"
    const val WHATSAPP_NUMBER = "+573165275176"
    const val WHATSAPP_DIGITS = "573165275176"
}

enum class TechSpecialty(val label: String, val shortLabel: String) {
    ELECTRICISTA("Electricista", "Eléctrico"),
    CERRAJERO("Cerrajero", "Cerrajería"),
    PLOMERO("Plomero", "Plomería"),
    DESATASCOS("Desatascos", "Desatascos"),
    AIRE_ACONDICIONADO("Aire Acondicionado", "A/C HVAC"),
    LAVADORAS("Lavadoras", "Lavadoras"),
    NEVERAS("Neveras", "Neveras"),
    COMPUTADORES("Computadores", "Cómputo"),
    SOLDADOR("Soldador", "Soldadura");

    companion object {
        fun fromRaw(raw: String): TechSpecialty =
            entries.firstOrNull { it.name == raw } ?: ELECTRICISTA
    }
}

enum class TechStatus(val label: String) {
    DISPONIBLE("Disponible"),
    EN_RUTA("En Ruta"),
    EN_SERVICIO("En Servicio"),
    FUERA_DE_TURNO("Fuera de Turno");

    companion object {
        fun fromRaw(raw: String): TechStatus =
            entries.firstOrNull { it.name == raw } ?: DISPONIBLE
    }
}

enum class OrderPriority(val label: String) {
    NORMAL("Normal"),
    URGENTE("Urgente"),
    EMERGENCIA("Emergencia");

    companion object {
        fun fromRaw(raw: String): OrderPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class OrderStatus(val label: String) {
    PENDIENTE("Pendiente"),
    ASIGNADA("Asignada"),
    EN_RUTA("En Ruta"),
    EN_PROGRESO("En Progreso"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada");

    companion object {
        fun fromRaw(raw: String): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: PENDIENTE
    }
}

enum class PaymentMethod(val label: String, val accountInfo: String) {
    NEQUI("Nequi", "Cel. ${TqmConstants.NEQUI_NUMBER}"),
    BANCOLOMBIA("Ahorros Bancolombia", "Cta. ${TqmConstants.BANCOLOMBIA_AHORROS}"),
    PSE("PSE / Transferencia", "Pasarela segura"),
    EFECTIVO("Efectivo contra entrega", "Pago directo al técnico");

    companion object {
        fun fromRaw(raw: String): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: NEQUI
    }
}

enum class PaymentStatus(val label: String) {
    PENDIENTE("Pendiente"),
    VERIFICADO("Verificado"),
    PAGADO("Pagado");

    companion object {
        fun fromRaw(raw: String): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: PENDIENTE
    }
}

data class IbagueZone(
    val name: String,
    val comuna: String,
    val latitude: Double,
    val longitude: Double
)

object IbagueGeo {
    const val MIN_LAT = 4.405
    const val MAX_LAT = 4.468
    const val MIN_LNG = -75.262
    const val MAX_LNG = -75.158

    val zones = listOf(
        IbagueZone("Centro", "Comuna 1", 4.4389, -75.2322),
        IbagueZone("Belén / La Pola", "Comuna 2", 4.4465, -75.2395),
        IbagueZone("Cádiz / Magisterio", "Comuna 3", 4.4420, -75.2225),
        IbagueZone("Piedra Pintada", "Comuna 4", 4.4410, -75.2095),
        IbagueZone("El Jardín / Av. 5ta", "Comuna 5", 4.4425, -75.2030),
        IbagueZone("Ambalá / El Vergel", "Comuna 6", 4.4510, -75.2070),
        IbagueZone("El Salado", "Comuna 7", 4.4585, -75.1640),
        IbagueZone("La Floresta / Calle 60", "Comuna 8", 4.4395, -75.1930),
        IbagueZone("Jordán (Etapas 1-9)", "Comuna 8", 4.4345, -75.1965),
        IbagueZone("Picaleña / Mirolindo", "Comuna 9", 4.4150, -75.1745),
        IbagueZone("Estadio / Cádiz Sur", "Comuna 10", 4.4310, -75.2140),
        IbagueZone("Ricaurte", "Comuna 12", 4.4215, -75.2360),
        IbagueZone("Boquerón", "Comuna 13", 4.4195, -75.2520)
    )

    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun estimateMinutes(distanceKm: Double): Int {
        // Urban Ibagué average speed ~24 km/h -> 2.5 min per km + 4 min dispatch buffer
        return (distanceKm * 2.5 + 4.0).toInt().coerceAtLeast(5)
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val roleLabel: String = "ADMIN",
    val preferredZone: String = "Centro",
    val themeMode: String = "DARK",
    val twoFactorEnabled: Boolean = true,
    val twoFactorPinHash: String = "",
    val pushAssignments: Boolean = true,
    val pushPayments: Boolean = true,
    val pushRouteAlerts: Boolean = true,
    val webhookEnabled: Boolean = false,
    val webhookUrl: String = "",
    val webhookApiKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Technician(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val documentNumber: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val barrio: String = "Centro",
    val specialties: List<String> = listOf(TechSpecialty.ELECTRICISTA.name),
    val departureTime: String = "07:30",
    val workSchedule: String = "07:30 - 17:30 L-S",
    val knowledgeNotes: String = "",
    val latitude: Double = 4.4389,
    val longitude: Double = -75.2322,
    val status: String = TechStatus.DISPONIBLE.name,
    val rating: Double = 5.0,
    val completedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ServiceOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderCode: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val address: String = "",
    val barrio: String = "Centro",
    val latitude: Double = 4.4389,
    val longitude: Double = -75.2322,
    val specialty: String = TechSpecialty.ELECTRICISTA.name,
    val priority: String = OrderPriority.NORMAL.name,
    val status: String = OrderStatus.PENDIENTE.name,
    val assignedTechId: String = "",
    val assignedTechName: String = "",
    val scheduledTime: String = "",
    val estimatedCostCop: Long = 0L,
    val paymentMethod: String = PaymentMethod.NEQUI.name,
    val paymentReference: String = "",
    val paymentStatus: String = PaymentStatus.PENDIENTE.name,
    val description: String = "",
    val technicianNotes: String = "",
    val clientRating: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PushNotificationLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val notifType: String = "SISTEMA",
    val orderId: String = "",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val category: String = TechSpecialty.ELECTRICISTA.name,
    val priority: String = "NORMAL",
    val description: String = "",
    val status: String = "ABIERTO",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudBackupSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val techCount: Long = 0L,
    val orderCount: Long = 0L,
    val totalRevenueCop: Long = 0L,
    val checksum: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
