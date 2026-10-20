// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package ca.cyberengma.openodo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ca.cyberengma.openodo.core.fuel.FuelStats
import ca.cyberengma.openodo.core.fuel.FuelStatsResult
import ca.cyberengma.openodo.core.fuel.Economy
import ca.cyberengma.openodo.core.fuel.PriceStats
import ca.cyberengma.openodo.core.model.*
import ca.cyberengma.openodo.core.money.Money
import ca.cyberengma.openodo.core.money.CurrencyMinorDigits
import ca.cyberengma.openodo.core.portability.ImportResult
import ca.cyberengma.openodo.core.reminders.DueCalculator
import ca.cyberengma.openodo.core.reminders.ReminderState
import ca.cyberengma.openodo.core.reminders.ReminderStatus
import ca.cyberengma.openodo.core.units.*
import ca.cyberengma.openodo.core.validation.ValidationResult
import ca.cyberengma.openodo.core.validation.Validators
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import ca.cyberengma.openodo.ui.util.Jalali
import ca.cyberengma.openodo.ui.util.JalaliDatePickerDialog
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    VEHICLES("vehicles", "خودروها", Icons.Outlined.DirectionsCar),
    DASHBOARD("dashboard", "داشبورد", Icons.Outlined.Speed),
    RECORDS("records", "سوابق", Icons.Outlined.ReceiptLong),
    REMINDERS("reminders", "یادآورها", Icons.Outlined.Notifications),
    SETTINGS("settings", "تنظیمات", Icons.Outlined.Settings),
    STATS("statistics", "آمار", Icons.Outlined.BarChart),
    DATA("portability", "داده", Icons.Outlined.Storage),
    TYPES("record-types", "انواع", Icons.Outlined.Category),
}

internal fun initialAppRoute(hasVehicles: Boolean): String =
    if (hasVehicles) Destination.DASHBOARD.route else Destination.VEHICLES.route

@Composable
fun OpenOdoApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    if (!state.isLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val navController = rememberNavController()
    val startRoute = initialAppRoute(state.vehicles.isNotEmpty())
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: startRoute
    var lastBack by remember { mutableStateOf(0L) }
    var previousEmpty by remember { mutableStateOf(state.vehicles.isEmpty()) }

    LaunchedEffect(state.vehicles.isEmpty()) {
        if (previousEmpty && state.vehicles.isNotEmpty() && currentRoute == Destination.VEHICLES.route) {
            navController.navigate(Destination.DASHBOARD.route) {
                popUpTo(Destination.VEHICLES.route) { inclusive = true }
            }
        }
        previousEmpty = state.vehicles.isEmpty()
    }

    BackHandler(enabled = currentRoute == Destination.DASHBOARD.route || (currentRoute == Destination.VEHICLES.route && state.vehicles.isEmpty())) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) (context as? Activity)?.finish()
        else { lastBack = now; Toast.makeText(context, "برای خروج دوباره بازگشت را بزنید", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val primary = listOf(Destination.DASHBOARD, Destination.RECORDS, Destination.STATS, Destination.REMINDERS, Destination.SETTINGS)
            if (primary.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    primary.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (item == Destination.DASHBOARD) navController.popBackStack(Destination.DASHBOARD.route, inclusive = false)
                                else navController.navigate(item.route) { launchSingleTop = true }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = startRoute, modifier = Modifier.padding(padding)) {
            composable(Destination.VEHICLES.route) { VehiclesScreen(state, viewModel, Modifier) }
            composable(Destination.DASHBOARD.route) {
                DashboardScreen(state, viewModel, Modifier, { navController.navigate("fuel/new") }, { navController.navigate("expense/new") }, { navController.navigate("reminder/new") }, { id -> navController.navigate("reminder/edit/$id") })
            }
            composable(Destination.RECORDS.route) { RecordsScreen(state, viewModel, Modifier, null) {} }
            composable(Destination.REMINDERS.route) { RemindersScreen(state, viewModel, Modifier, false) {} }
            composable(Destination.SETTINGS.route) { SettingsScreen(state, viewModel, Modifier, { navController.navigate(Destination.VEHICLES.route) }, { navController.navigate(Destination.DATA.route) }, { navController.navigate(Destination.TYPES.route) }) }
            composable(Destination.STATS.route) { StatisticsScreen(state, viewModel, Modifier) }
            composable(Destination.DATA.route) { PortabilityScreen(viewModel, Modifier) }
            composable(Destination.TYPES.route) { RecordTypesScreen(viewModel, Modifier) }
            composable("fuel/new") {
                state.vehicles.firstOrNull { it.id == state.activeVehicleId }?.let { vehicle ->
                    FuelForm(vehicle, viewModel, { viewModel.saveFuel(it); navController.popBackStack() }, { navController.popBackStack() }, Modifier)
                }
            }
            composable("expense/new") {
                state.vehicles.firstOrNull { it.id == state.activeVehicleId }?.let { vehicle ->
                    ExpenseForm(vehicle, viewModel, { viewModel.saveExpense(it); navController.popBackStack() }, { navController.popBackStack() }, Modifier)
                }
            }
            composable("reminder/new") {
                state.vehicles.firstOrNull { it.id == state.activeVehicleId }?.let { vehicle ->
                    ReminderForm(vehicle, viewModel, { viewModel.saveReminder(it); navController.popBackStack() }, { navController.popBackStack() }, Modifier)
                }
            }
            composable("reminder/edit/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
                state.vehicles.firstOrNull { it.id == state.activeVehicleId }?.let { vehicle ->
                    val reminder = viewModel.reminders(vehicle.id).collectAsState(initial = emptyList()).value.firstOrNull { it.id == id }
                    if (reminder != null) {
                        ReminderForm(vehicle, viewModel, { viewModel.saveReminder(it); navController.popBackStack() }, { navController.popBackStack() }, Modifier, reminder)
                    }
                }
            }
        }
    }
}

// ---------- Shared components ----------

@Composable
private fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String = "اُپن‌اُدو",
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        content()
    }
}

@Composable
private fun StatusPill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConfigPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChartCard(title: String, message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, onClick: (() -> Unit)? = null) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- Vehicles ----------

@Composable
private fun FormTopBar(title: String, onClose: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "بستن") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Button(onClick = onSave) { Text("ذخیره") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun rememberDiscardRequest(dirty: Boolean, discard: () -> Unit): () -> Unit {
    var confirm by remember { mutableStateOf(false) }
    val request: () -> Unit = { if (dirty) confirm = true else discard() }
    BackHandler { request() }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("تغییرات ذخیره نشده؟") },
            text = { Text("تغییرات ذخیره نشده از بین می‌روند.") },
            confirmButton = { TextButton(onClick = { confirm = false; discard() }) { Text("صرف‌نظر", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("ادامه ویرایش") } },
        )
    }
    return request
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun VehiclesScreen(state: ShellState, viewModel: AppViewModel, modifier: Modifier) {
    var form by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Vehicle?>(null) }
    var deleteCandidate by remember { mutableStateOf<Vehicle?>(null) }
    if (form || editing != null) {
        VehicleForm({ viewModel.save(it); form = false; editing = null }, { form = false; editing = null }, modifier, editing)
        return
    }
    ScreenHeader("خودروها", modifier, actions = {
        TextButton(onClick = { form = true }) { Icon(Icons.Filled.Add, contentDescription = "افزودن خودرو"); Spacer(Modifier.width(4.dp)); Text("افزودن") }
    }) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.vehicles.isEmpty()) {
                item { EmptyState("گاراژ شما خالی است", "اولین خودرو را اضافه کنید تا گزارش خصوصی و آفلاین شروع شود.", "افزودن خودرو") { form = true } }
            }
            items(state.vehicles, key = { "active-${it.id}" }) { v ->
                VehicleCard(v, state.activeVehicleId == v.id, { viewModel.select(v.id) }, { editing = v }, { deleteCandidate = v })
            }
            if (state.archivedVehicles.isNotEmpty()) {
                item { Text("بایگانی‌شده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.archivedVehicles, key = { "archived-${it.id}" }) { VehicleCard(it, false, {}, { editing = it }, { deleteCandidate = it }) }
            }
        }
    }
    deleteCandidate?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("حذف دائمی ${vehicle.name}؟") },
            text = { Text("این کار خودرو، سوخت، هزینه‌ها و یادآورها را برای همیشه حذف می‌کند و قابل بازگشت نیست.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteVehicle(vehicle); deleteCandidate = null }) { Text("حذف دائمی", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun VehicleCard(v: Vehicle, active: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (active) StatusPill("فعال", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("${v.make} ${v.model} • ${v.year}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConfigPill(distanceLabel(v.distanceUnit))
                ConfigPill(volumeLabel(v.volumeUnit))
                ConfigPill(energyLabel(v.energyUnit))
                ConfigPill(v.currency)
            }
            Row {
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("ویرایش") }
                TextButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "حذف", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp)); Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun VehicleForm(save: (Vehicle) -> Unit, cancel: () -> Unit, modifier: Modifier, initial: Vehicle? = null) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var make by remember(initial) { mutableStateOf(initial?.make ?: "") }
    var model by remember(initial) { mutableStateOf(initial?.model ?: "") }
    var currency by remember(initial) { mutableStateOf(initial?.currency ?: "USD") }
    var distance by remember(initial) { mutableStateOf(initial?.distanceUnit ?: DistanceUnit.KILOMETRES) }
    var volume by remember(initial) { mutableStateOf(initial?.volumeUnit ?: VolumeUnit.LITRES) }
    var energy by remember(initial) { mutableStateOf(initial?.energyUnit ?: EnergyUnit.KILOWATT_HOURS) }
    var fuelType by remember(initial) { mutableStateOf(initial?.fuelType ?: FuelType.FUEL) }
    val dirty = name != (initial?.name ?: "") || make != (initial?.make ?: "") || model != (initial?.model ?: "") || currency != (initial?.currency ?: "USD") || distance != (initial?.distanceUnit ?: DistanceUnit.KILOMETRES) || volume != (initial?.volumeUnit ?: VolumeUnit.LITRES) || energy != (initial?.energyUnit ?: EnergyUnit.KILOWATT_HOURS) || fuelType != (initial?.fuelType ?: FuelType.FUEL)
    val requestCancel = rememberDiscardRequest(dirty, cancel)

    ScreenHeader(if (initial == null) "افزودن خودرو" else "ویرایش خودرو", modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { TextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("نام خودرو") }, singleLine = true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(make, { make = it }, Modifier.weight(1f), label = { Text("سازنده") }, singleLine = true)
                    TextField(model, { model = it }, Modifier.weight(1f), label = { Text("مدل") }, singleLine = true)
                }
            }
            item { Text("نوع سوخت", style = MaterialTheme.typography.titleSmall) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FuelType.entries.forEach { FilterChip(fuelType == it, { fuelType = it }, label = { Text(fuelTypeLabel(it)) }) } } }
            item { Text("واحد مسافت", style = MaterialTheme.typography.titleSmall) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DistanceUnit.entries.forEach { FilterChip(distance == it, { distance = it }, label = { Text(distanceLabel(it)) }) } } }
            if (fuelType != FuelType.ELECTRIC) {
                item { Text("واحد حجم", style = MaterialTheme.typography.titleSmall) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VolumeUnit.entries.forEach { FilterChip(volume == it, { volume = it }, label = { Text(volumeLabel(it)) }) } } }
            }
            if (fuelType != FuelType.FUEL) {
                item { Text("واحد انرژی", style = MaterialTheme.typography.titleSmall) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EnergyUnit.entries.forEach { FilterChip(energy == it, { energy = it }, label = { Text(energyLabel(it)) }) } } }
            }
            item { TextField(currency, { currency = it.uppercase().take(3) }, Modifier.fillMaxWidth(), label = { Text("ارز (کد ISO)") }, singleLine = true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = requestCancel, Modifier.weight(1f)) { Text("انصراف") }
                    Button(
                        onClick = {
                            save(Vehicle(
                                id = initial?.id ?: 0, name = name, make = make, model = model,
                                year = initial?.year ?: 2024, vin = initial?.vin, photoFileName = initial?.photoFileName,
                                distanceUnit = distance, volumeUnit = volume, energyUnit = energy,
                                currency = currency.ifBlank { "USD" }, fuelType = fuelType,
                                manualOdometer = initial?.manualOdometer,
                                manualOdometerAt = initial?.manualOdometerAt, isArchived = initial?.isArchived ?: false,
                                notes = initial?.notes, createdAt = initial?.createdAt ?: 0, updatedAt = System.currentTimeMillis(),
                            ))
                        },
                        Modifier.weight(1f),
                        enabled = name.isNotBlank(),
                    ) { Text("ذخیره") }
                }
            }
        }
    }
}

// ---------- Dashboard ----------

@Composable
private fun DashboardScreen(
    state: ShellState,
    viewModel: AppViewModel,
    modifier: Modifier,
    onFuel: () -> Unit,
    onExpense: () -> Unit,
    onReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
) {
    val v = state.vehicles.firstOrNull { it.id == state.activeVehicleId }
    ScreenHeader(v?.name ?: "داشبورد", modifier, eyebrow = "داشبورد") {
        if (v == null) {
            Column(Modifier.padding(20.dp)) { EmptyState("اولین خودرو را تنظیم کنید", "اُپن‌اُدو گزارش شما را روی همین دستگاه نگه می‌دارد.", "رفتن به خودروها") {} }
            return@ScreenHeader
        }
        val fuels = viewModel.fuelEntries(v.id).collectAsState(initial = emptyList()).value
        val expenses = viewModel.expenseRecords(v.id).collectAsState(initial = emptyList()).value
        val reminders = viewModel.reminders(v.id).collectAsState(initial = emptyList()).value
        val typeNames = viewModel.recordTypes().collectAsState(initial = emptyList()).value.associate { it.id to it.name }
        val currentOdometer = currentOdometer(v, fuels, expenses)
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { HeroCard(v, currentOdometer) }
            item { QuickActions(onFuel, onExpense, onReminder) }
            item { Text("یادآورهای فعال", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (reminders.isEmpty()) item { EmptyState("هنوز یادآوری نیست", "یک بازه سرویس اضافه کنید تا به‌موقع باشید.", "افزودن یادآور", onReminder) }
            items(reminders.take(3), key = { "reminder-${it.id}" }) { ReminderCard(it, DueCalculator.evaluate(it, currentOdometer, LocalDate.now()), typeNames[it.typeId] ?: "سرویس", v, currentOdometer, viewModel, { onEditReminder(it.id) }) }
            item { Text("سوخت اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (fuels.isEmpty()) item { EmptyState("سابقه سوخت نیست", "اولین سوخت‌گیری یا شارژ را ثبت کنید.", "افزودن سوخت", onFuel) }
            items(fuels.take(3), key = { "fuel-${it.id}" }) { fuel -> RecentFuelRow(fuel, v) }
        }
    }
}

@Composable
private fun HeroCard(v: Vehicle, currentOdometer: Metres?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("قرائت فعلی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            Text(currentOdometer?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, Locale.getDefault()) } ?: "—", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text(distanceLabel(v.distanceUnit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        }
    }
}

private fun currentOdometer(vehicle: Vehicle, fuels: List<FuelEntry>, expenses: List<ExpenseRecord>): Metres? {
    val readings = (fuels.map { RecordedOdometer(it.date, it.odometer) } + expenses.map { RecordedOdometer(it.date, it.odometer) })
        .filter { it.odometer.value >= 0 }
    return when (val result = CurrentOdometer.of(vehicle, readings)) {
        is CurrentOdometerResult.Value -> result.odometer
        CurrentOdometerResult.Empty -> null
    }
}

@Composable
private fun QuickActions(onFuel: () -> Unit, onExpense: () -> Unit, onReminder: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile(Icons.Filled.LocalGasStation, "افزودن سوخت", onFuel, Modifier.weight(1f))
        ActionTile(Icons.Filled.Build, "هزینه", onExpense, Modifier.weight(1f))
        ActionTile(Icons.Filled.Alarm, "یادآور", onReminder, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---------- Records ----------

private sealed interface HistoryEntry {
    val date: LocalDate
    data class Fuel(val entry: FuelEntry) : HistoryEntry { override val date get() = entry.date }
    data class Expense(val record: ExpenseRecord) : HistoryEntry { override val date get() = record.date }
}

private enum class HistoryFilter(val label: String) {
    ALL("All"), FUEL("سوخت"), SERVICE("سرویس"), REPAIR("تعمیر"), UPGRADE("ارتقاء"), OTHER("سایر");

    fun categoryOrNull(): RecordCategory? = when (this) {
        SERVICE -> RecordCategory.SERVICE
        REPAIR -> RecordCategory.REPAIR
        UPGRADE -> RecordCategory.UPGRADE
        OTHER -> RecordCategory.OTHER
        else -> null
    }
}

@Composable
private fun RecordsScreen(state: ShellState, vm: AppViewModel, modifier: Modifier, action: String?, clear: () -> Unit) {
    val v = state.vehicles.firstOrNull { it.id == state.activeVehicleId }
    var addFuel by remember { mutableStateOf(action == "fuel") }
    var addExpense by remember { mutableStateOf(action == "expense") }
    var editingFuel by remember { mutableStateOf<FuelEntry?>(null) }
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var deletingFuel by remember { mutableStateOf<FuelEntry?>(null) }
    var deletingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(action) {
        when (action) {
            "fuel" -> { addFuel = true; clear() }
            "expense" -> { addExpense = true; clear() }
        }
    }

    if (v != null && (addFuel || editingFuel != null)) {
        FuelForm(v, vm, { vm.saveFuel(it); addFuel = false; editingFuel = null }, { addFuel = false; editingFuel = null }, modifier, editingFuel)
        return
    }
    if (v != null && (addExpense || editingExpense != null)) {
        ExpenseForm(v, vm, { vm.saveExpense(it); addExpense = false; editingExpense = null }, { addExpense = false; editingExpense = null }, modifier, editingExpense)
        return
    }
    val fuels = v?.let { vm.fuelEntries(it.id).collectAsState(initial = emptyList()).value } ?: emptyList()
    val expenses = v?.let { vm.expenseRecords(it.id).collectAsState(initial = emptyList()).value } ?: emptyList()
    val typeById = vm.recordTypes().collectAsState(initial = emptyList()).value.associateBy { it.id }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val entries = remember(fuels, expenses, typeById, filter) {
        buildList {
            if (filter == HistoryFilter.ALL || filter == HistoryFilter.FUEL) fuels.forEach { add(HistoryEntry.Fuel(it)) }
            expenses.forEach { record ->
                if (filter == HistoryFilter.ALL || filter.categoryOrNull() == record.category) add(HistoryEntry.Expense(record))
            }
        }.sortedByDescending { it.date }
    }

    val months = entries.groupBy { YearMonth.from(it.date) }
    val models = buildList {
        months.keys.sortedDescending().forEach { month ->
            val list = months.getValue(month).sortedByDescending { it.date }
            list.forEachIndexed { index, entry ->
                add(TimelineModel(entry, Jalali.formatYearMonth(month).takeIf { index == 0 }, index == list.lastIndex))
            }
        }
    }

    ScreenHeader("سابقه", modifier, actions = {
        Box {
            IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "افزودن رکورد") }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text("افزودن سوخت") }, onClick = { addMenu = false; addFuel = true })
                DropdownMenuItem(text = { Text("افزودن هزینه") }, onClick = { addMenu = false; addExpense = true })
            }
        }
    }) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(filter == f, { filter = f }, label = { Text(f.label) })
                }
            }
            if (models.isEmpty()) {
                EmptyState("هنوز سابقه‌ای نیست", "سوخت، شارژ یا هزینه اضافه کنید تا سابقه ساخته شود.", "افزودن سوخت") { addFuel = true }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(models, key = { it.entry.key() }) { model ->
                        if (model.monthHeader != null) {
                            Text(model.monthHeader, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                        }
                        when (val entry = model.entry) {
                            is HistoryEntry.Fuel -> FuelHistoryItem(entry.entry, v, selectedKey == entry.key(), { selectedKey = if (selectedKey == entry.key()) null else entry.key() }, { editingFuel = entry.entry }, { deletingFuel = entry.entry }, model.isLast)
                            is HistoryEntry.Expense -> ExpenseHistoryItem(entry.record, typeById, v, selectedKey == entry.key(), { selectedKey = if (selectedKey == entry.key()) null else entry.key() }, { editingExpense = entry.record }, { deletingExpense = entry.record }, model.isLast)
                        }
                    }
                }
            }
        }
    }

    deletingFuel?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingFuel = null },
            title = { Text("حذف رکورد سوخت؟") },
            text = { Text("حذف ${entry.fuelLabel} از ${Jalali.format(entry.date)}؟ قابل بازگشت نیست.") },
            confirmButton = { TextButton(onClick = { vm.deleteFuel(entry); deletingFuel = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingFuel = null }) { Text("انصراف") } },
        )
    }
    deletingExpense?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingExpense = null },
            title = { Text("حذف رکورد هزینه؟") },
            text = { Text("این هزینه حذف شود؟ یادآورهای مرتبط به جدیدترین رکورد منتقل یا بدون لنگر می‌شوند. قابل بازگشت نیست.") },
            confirmButton = { TextButton(onClick = { vm.deleteExpense(record); deletingExpense = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingExpense = null }) { Text("انصراف") } },
        )
    }
}

private data class TimelineModel(val entry: HistoryEntry, val monthHeader: String?, val isLast: Boolean)

private fun HistoryEntry.key(): String = when (this) {
    is HistoryEntry.Fuel -> "f${entry.id}"
    is HistoryEntry.Expense -> "e${record.id}"
}

private fun viewReceipt(context: android.content.Context, receipt: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(receipt), "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.onFailure {
        Toast.makeText(context, "هیچ برنامه‌ای این رسید را باز نمی‌کند", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TimelineRow(icon: ImageVector, tint: Color, isLast: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        }
        Card(
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
private fun FuelHistoryItem(f: FuelEntry, v: Vehicle?, expanded: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, isLast: Boolean) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val title = f.stationName ?: f.fuelLabel.ifBlank { if (f.kind == FuelKind.ELECTRIC) "شارژ" else "سوخت" }
    val odometer = v?.let { VehicleValueFormatter.formatDistance(f.odometer, it.distanceUnit, locale) } ?: f.odometer.value.toString()
    val symbol = v?.let { distanceSymbol(it.distanceUnit) } ?: "m"
    val icon = if (f.kind == FuelKind.ELECTRIC) Icons.Outlined.Bolt else Icons.Outlined.LocalGasStation
    TimelineRow(icon = icon, tint = MaterialTheme.colorScheme.primary, isLast = isLast, onClick = onToggle) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text("$odometer $symbol • ${Jalali.format(f.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            val volume = f.volume
            if (volume != null) Text("حجم: ${v?.let { VehicleValueFormatter.formatVolume(volume, it.volumeUnit, locale) } ?: volume.value.toString()} ${v?.let { volumeSymbol(it.volumeUnit) } ?: ""}", style = MaterialTheme.typography.bodySmall)
            val energy = f.energy
            if (energy != null) Text("انرژی: ${v?.let { VehicleValueFormatter.formatEnergy(energy, it.energyUnit, locale) } ?: energy.value.toString()} ${v?.let { energySymbol(it.energyUnit) } ?: ""}", style = MaterialTheme.typography.bodySmall)
            f.unitPrice?.let { Text("قیمت واحد: ${VehicleValueFormatter.formatUnitPrice(it, locale)}", style = MaterialTheme.typography.bodySmall) }
            Text("هزینه: ${f.totalCost.currency} ${VehicleValueFormatter.formatMoney(f.totalCost, locale)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConfigPill(f.fuelLabel.ifBlank { if (f.kind == FuelKind.ELECTRIC) "شارژ" else "سوخت" })
                if (f.fullTank) ConfigPill(if (f.kind == FuelKind.ELECTRIC) "شارژ کامل" else "باک کامل")
                if (f.missedPreviousFillUp) ConfigPill("سوخت‌گیری از دست‌رفته")
            }
            f.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                f.receiptFileName?.let { receipt -> TextButton(onClick = { viewReceipt(context, receipt) }) { Icon(Icons.Outlined.Receipt, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("رسید") } }
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("ویرایش") }
                TextButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "حذف", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp)); Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ExpenseHistoryItem(r: ExpenseRecord, typeById: Map<Long, RecordType>, v: Vehicle?, expanded: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, isLast: Boolean) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val names = r.lineItems.map { typeById[it.typeId]?.name ?: "سرویس" }
    val title = if (names.isEmpty()) categoryLabel(r.category) else names.joinToString(", ")
    val odometer = v?.let { VehicleValueFormatter.formatDistance(r.odometer, it.distanceUnit, locale) } ?: r.odometer.value.toString()
    val symbol = v?.let { distanceSymbol(it.distanceUnit) } ?: "m"
    val category = r.category
    TimelineRow(icon = categoryIcon(category), tint = MaterialTheme.colorScheme.primary, isLast = isLast, onClick = onToggle) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text("$odometer $symbol • ${Jalali.format(r.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            r.lineItems.forEach { li ->
                Text("${typeById[li.typeId]?.name ?: "سرویس"}: ${li.cost.currency} ${VehicleValueFormatter.formatMoney(li.cost, locale)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("جمع: ${r.totalCost.currency} ${VehicleValueFormatter.formatMoney(r.totalCost, locale)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConfigPill(categoryLabel(category))
                ConfigPill(if (r.performedBy == PerformedBy.SHOP) "تعمیرگاه" else "خودم")
            }
            r.shopName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            r.warrantyUntil?.let { Text("گارانتی تا ${Jalali.format(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            r.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                r.receiptFileName?.let { receipt -> TextButton(onClick = { viewReceipt(context, receipt) }) { Icon(Icons.Outlined.Receipt, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("رسید") } }
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("ویرایش") }
                TextButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "حذف", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp)); Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun RecentFuelRow(f: FuelEntry, v: Vehicle) {
    val locale = Locale.getDefault()
    val title = f.stationName ?: f.fuelLabel.ifBlank { if (f.kind == FuelKind.ELECTRIC) "شارژ" else "سوخت" }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = if (f.kind == FuelKind.ELECTRIC) Icons.Outlined.Bolt else Icons.Outlined.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("${VehicleValueFormatter.formatDistance(f.odometer, v.distanceUnit, locale)} ${distanceSymbol(v.distanceUnit)} • ${Jalali.format(f.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${f.totalCost.currency} ${VehicleValueFormatter.formatMoney(f.totalCost, locale)}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DateField(
    value: LocalDate?,
    label: String,
    onSelect: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier) {
        TextField(
            value = value?.let { Jalali.format(it) }.orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            singleLine = true,
        )
        Box(Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) {
        JalaliDatePickerDialog(
            initial = value,
            onConfirm = { selected ->
                onSelect(selected)
                showPicker = false
            },
            onDismiss = { showPicker = false },
            onClear = if (allowClear) {
                {
                    onSelect(null)
                    showPicker = false
                }
            } else null,
        )
    }
}

@Composable
private fun SuggestionField(
    value: String,
    label: String,
    suggestions: List<String>,
    placeholder: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            TextField(
                value = value,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                singleLine = true,
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                suggestions.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
                if (value.isNotBlank()) DropdownMenuItem(text = { Text("پاک کردن انتخاب") }, onClick = { onSelect(""); expanded = false })
                DropdownMenuItem(
                    text = { Text("افزودن سریع") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { draft = ""; adding = true; expanded = false },
                )
            }
            Box(Modifier.matchParentSize().clickable { expanded = true })
        }
        if (adding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(draft, { draft = it }, Modifier.weight(1f), label = { Text("$label جدید") }, singleLine = true)
                TextButton(
                    onClick = { onSelect(draft.trim()); adding = false },
                    enabled = draft.isNotBlank(),
                ) { Text("افزودن") }
                TextButton(onClick = { adding = false }) { Text("انصراف") }
            }
        }
    }
}

@Composable
private fun FuelForm(v: Vehicle, vm: AppViewModel, save: (FuelEntry) -> Unit, cancel: () -> Unit, modifier: Modifier, initial: FuelEntry? = null) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val existingFuels = vm.fuelEntries(v.id).collectAsState(initial = emptyList()).value
    val stationSuggestions = remember(existingFuels) { existingFuels.mapNotNull { it.stationName?.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }
    val labelSuggestions = remember(existingFuels) { existingFuels.map { it.fuelLabel.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }
    var kind by remember(initial) { mutableStateOf(initial?.kind ?: if (v.fuelType == FuelType.ELECTRIC) FuelKind.ELECTRIC else FuelKind.LIQUID) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var amount by remember(initial, v.volumeUnit, v.energyUnit) { mutableStateOf(if (initial?.kind == FuelKind.ELECTRIC) initial.energy?.let { VehicleValueFormatter.formatEnergy(it, v.energyUnit, locale) } ?: "" else initial?.volume?.let { VehicleValueFormatter.formatVolume(it, v.volumeUnit, locale) } ?: "") }
    var unitPrice by remember(initial) { mutableStateOf(initial?.unitPrice?.let { VehicleValueFormatter.formatUnitPrice(it, locale) } ?: "") }
    var cost by remember(initial) { mutableStateOf(initial?.totalCost?.let { VehicleValueFormatter.formatMoney(it, locale) } ?: "") }
    var priceIsSource by remember { mutableStateOf(true) }
    var odo by remember(initial, v.distanceUnit) { mutableStateOf(initial?.odometer?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale) } ?: "") }
    var label by remember(initial) { mutableStateOf(initial?.fuelLabel ?: "") }
    var station by remember(initial) { mutableStateOf(initial?.stationName ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var full by remember(initial) { mutableStateOf(initial?.fullTank ?: true) }
    var missed by remember(initial) { mutableStateOf(initial?.missedPreviousFillUp ?: false) }
    var receipt by remember(initial) { mutableStateOf(initial?.receiptFileName) }
    var error by remember { mutableStateOf<String?>(null) }
    val initialAmount = if (initial?.kind == FuelKind.ELECTRIC) initial.energy?.let { VehicleValueFormatter.formatEnergy(it, v.energyUnit, locale) } ?: "" else initial?.volume?.let { VehicleValueFormatter.formatVolume(it, v.volumeUnit, locale) } ?: ""
    val dirty = kind != (initial?.kind ?: FuelKind.LIQUID) || date != (initial?.date ?: LocalDate.now()) || amount != initialAmount || unitPrice != (initial?.unitPrice?.let { VehicleValueFormatter.formatUnitPrice(it, locale) } ?: "") || cost != (initial?.totalCost?.let { VehicleValueFormatter.formatMoney(it, locale) } ?: "") || odo != (initial?.odometer?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale) } ?: "") || label != (initial?.fuelLabel ?: "") || station != (initial?.stationName ?: "") || notes != (initial?.notes ?: "") || full != (initial?.fullTank ?: true) || missed != (initial?.missedPreviousFillUp ?: false) || receipt != initial?.receiptFileName
    fun recomputeDerived() {
        val amountDecimal = VehicleValueFormatter.parseDecimal(amount, locale) ?: return
        if (priceIsSource) {
            val priceDecimal = unitPrice.takeIf { it.isNotBlank() }?.let { VehicleValueFormatter.parseDecimal(it, locale) } ?: return
            if (priceDecimal.signum() == 0) return
            val digits = CurrencyMinorDigits.of(v.currency)
            cost = VehicleValueFormatter.formatDecimal(amountDecimal.multiply(priceDecimal), locale, digits, digits)
        } else {
            val totalDecimal = cost.takeIf { it.isNotBlank() }?.let { VehicleValueFormatter.parseDecimal(it, locale) } ?: return
            if (amountDecimal.signum() == 0) return
            unitPrice = VehicleValueFormatter.formatDecimal(totalDecimal.divide(amountDecimal, 12, java.math.RoundingMode.HALF_EVEN), locale, 3)
        }
    }
    val requestCancel = rememberDiscardRequest(dirty, cancel)
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            receipt = uri.toString()
        }
    }
    val doSave: () -> Unit = {
        val parsedOdometer = VehicleValueFormatter.parseDistance(odo, v.distanceUnit, locale)
        val parsedMoney = VehicleValueFormatter.parseMoney(cost, v.currency, locale)
        val parsedVolume = if (kind == FuelKind.LIQUID) VehicleValueFormatter.parseVolume(amount, v.volumeUnit, locale) else null
        val parsedEnergy = if (kind == FuelKind.ELECTRIC) VehicleValueFormatter.parseEnergy(amount, v.energyUnit, locale) else null
        val candidate = if (parsedOdometer != null && parsedMoney != null && (parsedVolume != null || parsedEnergy != null)) FuelEntry(id = initial?.id ?: 0, vehicleId = v.id, date = date, odometer = parsedOdometer, kind = kind, volume = parsedVolume, energy = parsedEnergy, unitPrice = unitPrice.takeIf { it.isNotBlank() }?.let { VehicleValueFormatter.parseUnitPrice(it, v.currency, locale) }, totalCost = parsedMoney, fuelLabel = label.ifBlank { if (kind == FuelKind.LIQUID) "سوخت" else "شارژ" }, fullTank = full, missedPreviousFillUp = missed, stationName = station.ifBlank { null }, receiptFileName = receipt, notes = notes.ifBlank { null }, createdAt = initial?.createdAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) else null
        when (val validation = candidate?.let { Validators.fuelEntry(it, Clock.systemDefaultZone()) }) {
            null -> error = "تاریخ، کیلومتر، مقدار اعشاری، هزینه و قیمت واحد اختیاری معتبر وارد کنید"
            is ValidationResult.Error -> error = validation.message
            else -> save(candidate)
        }
    }

    Column(modifier.fillMaxSize()) {
        FormTopBar(if (initial == null) "افزودن سوخت" else "ویرایش سوخت", onClose = requestCancel, onSave = doSave)
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (v.fuelType == FuelType.HYBRID) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kind == FuelKind.LIQUID, { kind = FuelKind.LIQUID }, label = { Text("سوخت مایع") }, leadingIcon = { Icon(Icons.Outlined.LocalGasStation, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f))
                        FilterChip(kind == FuelKind.ELECTRIC, { kind = FuelKind.ELECTRIC }, label = { Text("شارژ برقی") }, leadingIcon = { Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField(date, "تاریخ", { selected -> if (selected != null) date = selected }, Modifier.weight(1f))
                    }
                    TextField(odo, { odo = it }, Modifier.fillMaxWidth(), label = { Text("کیلومترشمار (${distanceLabel(v.distanceUnit)})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            }
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(amount, { amount = it; recomputeDerived() }, Modifier.weight(1f), label = { Text(if (kind == FuelKind.LIQUID) "حجم (${volumeLabel(v.volumeUnit)})" else "انرژی (${energyLabel(v.energyUnit)})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        TextField(unitPrice, { unitPrice = it; priceIsSource = true; recomputeDerived() }, Modifier.weight(1f), label = { Text("قیمت واحد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    TextField(cost, { cost = it; priceIsSource = false; recomputeDerived() }, Modifier.fillMaxWidth(), label = { Text("هزینه کل (${v.currency})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Text("مقدار و یکی از قیمت واحد یا هزینه کل را وارد کنید — دیگری خودکار پر می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(full, { full = it }); Text(if (kind == FuelKind.LIQUID) "باک کامل" else "شارژ کامل") }
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(missed, { missed = it }); Text("سوخت‌گیری از دست‌رفته") }
                    }
                }
            }
            item {
                SectionCard {
                    SuggestionField(station, "پمپ بنزین", stationSuggestions, "ایستگاه را انتخاب یا سریع اضافه کنید") { station = it }
                    SuggestionField(label, "گرید سوخت (اختیاری)", labelSuggestions, "گرید را انتخاب یا سریع اضافه کنید") { label = it }
                    TextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت (اختیاری)") }, minLines = 2)
                    TextButton(onClick = { receiptPicker.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(if (receipt == null) Icons.Outlined.AttachFile else Icons.Filled.CheckCircle, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (receipt == null) "پیوست رسید" else "رسید پیوست شد")
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun ExpenseForm(v: Vehicle, vm: AppViewModel, save: (ExpenseRecord) -> Unit, cancel: () -> Unit, modifier: Modifier, initial: ExpenseRecord? = null) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    var date by remember(initial) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var odo by remember(initial, v.distanceUnit) { mutableStateOf(initial?.odometer?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale) } ?: "") }
    val types = vm.recordTypes().collectAsState(initial = emptyList()).value
    var category by remember(initial) { mutableStateOf(initial?.category ?: RecordCategory.SERVICE) }
    var lineCosts by remember(initial) { mutableStateOf<Map<Long, String>>(initial?.lineItems?.associate { it.typeId to VehicleValueFormatter.formatMoney(it.cost, locale) } ?: emptyMap()) }
    var performedBy by remember(initial) { mutableStateOf(initial?.performedBy ?: PerformedBy.SELF) }
    var shop by remember(initial) { mutableStateOf(initial?.shopName ?: "") }
    var warranty by remember(initial) { mutableStateOf(initial?.warrantyUntil) }
    var receipt by remember(initial) { mutableStateOf(initial?.receiptFileName) }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val categoryTypes = types.filter { it.category == category }
    val dirty = date != (initial?.date ?: LocalDate.now()) || description != (initial?.description ?: "") || odo != (initial?.odometer?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale) } ?: "") || category != (initial?.category ?: RecordCategory.SERVICE) || lineCosts != (initial?.lineItems?.associate { it.typeId to VehicleValueFormatter.formatMoney(it.cost, locale) } ?: emptyMap<Long, String>()) || performedBy != (initial?.performedBy ?: PerformedBy.SELF) || shop != (initial?.shopName ?: "") || warranty != initial?.warrantyUntil || receipt != initial?.receiptFileName || notes != (initial?.notes ?: "")
    val requestCancel = rememberDiscardRequest(dirty, cancel)
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            receipt = uri.toString()
        }
    }
    val doSave: () -> Unit = {
        val parsedOdometer = VehicleValueFormatter.parseDistance(odo, v.distanceUnit, locale)
        val lineItems = lineCosts.mapNotNull { (typeId, costText) ->
            VehicleValueFormatter.parseMoney(costText, v.currency, locale)?.let { ExpenseLineItem(typeId, it) }
        }
        val candidate = if (lineItems.isNotEmpty() && parsedOdometer != null) ExpenseRecord(id = initial?.id ?: 0, vehicleId = v.id, category = category, date = date, odometer = parsedOdometer, description = description.ifBlank { null }, performedBy = performedBy, shopName = shop.ifBlank { null }, warrantyUntil = warranty, receiptFileName = receipt, notes = notes.ifBlank { null }, lineItems = lineItems, createdAt = initial?.createdAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) else null
        when (val validation = candidate?.let(Validators::expenseRecord)) {
            null -> error = "حداقل یک سرویس انتخاب کنید و هزینه، کیلومتر و تاریخ را وارد کنید"
            is ValidationResult.Error -> error = validation.message
            else -> save(candidate)
        }
    }

    Column(modifier.fillMaxSize()) {
        FormTopBar(if (initial == null) "افزودن هزینه" else "ویرایش هزینه", onClose = requestCancel, onSave = doSave)
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("دسته‌بندی", style = MaterialTheme.typography.titleSmall) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordCategory.entries.forEach { c ->
                        FilterChip(category == c, { category = c; lineCosts = emptyMap() }, leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(16.dp)) }, label = { Text(categoryLabel(c)) })
                    }
                }
            }
            item { Text("سرویس‌ها", style = MaterialTheme.typography.titleSmall) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryTypes.forEach { t ->
                        val selected = t.id in lineCosts
                        FilterChip(
                            selected = selected,
                            onClick = {
                                lineCosts = if (selected) lineCosts - t.id else lineCosts + (t.id to "")
                            },
                            label = { Text(t.name) },
                        )
                    }
                }
            }
            if (lineCosts.isNotEmpty()) {
                item { Text("هزینه هر سرویس", style = MaterialTheme.typography.titleSmall) }
                items(lineCosts.keys.toList(), key = { "line-$it" }) { typeId ->
                    val name = types.firstOrNull { it.id == typeId }?.name ?: "سرویس"
                    TextField(
                        value = lineCosts[typeId] ?: "",
                        onValueChange = { text -> lineCosts = lineCosts + (typeId to text) },
                        Modifier.fillMaxWidth(),
                        label = { Text("$name (${v.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            }
            item { DateField(date, "تاریخ", { selected -> if (selected != null) date = selected }) }
            item { TextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("توضیحات (اختیاری)") }, minLines = 2) }
            item { TextField(odo, { odo = it }, Modifier.fillMaxWidth(), label = { Text("کیلومترشمار (${distanceLabel(v.distanceUnit)})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item { Text("انجام‌دهنده", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(performedBy == PerformedBy.SELF, { performedBy = PerformedBy.SELF }, label = { Text("خودم") })
                    FilterChip(performedBy == PerformedBy.SHOP, { performedBy = PerformedBy.SHOP }, label = { Text("تعمیرگاه") })
                }
            }
            if (performedBy == PerformedBy.SHOP) item { TextField(shop, { shop = it }, Modifier.fillMaxWidth(), label = { Text("نام تعمیرگاه / مکانیک") }, singleLine = true) }
            item { DateField(warranty, "گارانتی تا (اختیاری)", { warranty = it }, allowClear = true) }
            item { TextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت (اختیاری)") }, minLines = 2) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { receiptPicker.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(if (receipt == null) Icons.Outlined.AttachFile else Icons.Filled.CheckCircle, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (receipt == null) "پیوست رسید" else "جایگزینی رسید")
                    }
                    if (receipt != null) TextButton(onClick = { receipt = null }) { Text("حذف") }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun RecordTypeDropdown(types: List<RecordType>, selected: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = types.firstOrNull { it.id == selected }?.name ?: "انتخاب نوع"
    Box {
        TextField(
            value = selectedName,
            onValueChange = {},
            Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text("نوع رکورد") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { onSelect(t.id); expanded = false }) }
        }
        // Invisible clickable overlay to open the menu
        Box(Modifier.matchParentSize().clickable { expanded = true })
    }
}

// ---------- Reminders ----------

@Composable
private fun RemindersScreen(state: ShellState, vm: AppViewModel, modifier: Modifier, open: Boolean, clear: () -> Unit) {
    val v = state.vehicles.firstOrNull { it.id == state.activeVehicleId }
    var form by remember { mutableStateOf(open) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }
    LaunchedEffect(open) { if (open) { form = true; clear() } }
    if (v != null && (form || editing != null)) {
        ReminderForm(v, vm, { vm.saveReminder(it); form = false; editing = null }, { form = false; editing = null }, modifier, editing)
        return
    }
    val rs = if (v == null) emptyList<Reminder>() else vm.reminders(v.id).collectAsState(initial = emptyList()).value
    val fuels = if (v == null) emptyList() else vm.fuelEntries(v.id).collectAsState(initial = emptyList()).value
    val expenses = if (v == null) emptyList() else vm.expenseRecords(v.id).collectAsState(initial = emptyList()).value
    val currentOdometer = v?.let { currentOdometer(it, fuels, expenses) }
    val typeNames = vm.recordTypes().collectAsState(initial = emptyList()).value.associate { it.id to it.name }
    var filter by remember { mutableStateOf<ReminderState?>(null) }
    val statuses = rs.map { it to DueCalculator.evaluate(it, currentOdometer, LocalDate.now()) }
    val filtered = if (filter == null) statuses else statuses.filter { it.second.state == filter }
    ScreenHeader("یادآورها", modifier, actions = {
        TextButton(onClick = { form = true }) { Icon(Icons.Filled.Add, contentDescription = "افزودن یادآور"); Spacer(Modifier.width(4.dp)); Text("افزودن") }
    }) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("اعلان‌ها را برای هشدار یادآورها فعال کنید.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("فعال‌سازی") }
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, label = { Text("همه (${rs.size})") })
                    listOf(ReminderState.OVERDUE, ReminderState.DUE_SOON, ReminderState.OK, ReminderState.INACTIVE).forEach { st ->
                        FilterChip(filter == st, { filter = st }, label = { Text("${when (st) {
                            ReminderState.OVERDUE -> "تأخیر"
                            ReminderState.DUE_SOON -> "به‌زودی"
                            ReminderState.OK -> "به‌موقع"
                            ReminderState.INACTIVE -> "غیرفعال"
                        }} (${statuses.count { it.second.state == st }})") })
                    }
                }
            }
            item { Text("هر کدام زودتر برسد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (filtered.isEmpty()) item { EmptyState("یادآوری نیست", "یک بازه سرویس مرتبط با نوع سرویس اضافه کنید.", "افزودن یادآور") { form = true } }
            if (v != null) items(filtered, key = { it.first.id }) { (r, s) -> ReminderCard(r, s, typeNames[r.typeId] ?: "سرویس", v, currentOdometer, vm, { editing = r }) }
        }
    }
}

@Composable
private fun ReminderForm(v: Vehicle, vm: AppViewModel, save: (Reminder) -> Unit, cancel: () -> Unit, modifier: Modifier, initial: Reminder? = null) {
    val locale = Locale.getDefault()
    val types = vm.recordTypes().collectAsState(initial = emptyList()).value.filter { it.category == RecordCategory.SERVICE }
    var type by remember(initial?.id) { mutableStateOf(initial?.typeId ?: 0) }
    var typeInitialized by remember(initial?.id) { mutableStateOf(false) }
    var months by remember(initial) { mutableStateOf(initial?.intervalMonths?.toString() ?: "") }
    var distance by remember(initial, v.distanceUnit) { mutableStateOf(initial?.intervalDistance?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale) } ?: "") }
    var active by remember(initial) { mutableStateOf(initial?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(types, initial?.id) {
        if (!typeInitialized && types.isNotEmpty()) {
            type = initial?.typeId?.takeIf { id -> types.any { it.id == id } } ?: types.first().id
            typeInitialized = true
        }
    }
    val dirty = type != (initial?.typeId ?: 0) || months != (initial?.intervalMonths?.toString() ?: "") || distance != (initial?.intervalDistance?.let { VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale) } ?: "") || active != (initial?.active ?: true)
    val requestCancel = rememberDiscardRequest(dirty, cancel)

    ScreenHeader(if (initial == null) "افزودن یادآور" else "ویرایش یادآور", modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("نوع سرویس", style = MaterialTheme.typography.titleSmall) }
            item { RecordTypeDropdown(types, type) { type = it } }
            item { TextField(months, { months = it }, Modifier.fillMaxWidth(), label = { Text("فاصله ماهانه") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) }
            item { TextField(distance, { distance = it }, Modifier.fillMaxWidth(), label = { Text("فاصله مسافت (${distanceLabel(v.distanceUnit)})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Spacer(Modifier.width(8.dp))
                    Text("یادآور فعال")
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = requestCancel, Modifier.weight(1f)) { Text("انصراف") }
                    Button(onClick = {
                        val m = months.toIntOrNull()
                        val d = distance.takeIf { it.isNotBlank() }?.let { VehicleValueFormatter.parseDistance(it, v.distanceUnit, locale) }
                        val candidate = Reminder(id = initial?.id ?: 0, vehicleId = v.id, typeId = type, intervalDistance = d, intervalMonths = m, anchorDate = initial?.anchorDate, anchorOdometer = initial?.anchorOdometer, active = active)
                        when (val validation = Validators.reminder(candidate)) {
                            is ValidationResult.Error -> error = validation.message
                            else -> if (type == 0L) error = "نوع سرویس را انتخاب کنید" else save(candidate)
                        }
                    }, Modifier.weight(1f)) { Text("ذخیره") }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(r: Reminder, s: ReminderStatus, name: String, v: Vehicle, currentOdometer: Metres?, vm: AppViewModel, onEdit: () -> Unit) {
    val overdue = s.state == ReminderState.OVERDUE
    val dueSoon = s.state == ReminderState.DUE_SOON
    val accent = when { overdue -> MaterialTheme.colorScheme.error; dueSoon -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
    val locale = Locale.getDefault()
    val intervalParts = buildList {
        r.intervalDistance?.let { add("${VehicleValueFormatter.formatDistance(it, v.distanceUnit, locale)} ${distanceSymbol(v.distanceUnit)}") }
        r.intervalMonths?.let { add("$it mo") }
    }
    val intervalText = if (intervalParts.isEmpty()) "بدون بازه" else "هر ${intervalParts.joinToString(" or ")}"
    val remainingDays = s.remainingDays
    val remainingDistance = s.remainingDistance
    val nextDueDate = s.nextDueDate
    val nextDueOdometer = s.nextDueOdometer
    val intervalDistance = r.intervalDistance
    val remaining = when {
        remainingDays != null && remainingDays < 0 -> "${-remainingDays} days past due"
        remainingDistance != null && remainingDistance.value < 0 -> "${VehicleValueFormatter.formatDistance(Metres(-remainingDistance.value), v.distanceUnit, locale)} ${distanceSymbol(v.distanceUnit)} past due"
        remainingDays != null -> "$remainingDays days left"
        remainingDistance != null -> "${VehicleValueFormatter.formatDistance(remainingDistance, v.distanceUnit, locale)} ${distanceSymbol(v.distanceUnit)} left"
        else -> null
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (overdue) 2.dp else 1.dp, if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill(when (s.state) {
                    ReminderState.OVERDUE -> "تأخیر"
                    ReminderState.DUE_SOON -> "به‌زودی"
                    ReminderState.OK -> "به‌موقع"
                    ReminderState.INACTIVE -> "غیرفعال"
                }, if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(intervalText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (nextDueDate != null) Text("موعد ${Jalali.format(nextDueDate)}", style = MaterialTheme.typography.bodySmall, color = accent)
            if (nextDueOdometer != null) Text("موعد در ${VehicleValueFormatter.formatDistance(nextDueOdometer, v.distanceUnit, locale)} ${distanceSymbol(v.distanceUnit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            remaining?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.SemiBold) }
            if (dueSoon && remainingDistance != null && intervalDistance != null) {
                val progress = (remainingDistance.value.toFloat() / intervalDistance.value.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("ویرایش") }
                TextButton(onClick = { vm.saveReminder(r.copy(anchorDate = LocalDate.now(), anchorOdometer = currentOdometer)) }, enabled = currentOdometer != null) { Icon(Icons.Outlined.Refresh, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("بازنشانی") }
                IconButton(onClick = { vm.saveReminder(r.copy(active = !r.active)) }) { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = if (r.active) "غیرفعال‌سازی" else "فعال‌سازی", tint = if (r.active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

// ---------- Settings / Stats / Portability ----------

@Composable
private fun SettingsScreen(state: ShellState, vm: AppViewModel, modifier: Modifier, onVehicles: () -> Unit, onData: () -> Unit, onTypes: () -> Unit) {
    val themeMode by vm.themeMode.collectAsState()
    ScreenHeader("تنظیمات", modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SettingsRow("خودروی فعال", state.vehicles.firstOrNull { it.id == state.activeVehicleId }?.currency ?: "بدون خودرو") }
            item { SettingsRow("خودروها", "مدیریت گاراژ", onVehicles) }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ظاهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("تم را انتخاب کنید یا از تنظیم دستگاه پیروی کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { vm.setThemeMode(mode) },
                                    label = { Text(when (mode) { ThemeMode.SYSTEM -> "سیستم"; ThemeMode.LIGHT -> "روشن"; ThemeMode.DARK -> "تاریک" }) },
                                )
                            }
                        }
                    }
                }
            }
            item { SettingsRow("انواع رکورد", "مدیریت انواع سرویس، تعمیر، ارتقاء و سایر", onTypes) }
            item { SettingsRow("پشتیبان و مهاجرت", "JSON • CSV • درایوو • فیولیو", onData) }
            item { SettingsRow("حریم خصوصی", "اولویت آفلاین • بدون ردیابی") }
        }
    }
}

@Composable
private fun RecordTypesScreen(vm: AppViewModel, modifier: Modifier) {
    val types = vm.recordTypes().collectAsState(initial = emptyList()).value
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(RecordCategory.SERVICE) }
    ScreenHeader("انواع رکورد", modifier, actions = {
        TextButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "افزودن نوع"); Spacer(Modifier.width(4.dp)); Text("افزودن") }
    }) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (adding) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("نام نوع") }, singleLine = true)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecordCategory.entries.forEach { c -> FilterChip(category == c, { category = c }, leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(16.dp)) }, label = { Text(categoryLabel(c)) }) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { adding = false; name = "" }, Modifier.weight(1f)) { Text("انصراف") }
                                Button(onClick = { if (name.isNotBlank()) { vm.saveRecordType(RecordType(category = category, name = name.trim(), isDefault = false)); adding = false; name = "" } }, Modifier.weight(1f), enabled = name.isNotBlank()) { Text("ذخیره") }
                            }
                        }
                    }
                }
            }
            RecordCategory.entries.forEach { c ->
                val inCategory = types.filter { it.category == c }
                if (inCategory.isNotEmpty()) {
                    item { Text(categoryLabel(c), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(inCategory, key = { it.id }) { t -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(t.name, Modifier.padding(14.dp)) } }
                }
            }
        }
    }
}

@Composable
private fun StatisticsScreen(state: ShellState, vm: AppViewModel, modifier: Modifier) {
    val v = state.vehicles.firstOrNull { it.id == state.activeVehicleId }
    val entries = if (v == null) emptyList() else vm.fuelEntries(v.id).collectAsState(initial = emptyList()).value
    val expenses = if (v == null) emptyList() else vm.expenseRecords(v.id).collectAsState(initial = emptyList()).value
    var period by remember { mutableIntStateOf(1) }
    val months = listOf(3, 6, 12, 24)
    val monthCount = months[period]
    val firstMonth = YearMonth.now().minusMonths(monthCount.toLong() - 1)
    val daysInPeriod = ChronoUnit.DAYS.between(firstMonth.atDay(1), YearMonth.now().atEndOfMonth()) + 1
    val filteredEntries = entries.filter { YearMonth.from(it.date) >= firstMonth }
    val filteredExpenses = expenses.filter { YearMonth.from(it.date) >= firstMonth }
    val result = FuelStats.of(filteredEntries, Clock.systemUTC(), firstMonth, YearMonth.now())
    val stats = result as? FuelStatsResult.Stats

    val currency = v?.currency ?: "USD"
    val fuelCostMinor = stats?.totalCost?.minor ?: 0L
    val expenseLineItems = filteredExpenses.flatMap { it.lineItems }
    val expenseCostMinor = expenseLineItems.sumOf { it.cost.minor }

    val odoReadings = (filteredEntries.map { it.odometer.value } + filteredExpenses.map { it.odometer.value })
    val kmDriven = if (odoReadings.size >= 2) (odoReadings.maxOrNull()!! - odoReadings.minOrNull()!!) / 1000.0 else 0.0

    val totalCostMinor = fuelCostMinor + expenseCostMinor
    fun byDay(minor: Long): String = if (daysInPeriod > 0) "${currency} ${formatMoney((minor / daysInPeriod).toLong().coerceAtLeast(0), currency)}/day" else "—"
    fun byKm(minor: Long): String = if (kmDriven > 0) "${currency} ${formatMoney((minor / kmDriven).toLong().coerceAtLeast(0), currency)}/km" else "—"

    val expenseByCategory = filteredExpenses.groupBy { it.category }
        .mapValues { (_, recs) -> recs.flatMap { it.lineItems }.sumOf { it.cost.minor } }

    ScreenHeader(if (v == null) "آمار" else "${v.name} statistics", modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("3M", "6M", "1Y", "2Y").forEachIndexed { i, label -> FilterChip(period == i, { period = i }, label = { Text(label) }) }
                }
            }

            item { SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("هزینه کل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${currency} ${formatMoney(totalCostMinor, currency)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("سوخت + هزینه‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("روزانه: ${byDay(totalCostMinor)}", style = MaterialTheme.typography.bodySmall)
                    Text("کیلومتر: ${byKm(totalCostMinor)}", style = MaterialTheme.typography.bodySmall)
                }
            } }

            item { SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سوخت‌گیری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("هزینه: ${currency} ${formatMoney(fuelCostMinor, currency)}", style = MaterialTheme.typography.bodyMedium)
                    Text("روزانه: ${byDay(fuelCostMinor)} • کیلومتر: ${byKm(fuelCostMinor)}", style = MaterialTheme.typography.bodySmall)
                    stats?.totalVolume?.let { Text("حجم: ${(it.value.toDouble() / 1000.0)} لیتر", style = MaterialTheme.typography.bodyMedium) }
                    if (!filteredEntries.isEmpty()) {
                        val avgFill = (filteredEntries.sumOf { it.volume?.value ?: 0L }.toDouble() / 1000.0) / filteredEntries.size
                        Text("میانگین کلی: ${"%.2f".format(avgFill)} L / fill", style = MaterialTheme.typography.bodySmall)
                    }
                    stats?.averageMlPer100Km?.let { Text("مصرف سوخت: ${Economy.kmPerLitre(it, 2)} کیلومتر/لیتر", style = MaterialTheme.typography.bodyMedium) }
                }
            } }

            item { SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("هزینه‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("هزینه: ${currency} ${formatMoney(expenseCostMinor, currency)}", style = MaterialTheme.typography.bodyMedium)
                    Text("روزانه: ${byDay(expenseCostMinor)} • کیلومتر: ${byKm(expenseCostMinor)}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    listOf(RecordCategory.SERVICE, RecordCategory.REPAIR, RecordCategory.UPGRADE, RecordCategory.OTHER).forEach { cat ->
                        val minor = expenseByCategory[cat] ?: 0L
                        if (minor > 0L) {
                            Text(categoryLabel(cat), style = MaterialTheme.typography.titleSmall)
                            Text("${currency} ${formatMoney(minor, currency)} — day ${byDay(minor)} — km ${byKm(minor)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } }
        }
    }
}

@Composable
private fun PortabilityScreen(vm: AppViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("هنوز عملیات انتقال داده انجام نشده") }
    var preview by remember { mutableStateOf<ImportResult?>(null) }

    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupJson().toByteArray()) }; message = "پشتیبان JSON ساخته شد" }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            message = if (text == null) "خواندن پشتیبان ممکن نشد" else if (vm.restoreJson(text).isSuccess) "پشتیبان بازیابی شد" else "بازیابی پشتیبان ناموفق"
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportFuelCsv().toByteArray()) }; message = "CSV سوخت صادر شد" }
    }
    val expensesCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportExpenseCsv().toByteArray()) }; message = "CSV هزینه صادر شد" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (text == null) message = "خواندن فایل انتخاب‌شده ممکن نشد"
            else { preview = if (text.contains("#Refueling")) vm.importDrivvo(text) else vm.importFuelio(text); message = "قبل از اعمال، واردات را بررسی کنید" }
        }
    }

    ScreenHeader("انتقال داده", modifier, eyebrow = "داده آفلاین") {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("داده‌هایتان را قابل انتقال و محلی نگه دارید.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick = { json.launch("openodo-backup.json") }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Save, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("پشتیبان JSON") } }
            item { OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/json")) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Upload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("بازیابی JSON") } }
            item { OutlinedButton(onClick = { csv.launch("openodo-fuel.csv") }, Modifier.fillMaxWidth()) { Text("خروجی CSV سوخت") } }
            item { OutlinedButton(onClick = { expensesCsv.launch("openodo-expenses.csv") }, Modifier.fillMaxWidth()) { Text("خروجی CSV هزینه") } }
            item { OutlinedButton(onClick = { importer.launch(arrayOf("text/csv", "text/plain", "text/comma-separated-values")) }, Modifier.fillMaxWidth()) { Text("وارد کردن CSV درایوو / فیولیو") } }
            preview?.let { p ->
                item { ChartCard("پیش‌نمایش واردات", "${p.domain.fuelEntries.size} fuel • ${p.domain.expenseRecords.size} expenses • ${p.report.count { it.level.toString().contains("WARN") }} warnings • ${p.report.count { it.level.toString().contains("SKIP") }} skipped") }
                if (p.domain.recordTypes.isNotEmpty()) {
                    item {
                        Text("اتصال نوع هزینه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("قبل از اعمال، محل نمایش هر نوع درایوو را مشخص کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(p.domain.recordTypes, key = { "import-type-${it.id}" }) { type ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(type.name, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RecordCategory.entries.forEach { category ->
                                        FilterChip(
                                            selected = type.category == category,
                                            onClick = {
                                                preview = p.copy(
                                                    domain = p.domain.copy(
                                                        recordTypes = p.domain.recordTypes.map { candidate ->
                                                            if (candidate.id == type.id) candidate.copy(category = category) else candidate
                                                        },
                                                    ),
                                                )
                                            },
                                            label = { Text(categoryLabel(category)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (p.report.isNotEmpty()) item { ChartCard("جزئیات گزارش", p.report.take(8).joinToString("\n") { "Row ${it.row}: ${it.code}" }) }
                item { Button(onClick = { vm.applyImport(p); preview = null; message = "واردات اعمال شد" }, Modifier.fillMaxWidth()) { Text("اعمال واردات") } }
            }
            item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

private fun distanceLabel(u: DistanceUnit) = when (u) {
    DistanceUnit.METRES -> "متر (m)"
    DistanceUnit.KILOMETRES -> "کیلومتر (km)"
    DistanceUnit.MILES -> "مایل (mi)"
}

private fun fuelTypeLabel(t: FuelType) = when (t) {
    FuelType.FUEL -> "سوخت"
    FuelType.ELECTRIC -> "برقی"
    FuelType.HYBRID -> "هیبرید"
}

private fun distanceSymbol(u: DistanceUnit) = when (u) {
    DistanceUnit.METRES -> "m"
    DistanceUnit.KILOMETRES -> "km"
    DistanceUnit.MILES -> "mi"
}

private fun volumeSymbol(u: VolumeUnit) = when (u) {
    VolumeUnit.MILLILITRES -> "mL"
    VolumeUnit.LITRES -> "L"
    VolumeUnit.US_GALLONS -> "gal"
    VolumeUnit.UK_GALLONS -> "gal"
}

private fun energySymbol(u: EnergyUnit) = when (u) {
    EnergyUnit.WATT_HOURS -> "Wh"
    EnergyUnit.KILOWATT_HOURS -> "kWh"
}

private fun volumeLabel(u: VolumeUnit) = when (u) {
    VolumeUnit.MILLILITRES -> "میلی‌لیتر (mL)"
    VolumeUnit.LITRES -> "لیتر (L)"
    VolumeUnit.US_GALLONS -> "گالن آمریکایی (gal)"
    VolumeUnit.UK_GALLONS -> "گالن انگلیسی (gal)"
}

private fun energyLabel(u: EnergyUnit) = when (u) {
    EnergyUnit.WATT_HOURS -> "وات‌ساعت (Wh)"
    EnergyUnit.KILOWATT_HOURS -> "کیلووات‌ساعت (kWh)"
}

private fun categoryLabel(c: RecordCategory) = when (c) {
    RecordCategory.SERVICE -> "سرویس"
    RecordCategory.REPAIR -> "تعمیر"
    RecordCategory.UPGRADE -> "ارتقاء"
    RecordCategory.OTHER -> "سایر"
}

private fun categoryIcon(c: RecordCategory): ImageVector = when (c) {
    RecordCategory.SERVICE -> Icons.Outlined.Build
    RecordCategory.REPAIR -> Icons.Outlined.Handyman
    RecordCategory.UPGRADE -> Icons.Outlined.AutoAwesome
    RecordCategory.OTHER -> Icons.Outlined.MoreHoriz
}

private fun formatMoney(minor: Long, currency: String): String {
    val digits = CurrencyMinorDigits.of(currency)
    return java.math.BigDecimal(minor).movePointLeft(digits).setScale(digits).toPlainString()
}
