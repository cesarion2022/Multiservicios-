package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.ServiceOrder
import com.example.model.TechSpecialty
import com.example.model.TechStatus
import com.example.model.Technician
import com.example.model.TqmConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationDispatcher {
    private const val CHANNEL_DISPATCH = "tqm_dispatch_alerts"
    private const val CHANNEL_SECURITY = "tqm_security_2fa"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val dispatchChannel = NotificationChannel(
                CHANNEL_DISPATCH,
                "Asignaciones y Rutas T.Q.M.",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas en tiempo real de asignación de técnicos y pagos."
            }
            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY,
                "Códigos de Seguridad 2FA",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Verificación de dos factores para inicio de sesión."
            }
            mgr.createNotificationChannel(dispatchChannel)
            mgr.createNotificationChannel(securityChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendDispatchNotification(context: Context, title: String, body: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_DISPATCH)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        mgr.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    fun sendTwoFactorCodeNotification(context: Context, code: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_SECURITY)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Código 2FA - Ibagué T.Q.M.")
            .setContentText("Tu código de verificación de sesión es: $code")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        mgr.notify(202602, notification)
    }
}

object ReportExporter {
    private val copFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply {
        maximumFractionDigits = 0
    }

    fun formatCop(amount: Long): String = copFormat.format(amount)

    fun writePdfReport(
        outputStream: OutputStream,
        technicians: List<Technician>,
        orders: List<ServiceOrder>
    ) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 61, 110)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(46, 139, 87)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        var y = 46f

        canvas.drawText(TqmConstants.BRAND_NAME.uppercase(), 36f, y, titlePaint)
        y += 16f
        canvas.drawText("${TqmConstants.BRAND_SUBTITLE} | Generado: $nowStr", 36f, y, subtitlePaint)
        y += 10f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 22f

        val completedCount = orders.count { it.status == OrderStatus.COMPLETADA.name }
        val paidRevenue = orders.filter { it.paymentStatus == PaymentStatus.PAGADO.name }.sumOf { it.estimatedCostCop }
        val totalPipeline = orders.sumOf { it.estimatedCostCop }

        canvas.drawText("1. Resumen Operativo y Financiero", 36f, y, sectionPaint)
        y += 16f
        canvas.drawText("Técnicos registrados: ${technicians.size} | Órdenes totales: ${orders.size} | Completadas: $completedCount", 36f, y, bodyPaint)
        y += 14f
        canvas.drawText("Ingresos confirmados: ${formatCop(paidRevenue)} | Valor total cartera: ${formatCop(totalPipeline)}", 36f, y, bodyPaint)
        y += 14f
        canvas.drawText("Canales de pago: Nequi ${TqmConstants.NEQUI_NUMBER} | Ahorros Bancolombia ${TqmConstants.BANCOLOMBIA_AHORROS} | WhatsApp ${TqmConstants.WHATSAPP_NUMBER}", 36f, y, bodyPaint)
        y += 22f

        canvas.drawText("2. Equipo Técnico y Horarios", 36f, y, sectionPaint)
        y += 16f
        if (technicians.isEmpty()) {
            canvas.drawText("No hay técnicos registrados aún.", 36f, y, bodyPaint)
            y += 14f
        } else {
            technicians.take(14).forEach { tech ->
                val specs = tech.specialties.joinToString(", ") { TechSpecialty.fromRaw(it).shortLabel }
                val line = "• ${tech.fullName} (${TechStatus.fromRaw(tech.status).label}) | Salida: ${tech.departureTime} | $specs | ${tech.barrio}"
                canvas.drawText(line.take(96), 36f, y, bodyPaint)
                y += 13f
            }
        }

        y += 12f
        canvas.drawText("3. Registro de Órdenes y Pasarela de Pagos", 36f, y, sectionPaint)
        y += 16f
        if (orders.isEmpty()) {
            canvas.drawText("No hay órdenes de servicio registradas.", 36f, y, bodyPaint)
        } else {
            orders.take(22).forEach { order ->
                if (y > 805f) return@forEach
                val line = "• ${order.orderCode} | ${order.clientName} | ${TechSpecialty.fromRaw(order.specialty).shortLabel} | ${OrderStatus.fromRaw(order.status).label} | ${formatCop(order.estimatedCostCop)} (${PaymentMethod.fromRaw(order.paymentMethod).label}: ${PaymentStatus.fromRaw(order.paymentStatus).label})"
                canvas.drawText(line.take(100), 36f, y, bodyPaint)
                y += 13f
            }
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
    }

    fun buildCsvContent(technicians: List<Technician>, orders: List<ServiceOrder>): String {
        val sb = StringBuilder()
        sb.appendLine("TIPO,ID,CODIGO_O_CEDULA,NOMBRE,TELEFONO,BARRIO,ESPECIALIDAD,ESTADO,HORA_SALIDA_O_CITA,VALOR_COP,METODO_PAGO,ESTADO_PAGO")
        technicians.forEach { t ->
            val specs = t.specialties.joinToString(";") { TechSpecialty.fromRaw(it).label }
            sb.appendLine("TECNICO,${csv(t.id)},${csv(t.documentNumber)},${csv(t.fullName)},${csv(t.phone)},${csv(t.barrio)},${csv(specs)},${csv(t.status)},${csv(t.departureTime)},0,N/A,N/A")
        }
        orders.forEach { o ->
            sb.appendLine("ORDEN,${csv(o.id)},${csv(o.orderCode)},${csv(o.clientName)},${csv(o.clientPhone)},${csv(o.barrio)},${csv(o.specialty)},${csv(o.status)},${csv(o.scheduledTime)},${o.estimatedCostCop},${csv(o.paymentMethod)},${csv(o.paymentStatus)}")
        }
        return sb.toString()
    }

    fun buildJsonContent(technicians: List<Technician>, orders: List<ServiceOrder>): String {
        val techArr = JSONArray().apply {
            technicians.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("fullName", t.fullName)
                    put("documentNumber", t.documentNumber)
                    put("phone", t.phone)
                    put("barrio", t.barrio)
                    put("specialties", JSONArray(t.specialties))
                    put("departureTime", t.departureTime)
                    put("workSchedule", t.workSchedule)
                    put("knowledgeNotes", t.knowledgeNotes)
                    put("latitude", t.latitude)
                    put("longitude", t.longitude)
                    put("status", t.status)
                })
            }
        }
        val orderArr = JSONArray().apply {
            orders.forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id)
                    put("orderCode", o.orderCode)
                    put("clientName", o.clientName)
                    put("clientPhone", o.clientPhone)
                    put("address", o.address)
                    put("barrio", o.barrio)
                    put("specialty", o.specialty)
                    put("priority", o.priority)
                    put("status", o.status)
                    put("assignedTechName", o.assignedTechName)
                    put("estimatedCostCop", o.estimatedCostCop)
                    put("paymentMethod", o.paymentMethod)
                    put("paymentReference", o.paymentReference)
                    put("paymentStatus", o.paymentStatus)
                    put("clientRating", o.clientRating)
                })
            }
        }
        return JSONObject().apply {
            put("brand", TqmConstants.BRAND_NAME)
            put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            put("technicians", techArr)
            put("orders", orderArr)
        }.toString(2)
    }

    fun shareCachedPdf(
        context: Context,
        technicians: List<Technician>,
        orders: List<ServiceOrder>
    ): Intent? {
        return try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "Reporte_Ibague_TQM_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                writePdfReport(out, technicians, orders)
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Reporte Operativo - ${TqmConstants.BRAND_NAME}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun csv(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
