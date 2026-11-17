package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.IbagueGeo
import com.example.model.OrderPriority
import com.example.model.OrderStatus
import com.example.model.PaymentMethod
import com.example.model.ServiceOrder
import com.example.model.TechSpecialty
import com.example.model.TechStatus
import com.example.model.Technician
import com.example.ui.theme.TqmAmber
import com.example.ui.theme.TqmCrimson
import com.example.ui.theme.TqmEmerald
import com.example.util.ReportExporter

@Composable
fun OperationsScreen(
    technicians: List<Technician>,
    orders: List<ServiceOrder>,
    onSaveTechnician: (Technician, () -> Unit) -> Unit,
    onDeleteTechnician: (String) -> Unit,
    onCreateOrder: (ServiceOrder, Technician?, () -> Unit) -> Unit,
    onAssignOrder: (ServiceOrder, Technician) -> Unit,
    onAdvanceOrderStatus: (ServiceOrder, OrderStatus, String?) -> Unit
) {
    var segment by remember { mutableIntStateOf(0) }
    var editingTech by remember { mutableStateOf<Technician?>(null) }
    var showTechDialog by remember { mutableStateOf(false) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var assignTargetOrder by remember { mutableStateOf<ServiceOrder?>(null) }
    var completeTargetOrder by remember { mutableStateOf<ServiceOrder?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.testTag("tab_technicians_segment")
                ) {
                    Text("Técnicos (${technicians.size})")
                }
                SegmentedButton(
                    selected = segment == 1,
                    onClick = { segment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("tab_orders_segment")
                ) {
                    Text("Órdenes (${orders.size})")
                }
            }

            if (segment == 0) {
                TechniciansListView(
                    technicians = technicians,
                    onEdit = { tech ->
                        editingTech = tech
                        showTechDialog = true
                    },
                    onDelete = onDeleteTechnician,
                    onRegisterNew = {
                        editingTech = null
                        showTechDialog = true
                    }
                )
            } else {
                OrdersListView(
                    orders = orders,
                    onAssignClick = { assignTargetOrder = it },
                    onAdvanceStatus = { order, next ->
                        if (next == OrderStatus.COMPLETADA) {
                            completeTargetOrder = order
                        } else {
                            onAdvanceOrderStatus(order, next, null)
                        }
                    },
                    onNewOrder = { showOrderDialog = true }
                )
            }
        }

        FloatingActionButton(
            onClick = {
                if (segment == 0) {
                    editingTech = null
                    showTechDialog = true
                } else {
                    showOrderDialog = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag(if (segment == 0) "fab_add_technician" else "fab_add_order")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = if (segment == 0) "Registrar técnico" else "Nueva orden"
            )
        }
    }

    if (showTechDialog) {
        TechnicianFormDialog(
            initial = editingTech,
            onDismiss = { showTechDialog = false },
            onSave = { tech ->
                onSaveTechnician(tech) { showTechDialog = false }
            }
        )
    }

    if (showOrderDialog) {
        OrderFormDialog(
            technicians = technicians,
            onDismiss = { showOrderDialog = false },
            onCreate = { order, autoTech ->
                onCreateOrder(order, autoTech) { showOrderDialog = false }
            }
        )
    }

    assignTargetOrder?.let { order ->
        AssignTechnicianDialog(
            order = order,
            technicians = technicians,
            onDismiss = { assignTargetOrder = null },
            onAssign = { tech ->
                onAssignOrder(order, tech)
                assignTargetOrder = null
            }
        )
    }

    completeTargetOrder?.let { order ->
        CompleteOrderReportDialog(
            order = order,
            onDismiss = { completeTargetOrder = null },
            onComplete = { notes ->
                onAdvanceOrderStatus(order, OrderStatus.COMPLETADA, notes)
                completeTargetOrder = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechniciansListView(
    technicians: List<Technician>,
    onEdit: (Technician) -> Unit,
    onDelete: (String) -> Unit,
    onRegisterNew: () -> Unit
) {
    var filterSpec by remember { mutableStateOf<TechSpecialty?>(null) }
    val filtered = remember(technicians, filterSpec) {
        if (filterSpec == null) technicians
        else technicians.filter { it.specialties.contains(filterSpec!!.name) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterSpec == null,
                onClick = { filterSpec = null },
                label = { Text("Todos") }
            )
            TechSpecialty.entries.forEach { spec ->
                FilterChip(
                    selected = filterSpec == spec,
                    onClick = { filterSpec = if (filterSpec == spec) null else spec },
                    label = { Text(spec.shortLabel) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Engineering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text("Sin técnicos registrados", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Registra electricistas, cerrajeros, plomeros, técnicos de A/C, lavadoras, neveras, computadores o soldadores con su hora de salida y conocimientos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRegisterNew) {
                        Text("Registrar primer técnico")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { tech ->
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
                                    Text(text = tech.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "C.C. ${tech.documentNumber} • ${tech.barrio}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    label = TechStatus.fromRaw(tech.status).label,
                                    color = when (tech.status) {
                                        TechStatus.DISPONIBLE.name -> TqmEmerald
                                        TechStatus.EN_RUTA.name -> TqmAmber
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tech.specialties.forEach { raw ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = TechSpecialty.fromRaw(raw).label,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Salida: ${tech.departureTime} | ${tech.workSchedule}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(text = tech.phone, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (tech.knowledgeNotes.isNotBlank()) {
                                Text(
                                    text = "Conocimientos: ${tech.knowledgeNotes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { onEdit(tech) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar técnico")
                                }
                                IconButton(onClick = { onDelete(tech.id) }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Eliminar técnico",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersListView(
    orders: List<ServiceOrder>,
    onAssignClick: (ServiceOrder) -> Unit,
    onAdvanceStatus: (ServiceOrder, OrderStatus) -> Unit,
    onNewOrder: () -> Unit
) {
    if (orders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AssignmentInd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(56.dp)
                )
                Text("No hay órdenes de servicio", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Crea una orden para asignar al técnico más cercano, enviar notificación push automática y generar reporte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onNewOrder) {
                    Text("Crear primera orden")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(orders, key = { it.id }) { order ->
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
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${order.clientName} (${order.clientPhone})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                label = OrderStatus.fromRaw(order.status).label,
                                color = when (order.status) {
                                    OrderStatus.PENDIENTE.name -> TqmCrimson
                                    OrderStatus.ASIGNADA.name, OrderStatus.EN_RUTA.name -> TqmAmber
                                    OrderStatus.COMPLETADA.name -> TqmEmerald
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        Text(
                            text = "${order.address} • ${order.barrio} | Valor: ${ReportExporter.formatCop(order.estimatedCostCop)}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (order.assignedTechName.isNotBlank()) {
                            Text(
                                text = "Técnico asignado: ${order.assignedTechName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (order.description.isNotBlank()) {
                            Text(
                                text = order.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (order.status == OrderStatus.PENDIENTE.name) {
                                Button(
                                    onClick = { onAssignClick(order) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Asignar Técnico")
                                }
                            } else if (order.status == OrderStatus.ASIGNADA.name) {
                                OutlinedButton(
                                    onClick = { onAdvanceStatus(order, OrderStatus.EN_RUTA) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Iniciar Ruta")
                                }
                            } else if (order.status == OrderStatus.EN_RUTA.name || order.status == OrderStatus.EN_PROGRESO.name) {
                                Button(
                                    onClick = { onAdvanceStatus(order, OrderStatus.COMPLETADA) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Completar + Reporte")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechnicianFormDialog(
    initial: Technician?,
    onDismiss: () -> Unit,
    onSave: (Technician) -> Unit
) {
    var fullName by remember { mutableStateOf(initial?.fullName.orEmpty()) }
    var docNumber by remember { mutableStateOf(initial?.documentNumber.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var selectedZone by remember {
        mutableStateOf(
            IbagueGeo.zones.firstOrNull { it.name == initial?.barrio } ?: IbagueGeo.zones.first()
        )
    }
    var zoneMenuExpanded by remember { mutableStateOf(false) }
    var departureTime by remember { mutableStateOf(initial?.departureTime ?: "07:30") }
    var workSchedule by remember { mutableStateOf(initial?.workSchedule ?: "07:30 - 17:30 L-S") }
    var knowledge by remember { mutableStateOf(initial?.knowledgeNotes.orEmpty()) }
    val selectedSpecs = remember {
        mutableStateListOf<TechSpecialty>().apply {
            if (initial != null && initial.specialties.isNotEmpty()) {
                addAll(initial.specialties.map { TechSpecialty.fromRaw(it) })
            } else {
                add(TechSpecialty.ELECTRICISTA)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Registrar Técnico" else "Editar Técnico") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tech_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = docNumber,
                        onValueChange = { docNumber = it },
                        label = { Text("Cédula *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección residencia / base") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    OutlinedButton(
                        onClick = { zoneMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zona Base Ibagué: ${selectedZone.name} (${selectedZone.comuna})")
                    }
                    DropdownMenu(
                        expanded = zoneMenuExpanded,
                        onDismissRequest = { zoneMenuExpanded = false }
                    ) {
                        IbagueGeo.zones.forEach { zone ->
                            DropdownMenuItem(
                                text = { Text("${zone.name} (${zone.comuna})") },
                                onClick = {
                                    selectedZone = zone
                                    zoneMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = departureTime,
                        onValueChange = { departureTime = it },
                        label = { Text("Hora salida *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = workSchedule,
                        onValueChange = { workSchedule = it },
                        label = { Text("Horario trabajo *") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                }

                Text("Especialidades (9 oficios):", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TechSpecialty.entries.forEach { spec ->
                        val active = selectedSpecs.contains(spec)
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active && selectedSpecs.size > 1) selectedSpecs.remove(spec)
                                else if (!active) selectedSpecs.add(spec)
                            },
                            label = { Text(spec.shortLabel) }
                        )
                    }
                }

                OutlinedTextField(
                    value = knowledge,
                    onValueChange = { knowledge = it },
                    label = { Text("Conocimientos y certificaciones") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tech = (initial ?: Technician()).copy(
                        fullName = fullName,
                        documentNumber = docNumber,
                        phone = phone,
                        email = email,
                        address = address,
                        barrio = selectedZone.name,
                        latitude = selectedZone.latitude,
                        longitude = selectedZone.longitude,
                        departureTime = departureTime,
                        workSchedule = workSchedule,
                        knowledgeNotes = knowledge,
                        specialties = selectedSpecs.map { it.name }
                    )
                    onSave(tech)
                },
                enabled = fullName.trim().length >= 2 && docNumber.trim().length >= 3 && phone.trim().length >= 5,
                modifier = Modifier.testTag("tech_save_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun OrderFormDialog(
    technicians: List<Technician>,
    onDismiss: () -> Unit,
    onCreate: (ServiceOrder, Technician?) -> Unit
) {
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var selectedZone by remember { mutableStateOf(IbagueGeo.zones.first()) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var specialty by remember { mutableStateOf(TechSpecialty.ELECTRICISTA) }
    var specExpanded by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(OrderPriority.NORMAL) }
    var costText by remember { mutableStateOf("120000") }
    var scheduledTime by remember { mutableStateOf("14:00") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.NEQUI) }
    var description by remember { mutableStateOf("") }
    var autoAssign by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Orden de Servicio") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Nombre del cliente *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("order_client_input")
                )
                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it },
                    label = { Text("Teléfono cliente *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección en Ibagué *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { zoneExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Barrio / Comuna: ${selectedZone.name}")
                    }
                    DropdownMenu(expanded = zoneExpanded, onDismissRequest = { zoneExpanded = false }) {
                        IbagueGeo.zones.forEach { z ->
                            DropdownMenuItem(
                                text = { Text("${z.name} (${z.comuna})") },
                                onClick = { selectedZone = z; zoneExpanded = false }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { specExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Especialidad: ${specialty.label}")
                    }
                    DropdownMenu(expanded = specExpanded, onDismissRequest = { specExpanded = false }) {
                        TechSpecialty.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = { specialty = s; specExpanded = false }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { if (it.all(Char::isDigit)) costText = it },
                        label = { Text("Valor (COP)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = scheduledTime,
                        onValueChange = { scheduledTime = it },
                        label = { Text("Hora cita") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OrderPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(PaymentMethod.NEQUI, PaymentMethod.BANCOLOMBIA, PaymentMethod.EFECTIVO).forEach { m ->
                        FilterChip(
                            selected = paymentMethod == m,
                            onClick = { paymentMethod = m },
                            label = { Text(m.label.substringBefore(" ")) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del trabajo") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoAssign, onCheckedChange = { autoAssign = it })
                    Text("Auto-asignar técnico más cercano + Push", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bestTech = if (autoAssign) {
                        technicians
                            .filter { it.status == TechStatus.DISPONIBLE.name || it.specialties.contains(specialty.name) }
                            .minByOrNull { tech ->
                                val penalty = if (tech.specialties.contains(specialty.name)) 0.0 else 30.0
                                IbagueGeo.distanceKm(tech.latitude, tech.longitude, selectedZone.latitude, selectedZone.longitude) + penalty
                            }
                    } else null
                    val order = ServiceOrder(
                        orderCode = "TQM-${(1000..9999).random()}",
                        clientName = clientName,
                        clientPhone = clientPhone,
                        address = address,
                        barrio = selectedZone.name,
                        latitude = selectedZone.latitude,
                        longitude = selectedZone.longitude,
                        specialty = specialty.name,
                        priority = priority.name,
                        scheduledTime = scheduledTime,
                        estimatedCostCop = costText.toLongOrNull() ?: 0L,
                        paymentMethod = paymentMethod.name,
                        description = description
                    )
                    onCreate(order, bestTech)
                },
                enabled = clientName.trim().length >= 2 && clientPhone.trim().length >= 5 && address.trim().length >= 3,
                modifier = Modifier.testTag("order_create_button")
            ) {
                Text("Crear Orden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AssignTechnicianDialog(
    order: ServiceOrder,
    technicians: List<Technician>,
    onDismiss: () -> Unit,
    onAssign: (Technician) -> Unit
) {
    val sorted = remember(technicians, order) {
        technicians.sortedBy { tech ->
            val matchPenalty = if (tech.specialties.contains(order.specialty)) 0.0 else 25.0
            val busyPenalty = if (tech.status == TechStatus.DISPONIBLE.name) 0.0 else 10.0
            IbagueGeo.distanceKm(tech.latitude, tech.longitude, order.latitude, order.longitude) + matchPenalty + busyPenalty
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asignar ${order.orderCode}") },
        text = {
            if (sorted.isEmpty()) {
                Text("No hay técnicos registrados aún. Registra un técnico primero.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sorted.forEach { tech ->
                        val dist = IbagueGeo.distanceKm(tech.latitude, tech.longitude, order.latitude, order.longitude)
                        val eta = IbagueGeo.estimateMinutes(dist)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAssign(tech) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(tech.fullName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${tech.barrio} • ${"%.1f".format(dist)} km (~$eta min) • ${TechStatus.fromRaw(tech.status).label}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun CompleteOrderReportDialog(
    order: ServiceOrder,
    onDismiss: () -> Unit,
    onComplete: (String) -> Unit
) {
    var notes by remember { mutableStateOf(order.technicianNotes.ifBlank { "Servicio ejecutado satisfactoriamente según protocolo técnico T.Q.M." }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Completar ${order.orderCode} + Reporte") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas del reporte automático") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onComplete(notes) }) {
                Text("Finalizar y Generar Reporte")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
