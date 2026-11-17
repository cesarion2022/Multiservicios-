package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.PaymentStatus
import com.example.model.ServiceOrder
import com.example.model.SupportTicket
import com.example.model.TechSpecialty
import com.example.model.Technician
import com.example.model.TqmConstants
import com.example.ui.theme.TqmAmber
import com.example.ui.theme.TqmEmerald
import com.example.util.ReportExporter

@Composable
fun ClientPortalAndPaymentsScreen(
    technicians: List<Technician>,
    orders: List<ServiceOrder>,
    supportTickets: List<SupportTicket>,
    onRegisterPayment: (ServiceOrder, PaymentMethod, String, PaymentStatus, Long?) -> Unit,
    onSubmitTicket: (String, String, String, String, () -> Unit) -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var segment by remember { mutableIntStateOf(0) }
    var checkoutOrder by remember { mutableStateOf<ServiceOrder?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SegmentedButton(
                selected = segment == 0,
                onClick = { segment = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("portal_payments_tab")
            ) {
                Text("Portal y Pagos")
            }
            SegmentedButton(
                selected = segment == 1,
                onClick = { segment = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("portal_support_tab")
            ) {
                Text("Soporte Técnico (${supportTickets.size})")
            }
        }

        if (segment == 0) {
            val filteredOrders = remember(orders, searchQuery) {
                if (searchQuery.isBlank()) orders
                else orders.filter {
                    it.clientName.contains(searchQuery, ignoreCase = true) ||
                        it.orderCode.contains(searchQuery, ignoreCase = true) ||
                        it.barrio.contains(searchQuery, ignoreCase = true)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PaymentGatewayHeaderCard(
                        onCopy = { label, value ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(label, value))
                            onPostMessage("$label copiado: $value")
                        },
                        onOpenWhatsApp = {
                            openWhatsApp(context, "Hola Ibagué T.Q.M. Multiservicios, deseo confirmar un pago o solicitar servicio.")
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Buscar por cliente, código TQM o barrio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historial de Servicios (${filteredOrders.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = {
                                val intent = ReportExporter.shareCachedPdf(context, technicians, filteredOrders)
                                if (intent != null) {
                                    context.startActivity(Intent.createChooser(intent, "Compartir comprobante PDF"))
                                }
                            }
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF Historial")
                        }
                    }
                }

                if (filteredOrders.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Aún no hay servicios en el historial. Las órdenes creadas y completadas aparecerán aquí con su recibo de pago.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(filteredOrders, key = { it.id }) { order ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${order.orderCode} • ${TechSpecialty.fromRaw(order.specialty).label}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${order.clientName} • ${order.barrio}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        label = PaymentStatus.fromRaw(order.paymentStatus).label,
                                        color = if (order.paymentStatus == PaymentStatus.PAGADO.name) TqmEmerald else TqmAmber
                                    )
                                }

                                Text(
                                    text = "Estado servicio: ${OrderStatus.fromRaw(order.status).label} | Valor: ${ReportExporter.formatCop(order.estimatedCostCop)} (${PaymentMethod.fromRaw(order.paymentMethod).label})",
                                    style = MaterialTheme.typography.labelMedium
                                )

                                if (order.technicianNotes.isNotBlank()) {
                                    Text(
                                        text = "Reporte técnico: ${order.technicianNotes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (order.clientRating > 0L) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        repeat(order.clientRating.toInt()) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = TqmAmber,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text("Calificación del cliente", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { checkoutOrder = order },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (order.paymentStatus == PaymentStatus.PAGADO.name) "Recibo / Calificar" else "Pagar Ahora")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            openWhatsApp(
                                                context,
                                                "Hola Ibagué T.Q.M., envío soporte de pago orden ${order.orderCode} por ${ReportExporter.formatCop(order.estimatedCostCop)}."
                                            )
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("WhatsApp")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            SpecializedSupportView(
                tickets = supportTickets,
                onSubmitTicket = onSubmitTicket,
                onOpenWhatsApp = {
                    openWhatsApp(context, "Hola soporte técnico especializado Ibagué T.Q.M., necesito asistencia urgente.")
                }
            )
        }
    }

    checkoutOrder?.let { order ->
        PaymentCheckoutDialog(
            order = order,
            onDismiss = { checkoutOrder = null },
            onConfirm = { method, ref, status, rating ->
                onRegisterPayment(order, method, ref, status, rating)
                checkoutOrder = null
            }
        )
    }
}

@Composable
private fun PaymentGatewayHeaderCard(
    onCopy: (String, String) -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = TqmEmerald)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pasarela de Pagos Oficial T.Q.M.",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            PaymentChannelRow(
                title = "Nequi Oficial",
                account = TqmConstants.NEQUI_NUMBER,
                onCopy = { onCopy("Nequi", TqmConstants.NEQUI_NUMBER) }
            )
            PaymentChannelRow(
                title = "Ahorros Bancolombia",
                account = TqmConstants.BANCOLOMBIA_AHORROS,
                onCopy = { onCopy("Bancolombia", TqmConstants.BANCOLOMBIA_AHORROS) }
            )

            OutlinedButton(
                onClick = onOpenWhatsApp,
                modifier = Modifier.fillMaxWidth().testTag("whatsapp_support_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = TqmEmerald)
                Spacer(Modifier.width(8.dp))
                Text("Enviar comprobante WhatsApp ${TqmConstants.WHATSAPP_NUMBER}")
            }
        }
    }
}

@Composable
private fun PaymentChannelRow(
    title: String,
    account: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(account, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar $title")
            }
        }
    }
}

@Composable
private fun PaymentCheckoutDialog(
    order: ServiceOrder,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod, String, PaymentStatus, Long?) -> Unit
) {
    var method by remember { mutableStateOf(PaymentMethod.fromRaw(order.paymentMethod)) }
    var reference by remember { mutableStateOf(order.paymentReference) }
    var paymentStatus by remember { mutableStateOf(PaymentStatus.PAGADO) }
    var rating by remember { mutableLongStateOf(if (order.clientRating > 0L) order.clientRating else 5L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pago Seguro • ${order.orderCode}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Total a pagar: ${ReportExporter.formatCop(order.estimatedCostCop)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                PaymentMethod.entries.forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text("${m.label} (${m.accountInfo})") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Número de comprobante / referencia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentStatus.entries.forEach { st ->
                        FilterChip(
                            selected = paymentStatus == st,
                            onClick = { paymentStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                Text("Calificación del servicio:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1L..5L).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "$star estrellas",
                                tint = if (star <= rating) TqmAmber else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(method, reference, paymentStatus, rating) }) {
                Text("Confirmar Transacción")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SpecializedSupportView(
    tickets: List<SupportTicket>,
    onSubmitTicket: (String, String, String, String, () -> Unit) -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TechSpecialty.ELECTRICISTA) }
    var catExpanded by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf("NORMAL") }
    var description by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.large)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HeadsetMic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Soporte Técnico Especializado", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Atención prioritaria para garantías, fallas complejas en refrigeración, electricidad, plomería y soporte de plataforma.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Asunto del ticket *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box {
                        OutlinedButton(onClick = { catExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Especialidad: ${category.label}")
                        }
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            TechSpecialty.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { category = s; catExpanded = false }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NORMAL", "ALTA", "CRITICA").forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Describe el caso técnico *") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onSubmitTicket(subject, category.name, priority, description) {
                                    subject = ""
                                    description = ""
                                }
                            },
                            enabled = subject.trim().length >= 2 && description.trim().length >= 5,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Crear Ticket")
                        }
                        OutlinedButton(onClick = onOpenWhatsApp) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp Directo")
                        }
                    }
                }
            }
        }

        items(tickets, key = { it.id }) { ticket ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ticket.subject, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusPill(ticket.status, MaterialTheme.colorScheme.secondary)
                    }
                    Text(
                        "${TechSpecialty.fromRaw(ticket.category).label} • Prioridad ${ticket.priority}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(ticket.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun openWhatsApp(context: Context, message: String) {
    val encoded = Uri.encode(message)
    val uri = Uri.parse("https://wa.me/${TqmConstants.WHATSAPP_DIGITS}?text=$encoded")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
