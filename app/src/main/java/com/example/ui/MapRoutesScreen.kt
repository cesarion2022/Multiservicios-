package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.IbagueGeo
import com.example.model.OrderStatus
import com.example.model.ServiceOrder
import com.example.model.TechSpecialty
import com.example.model.TechStatus
import com.example.model.Technician
import com.example.ui.theme.TqmAmber
import com.example.ui.theme.TqmCrimson
import com.example.ui.theme.TqmEmerald
import com.example.ui.theme.TqmSkyLight
import kotlin.math.hypot

@SuppressLint("MissingPermission")
private fun readLastKnownCoarseLocation(context: Context): Pair<Double, Double>? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) return null
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        loc?.let { it.latitude to it.longitude }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun MapRoutesScreen(
    technicians: List<Technician>,
    orders: List<ServiceOrder>,
    onAssignOrderToTech: (ServiceOrder, Technician) -> Unit,
    onUpdateTechStatus: (Technician, TechStatus, String?) -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedSpecialty by remember { mutableStateOf<TechSpecialty?>(null) }
    var selectedTech by remember { mutableStateOf<Technician?>(null) }
    var selectedOrder by remember { mutableStateOf<ServiceOrder?>(null) }
    var deviceLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val loc = readLastKnownCoarseLocation(context)
            if (loc != null) {
                deviceLocation = loc
                onPostMessage("Ubicación GPS del dispositivo sincronizada")
            } else {
                deviceLocation = 4.4389 to -75.2322
                onPostMessage("GPS activo • Centrado en Ibagué Centro")
            }
        } else {
            onPostMessage("Permiso de ubicación denegado; usando coordenadas de zonas de Ibagué")
        }
    }

    val filteredTechs = remember(technicians, selectedSpecialty) {
        if (selectedSpecialty == null) technicians
        else technicians.filter { it.specialties.contains(selectedSpecialty!!.name) }
    }
    val activeOrders = remember(orders, selectedSpecialty) {
        orders.filter {
            it.status != OrderStatus.COMPLETADA.name && it.status != OrderStatus.CANCELADA.name
        }.let { list ->
            if (selectedSpecialty == null) list
            else list.filter { it.specialty == selectedSpecialty!!.name }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Specialty filter bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSpecialty == null,
                onClick = { selectedSpecialty = null },
                label = { Text("Todas (${technicians.size})") },
                modifier = Modifier.testTag("map_filter_all")
            )
            TechSpecialty.entries.forEach { spec ->
                FilterChip(
                    selected = selectedSpecialty == spec,
                    onClick = {
                        selectedSpecialty = if (selectedSpecialty == spec) null else spec
                    },
                    label = { Text(spec.shortLabel) }
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IbagueInteractiveMapCard(
                        technicians = filteredTechs,
                        orders = activeOrders,
                        deviceLocation = deviceLocation,
                        selectedTech = selectedTech,
                        selectedOrder = selectedOrder,
                        onSelectTech = { selectedTech = it; selectedOrder = null },
                        onSelectOrder = { selectedOrder = it; selectedTech = null },
                        onRequestGps = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                deviceLocation = readLastKnownCoarseLocation(context) ?: (4.4389 to -75.2322)
                                onPostMessage("Ubicación en tiempo real actualizada")
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        modifier = Modifier
                            .weight(1.45f)
                            .fillMaxHeight()
                    )

                    MapSidePanel(
                        technicians = filteredTechs,
                        orders = activeOrders,
                        selectedTech = selectedTech,
                        selectedOrder = selectedOrder,
                        onSelectTech = { selectedTech = it; selectedOrder = null },
                        onSelectOrder = { selectedOrder = it; selectedTech = null },
                        onAssignOrderToTech = onAssignOrderToTech,
                        onUpdateTechStatus = onUpdateTechStatus,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IbagueInteractiveMapCard(
                        technicians = filteredTechs,
                        orders = activeOrders,
                        deviceLocation = deviceLocation,
                        selectedTech = selectedTech,
                        selectedOrder = selectedOrder,
                        onSelectTech = { selectedTech = it; selectedOrder = null },
                        onSelectOrder = { selectedOrder = it; selectedTech = null },
                        onRequestGps = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                deviceLocation = readLastKnownCoarseLocation(context) ?: (4.4389 to -75.2322)
                                onPostMessage("Ubicación en tiempo real actualizada")
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxWidth()
                    )

                    MapSidePanel(
                        technicians = filteredTechs,
                        orders = activeOrders,
                        selectedTech = selectedTech,
                        selectedOrder = selectedOrder,
                        onSelectTech = { selectedTech = it; selectedOrder = null },
                        onSelectOrder = { selectedOrder = it; selectedTech = null },
                        onAssignOrderToTech = onAssignOrderToTech,
                        onUpdateTechStatus = onUpdateTechStatus,
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun IbagueInteractiveMapCard(
    technicians: List<Technician>,
    orders: List<ServiceOrder>,
    deviceLocation: Pair<Double, Double>?,
    selectedTech: Technician?,
    selectedOrder: ServiceOrder?,
    onSelectTech: (Technician) -> Unit,
    onSelectOrder: (ServiceOrder) -> Unit,
    onRequestGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val roadPrimary = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            MaterialTheme.shapes.large
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.85f, 2.8f)
                            panOffset = Offset(
                                x = (panOffset.x + pan.x).coerceIn(-360f, 360f),
                                y = (panOffset.y + pan.y).coerceIn(-280f, 280f)
                            )
                        }
                    }
                    .pointerInput(technicians, orders, zoom, panOffset) {
                        detectTapGestures { tap ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val techHit = technicians.minByOrNull { t ->
                                val pt = geoToCanvas(t.latitude, t.longitude, w, h, zoom, panOffset)
                                hypot(pt.x - tap.x, pt.y - tap.y)
                            }
                            val techDist = techHit?.let {
                                val pt = geoToCanvas(it.latitude, it.longitude, w, h, zoom, panOffset)
                                hypot(pt.x - tap.x, pt.y - tap.y)
                            } ?: Float.MAX_VALUE

                            val orderHit = orders.minByOrNull { o ->
                                val pt = geoToCanvas(o.latitude, o.longitude, w, h, zoom, panOffset)
                                hypot(pt.x - tap.x, pt.y - tap.y)
                            }
                            val orderDist = orderHit?.let {
                                val pt = geoToCanvas(it.latitude, it.longitude, w, h, zoom, panOffset)
                                hypot(pt.x - tap.x, pt.y - tap.y)
                            } ?: Float.MAX_VALUE

                            if (techDist < 54f && techDist <= orderDist && techHit != null) {
                                onSelectTech(techHit)
                            } else if (orderDist < 54f && orderHit != null) {
                                onSelectOrder(orderHit)
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Coordinate Grid
                val step = 72f * zoom
                var gx = (panOffset.x % step)
                while (gx < w) {
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                    gx += step
                }
                var gy = (panOffset.y % step)
                while (gy < h) {
                    drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    gy += step
                }

                // Río Combeima contour
                val riverPath = Path().apply {
                    val p0 = geoToCanvas(4.462, -75.252, w, h, zoom, panOffset)
                    val p1 = geoToCanvas(4.436, -75.238, w, h, zoom, panOffset)
                    val p2 = geoToCanvas(4.412, -75.215, w, h, zoom, panOffset)
                    moveTo(p0.x, p0.y)
                    quadraticTo(p1.x, p1.y, p2.x, p2.y)
                }
                drawPath(riverPath, color = TqmSkyLight.copy(alpha = 0.25f), style = Stroke(width = 8f * zoom))

                // Main arterial corridors in Ibagué (Cra 5ta & Av. Mirolindo)
                val cra5Start = geoToCanvas(4.4389, -75.236, w, h, zoom, panOffset)
                val cra5Mid = geoToCanvas(4.4425, -75.203, w, h, zoom, panOffset)
                val cra5Salado = geoToCanvas(4.4585, -75.164, w, h, zoom, panOffset)
                drawLine(roadPrimary, cra5Start, cra5Mid, strokeWidth = 4f * zoom)
                drawLine(roadPrimary, cra5Mid, cra5Salado, strokeWidth = 3.5f * zoom)

                val mirolindoStart = geoToCanvas(4.438, -75.222, w, h, zoom, panOffset)
                val mirolindoEnd = geoToCanvas(4.415, -75.1745, w, h, zoom, panOffset)
                drawLine(roadPrimary, mirolindoStart, mirolindoEnd, strokeWidth = 3.5f * zoom)

                // Draw Zone Markers
                IbagueGeo.zones.forEach { zone ->
                    val pt = geoToCanvas(zone.latitude, zone.longitude, w, h, zoom, panOffset)
                    if (pt.x in 16f..(w - 16f) && pt.y in 16f..(h - 16f)) {
                        drawCircle(
                            color = labelColor.copy(alpha = 0.35f),
                            radius = 3.5f,
                            center = pt
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = zone.name.substringBefore(" /"),
                            topLeft = Offset(pt.x + 6f, pt.y - 8f),
                            style = TextStyle(color = labelColor.copy(alpha = 0.78f), fontSize = 10.sp)
                        )
                    }
                }

                // Draw Active Routes (Technician -> Assigned Order)
                orders.filter { it.assignedTechId.isNotBlank() }.forEach { order ->
                    val tech = technicians.firstOrNull { it.id == order.assignedTechId }
                    if (tech != null) {
                        val from = geoToCanvas(tech.latitude, tech.longitude, w, h, zoom, panOffset)
                        val to = geoToCanvas(order.latitude, order.longitude, w, h, zoom, panOffset)
                        drawLine(
                            color = TqmAmber,
                            start = from,
                            end = to,
                            strokeWidth = 3.5f * zoom,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    }
                }

                // Draw Device GPS Location if active
                deviceLocation?.let { (lat, lng) ->
                    val pt = geoToCanvas(lat, lng, w, h, zoom, panOffset)
                    drawCircle(TqmSkyLight.copy(alpha = 0.25f), radius = 22f, center = pt)
                    drawCircle(TqmSkyLight, radius = 8f, center = pt)
                }

                // Draw Service Order Pins
                orders.forEach { order ->
                    val pt = geoToCanvas(order.latitude, order.longitude, w, h, zoom, panOffset)
                    val isSel = selectedOrder?.id == order.id
                    val pinColor = when (order.status) {
                        OrderStatus.PENDIENTE.name -> TqmCrimson
                        OrderStatus.ASIGNADA.name, OrderStatus.EN_RUTA.name -> TqmAmber
                        else -> TqmEmerald
                    }
                    if (isSel) {
                        drawCircle(pinColor.copy(alpha = 0.28f), radius = 26f, center = pt)
                    }
                    drawCircle(Color.White, radius = 12f, center = pt)
                    drawCircle(pinColor, radius = 9f, center = pt)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = order.orderCode,
                        topLeft = Offset(pt.x - 20f, pt.y + 13f),
                        style = TextStyle(
                            color = pinColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Draw Technician Pins
                technicians.forEach { tech ->
                    val pt = geoToCanvas(tech.latitude, tech.longitude, w, h, zoom, panOffset)
                    val isSel = selectedTech?.id == tech.id
                    val pinColor = when (tech.status) {
                        TechStatus.DISPONIBLE.name -> TqmEmerald
                        TechStatus.EN_RUTA.name -> TqmAmber
                        TechStatus.EN_SERVICIO.name -> TqmSkyLight
                        else -> Color.Gray
                    }
                    if (isSel) {
                        drawCircle(pinColor.copy(alpha = 0.30f), radius = 28f, center = pt)
                    }
                    drawCircle(Color.White, radius = 14f, center = pt)
                    drawCircle(pinColor, radius = 11f, center = pt)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = tech.fullName.substringBefore(" "),
                        topLeft = Offset(pt.x - 18f, pt.y - 28f),
                        style = TextStyle(
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Top Map Header Badge
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TqmEmerald)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Ibagué en Vivo • ${technicians.size} técnicos • ${orders.size} órdenes",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Map Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = onRequestGps, modifier = Modifier.testTag("map_gps_button")) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = "Mi ubicación GPS")
                    }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(2.8f) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Acercar mapa")
                    }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.85f) }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Alejar mapa")
                    }
                }
            }

            // Legend
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(TqmEmerald, "Disponible")
                    LegendDot(TqmAmber, "En Ruta")
                    LegendDot(TqmCrimson, "Pendiente")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun geoToCanvas(
    lat: Double,
    lng: Double,
    width: Float,
    height: Float,
    zoom: Float,
    pan: Offset
): Offset {
    val normX = ((lng - IbagueGeo.MIN_LNG) / (IbagueGeo.MAX_LNG - IbagueGeo.MIN_LNG)).toFloat()
    val normY = (1.0 - (lat - IbagueGeo.MIN_LAT) / (IbagueGeo.MAX_LAT - IbagueGeo.MIN_LAT)).toFloat()
    val cx = width / 2f
    val cy = height / 2f
    val rawX = 40f + normX * (width - 80f)
    val rawY = 48f + normY * (height - 96f)
    return Offset(
        x = cx + (rawX - cx) * zoom + pan.x,
        y = cy + (rawY - cy) * zoom + pan.y
    )
}

@Composable
private fun MapSidePanel(
    technicians: List<Technician>,
    orders: List<ServiceOrder>,
    selectedTech: Technician?,
    selectedOrder: ServiceOrder?,
    onSelectTech: (Technician) -> Unit,
    onSelectOrder: (ServiceOrder) -> Unit,
    onAssignOrderToTech: (ServiceOrder, Technician) -> Unit,
    onUpdateTechStatus: (Technician, TechStatus, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            MaterialTheme.shapes.large
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedTech != null) {
                item {
                    SelectedTechCard(
                        tech = selectedTech,
                        pendingOrders = orders.filter { it.status == OrderStatus.PENDIENTE.name },
                        onAssignOrder = { order -> onAssignOrderToTech(order, selectedTech) },
                        onCycleStatus = { nextStatus -> onUpdateTechStatus(selectedTech, nextStatus, null) }
                    )
                }
            } else if (selectedOrder != null) {
                item {
                    SelectedOrderCard(
                        order = selectedOrder,
                        technicians = technicians,
                        onAssign = { tech -> onAssignOrderToTech(selectedOrder, tech) }
                    )
                }
            }

            item {
                Text(
                    text = "Técnicos Activos en Ruta (${technicians.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (technicians.isEmpty()) {
                item {
                    Text(
                        text = "Registra técnicos en la pestaña Operación para visualizar su ubicación, hora de salida y rutas en el mapa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(technicians, key = { it.id }) { tech ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onSelectTech(tech) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Engineering,
                            contentDescription = tech.fullName,
                            tint = when (tech.status) {
                                TechStatus.DISPONIBLE.name -> TqmEmerald
                                TechStatus.EN_RUTA.name -> TqmAmber
                                else -> TqmSkyLight
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tech.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${tech.barrio} • Salida ${tech.departureTime} • ${TechStatus.fromRaw(tech.status).label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (orders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Servicios en Curso / Pendientes (${orders.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                items(orders, key = { it.id }) { order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable { onSelectOrder(order) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = order.orderCode,
                            tint = TqmAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${order.orderCode} • ${TechSpecialty.fromRaw(order.specialty).label}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${order.barrio} • ${order.clientName} (${OrderStatus.fromRaw(order.status).label})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedTechCard(
    tech: Technician,
    pendingOrders: List<ServiceOrder>,
    onAssignOrder: (ServiceOrder) -> Unit,
    onCycleStatus: (TechStatus) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = tech.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Especialidades: ${tech.specialties.joinToString { TechSpecialty.fromRaw(it).label }}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Salida: ${tech.departureTime} | Turno: ${tech.workSchedule}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (tech.knowledgeNotes.isNotBlank()) {
                Text(
                    text = "Conocimientos: ${tech.knowledgeNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val nextStatus = when (TechStatus.fromRaw(tech.status)) {
                    TechStatus.DISPONIBLE -> TechStatus.EN_RUTA
                    TechStatus.EN_RUTA -> TechStatus.EN_SERVICIO
                    TechStatus.EN_SERVICIO -> TechStatus.DISPONIBLE
                    TechStatus.FUERA_DE_TURNO -> TechStatus.DISPONIBLE
                }
                OutlinedButton(onClick = { onCycleStatus(nextStatus) }) {
                    Text("Marcar ${nextStatus.label}")
                }
            }
            val matchingOrder = pendingOrders.firstOrNull { tech.specialties.contains(it.specialty) }
                ?: pendingOrders.firstOrNull()
            if (matchingOrder != null) {
                val dist = IbagueGeo.distanceKm(tech.latitude, tech.longitude, matchingOrder.latitude, matchingOrder.longitude)
                val eta = IbagueGeo.estimateMinutes(dist)
                Button(
                    onClick = { onAssignOrder(matchingOrder) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Asignar ${matchingOrder.orderCode} (~$eta min)")
                }
            }
        }
    }
}

@Composable
private fun SelectedOrderCard(
    order: ServiceOrder,
    technicians: List<Technician>,
    onAssign: (Technician) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${order.orderCode} • ${TechSpecialty.fromRaw(order.specialty).label}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Cliente: ${order.clientName} • ${order.address} (${order.barrio})",
                style = MaterialTheme.typography.bodySmall
            )
            val bestTech = technicians
                .filter { it.status == TechStatus.DISPONIBLE.name || it.specialties.contains(order.specialty) }
                .minByOrNull { tech ->
                    val penalty = if (tech.specialties.contains(order.specialty)) 0.0 else 25.0
                    IbagueGeo.distanceKm(tech.latitude, tech.longitude, order.latitude, order.longitude) + penalty
                }
            if (bestTech != null && order.assignedTechId.isBlank()) {
                val dist = IbagueGeo.distanceKm(bestTech.latitude, bestTech.longitude, order.latitude, order.longitude)
                Button(
                    onClick = { onAssign(bestTech) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Despachar a ${bestTech.fullName} (${"%.1f".format(dist)} km)")
                }
            }
        }
    }
}
