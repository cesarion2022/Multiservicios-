package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CloudBackupSnapshot
import com.example.model.IbagueGeo
import com.example.model.OrderStatus
import com.example.model.PaymentStatus
import com.example.model.PushNotificationLog
import com.example.model.ServiceOrder
import com.example.model.TechSpecialty
import com.example.model.TechStatus
import com.example.model.Technician
import com.example.model.UserProfile
import com.example.ui.theme.TqmAmber
import com.example.ui.theme.TqmEmerald
import com.example.ui.theme.TqmSkyLight
import com.example.util.ReportExporter

@Composable
fun AdminAnalyticsScreen(
    technicians: List<Technician>,
    orders: List<ServiceOrder>,
    backups: List<CloudBackupSnapshot>,
    onCreateBackup: (String) -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writePdfReport(out, technicians, orders)
                }
                onPostMessage("Reporte PDF exportado correctamente")
            } catch (e: Exception) {
                onPostMessage("Error exportando PDF: ${e.localizedMessage}")
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ReportExporter.buildCsvContent(technicians, orders).toByteArray(Charsets.UTF_8))
                }
                onPostMessage("Datos CSV exportados correctamente")
            } catch (e: Exception) {
                onPostMessage("Error exportando CSV: ${e.localizedMessage}")
            }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ReportExporter.buildJsonContent(technicians, orders).toByteArray(Charsets.UTF_8))
                }
                onPostMessage("Datos JSON exportados correctamente")
            } catch (e: Exception) {
                onPostMessage("Error exportando JSON: ${e.localizedMessage}")
            }
        }
    }

    val completedCount = orders.count { it.status == OrderStatus.COMPLETADA.name }
    val completionPct = if (orders.isEmpty()) 0 else (completedCount * 100) / orders.size
    val paidRevenue = orders.filter { it.paymentStatus == PaymentStatus.PAGADO.name }.sumOf { it.estimatedCostCop }
    val activeTechs = technicians.count { it.status != TechStatus.FUERA_DE_TURNO.name }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Panel de Control Administrativo", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Órdenes", "${orders.size}", "$completedCount completadas", TqmSkyLight, Modifier.weight(1f))
                KpiCard("Eficiencia", "$completionPct%", "$activeTechs técnicos activos", TqmEmerald, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Ingresos", ReportExporter.formatCop(paidRevenue), "Pagos verificados", TqmAmber, Modifier.weight(1f))
                KpiCard("Respaldos", "${backups.size}", "Sincronizado en nube", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Demanda por Especialidad (9 Oficios)", style = MaterialTheme.typography.titleMedium)
                    val counts = TechSpecialty.entries.map { spec ->
                        spec.shortLabel to orders.count { it.specialty == spec.name }
                    }
                    val maxVal = (counts.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    val barColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        val barWidth = (size.width / counts.size) * 0.62f
                        val gap = size.width / counts.size
                        counts.forEachIndexed { index, (_, count) ->
                            val x = index * gap + (gap - barWidth) / 2f
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(x, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            val h = (count.toFloat() / maxVal.toFloat()) * size.height
                            if (h > 0f) {
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barWidth, h),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        counts.forEach { (label, count) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(label.take(4), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exportación de Reportes y Datos", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { pdfExportLauncher.launch("Reporte_Ibague_TQM.pdf") },
                            modifier = Modifier.weight(1f).testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = ReportExporter.shareCachedPdf(context, technicians, orders)
                                if (intent != null) context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { csvExportLauncher.launch("Ibague_TQM_Datos.csv") },
                            modifier = Modifier.weight(1f).testTag("export_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Exportar CSV")
                        }
                        OutlinedButton(
                            onClick = { jsonExportLauncher.launch("Ibague_TQM_Datos.json") },
                            modifier = Modifier.weight(1f).testTag("export_json_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Exportar JSON")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Respaldos Automáticos en la Nube", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { onCreateBackup("Respaldo Manual T.Q.M.") },
                        modifier = Modifier.fillMaxWidth().testTag("create_backup_button")
                    ) {
                        Text("Generar Respaldo Cifrado Ahora")
                    }
                    backups.take(4).forEach { b ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(b.label, style = MaterialTheme.typography.labelLarge)
                                    Text("${b.techCount} técnicos • ${b.orderCount} órdenes", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("SHA:${b.checksum.take(8)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProfileSettingsScreen(
    profile: UserProfile?,
    themeMode: String,
    compactDensity: Boolean,
    e2eFingerprint: String,
    onSetThemeMode: (String) -> Unit,
    onSetCompactDensity: (Boolean) -> Unit,
    onSaveProfile: (String, String, String, String, Boolean, String?, Boolean, Boolean, Boolean, Boolean, String, String) -> Unit,
    onTestWebhook: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile) { mutableStateOf(profile?.displayName.orEmpty()) }
    var phone by remember(profile) { mutableStateOf(profile?.phone.orEmpty()) }
    var roleLabel by remember(profile) { mutableStateOf(profile?.roleLabel ?: "ADMIN") }
    var preferredZone by remember(profile) { mutableStateOf(profile?.preferredZone ?: IbagueGeo.zones.first().name) }
    var twoFactorEnabled by remember(profile) { mutableStateOf(profile?.twoFactorEnabled ?: true) }
    var newPin by remember { mutableStateOf("") }
    var pushAssignments by remember(profile) { mutableStateOf(profile?.pushAssignments ?: true) }
    var pushPayments by remember(profile) { mutableStateOf(profile?.pushPayments ?: true) }
    var pushRouteAlerts by remember(profile) { mutableStateOf(profile?.pushRouteAlerts ?: true) }
    var webhookEnabled by remember(profile) { mutableStateOf(profile?.webhookEnabled ?: false) }
    var webhookUrl by remember(profile) { mutableStateOf(profile?.webhookUrl.orEmpty()) }
    var webhookApiKey by remember(profile) { mutableStateOf(profile?.webhookApiKey.orEmpty()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Perfil de Usuario y Rol", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Nombre completo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Rol en plataforma:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ADMIN", "DISPATCHER", "TECHNICIAN", "CLIENT").forEach { r ->
                            FilterChip(
                                selected = roleLabel == r,
                                onClick = { roleLabel = r },
                                label = { Text(r.take(6)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = preferredZone,
                        onValueChange = { preferredZone = it },
                        label = { Text("Zona / Comuna de Ibagué") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Apariencia y Modo Oscuro", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("DARK" to "Oscuro", "LIGHT" to "Claro", "SYSTEM" to "Sistema").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Modo compacto de tarjetas", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = compactDensity, onCheckedChange = onSetCompactDensity)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = TqmEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("Seguridad 2FA y Cifrado E2E", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Huella AES-256-GCM del dispositivo: $e2eFingerprint",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Requerir 2FA en cada inicio de sesión", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = twoFactorEnabled, onCheckedChange = { twoFactorEnabled = it })
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Configurar / Cambiar PIN 2FA (6 dígitos)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Canales de Notificaciones Push", style = MaterialTheme.typography.titleSmall)
                    }
                    ToggleRow("Asignación de servicios", pushAssignments) { pushAssignments = it }
                    ToggleRow("Confirmación de pagos", pushPayments) { pushPayments = it }
                    ToggleRow("Alertas de ruta y llegada", pushRouteAlerts) { pushRouteAlerts = it }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Api, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("Integración APIs y Webhooks", style = MaterialTheme.typography.titleMedium)
                    }
                    ToggleRow("Automatizar flujo vía Webhook HTTP", webhookEnabled) { webhookEnabled = it }
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("URL Endpoint Webhook (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = webhookApiKey,
                        onValueChange = { webhookApiKey = it },
                        label = { Text("Bearer API Key (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { onTestWebhook(webhookUrl, webhookApiKey) },
                        enabled = webhookUrl.startsWith("http"),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Probar Conexión API / Webhook")
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSaveProfile(
                        displayName, phone, roleLabel, preferredZone,
                        twoFactorEnabled, newPin.takeIf { it.length == 6 },
                        pushAssignments, pushPayments, pushRouteAlerts,
                        webhookEnabled, webhookUrl, webhookApiKey
                    )
                    newPin = ""
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_button")
            ) {
                Text("Guardar Preferencias en la Nube")
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cerrar Sesión")
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<PushNotificationLog>,
    onMarkRead: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Notificaciones en Tiempo Real", style = MaterialTheme.typography.titleLarge)
            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay notificaciones recientes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { n ->
                        Surface(
                            color = if (n.readFlag) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.medium,
                            onClick = { if (!n.readFlag) onMarkRead(n.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                                    Text(n.notifType, style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(n.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
