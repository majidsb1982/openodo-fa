// SPDX-License-Identifier: GPL-3.0-only
package ca.cyberengma.openodo.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * Fully Persian (Jalali) date picker dialog — no Gregorian calendar UI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JalaliDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val todayJ = Jalali.today()
    val initialJ = initial?.let { Jalali.fromGregorian(it) } ?: todayJ

    var year by remember { mutableIntStateOf(initialJ.year) }
    var month by remember { mutableIntStateOf(initialJ.month) }
    var selectedDay by remember { mutableIntStateOf(initialJ.day.coerceAtMost(Jalali.daysInMonth(initialJ.year, initialJ.month))) }
    var showYearPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val daysInMonth = Jalali.daysInMonth(year, month)
    if (selectedDay > daysInMonth) selectedDay = daysInMonth

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "انتخاب تاریخ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Year / Month header with navigation
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = {
                        if (month == 1) {
                            month = 12
                            year--
                        } else month--
                    }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "ماه قبل")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showYearPicker = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Text(
                            text = Jalali.monthNames[month - 1],
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMonthPicker = true }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                    IconButton(onClick = {
                        if (month == 12) {
                            month = 1
                            year++
                        } else month++
                    }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "ماه بعد")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Weekday headers (شنبه … جمعه)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Day grid
                // First day of month weekday offset (Jalali: Saturday = 0)
                val firstOfMonth = Jalali.toGregorian(year, month, 1)
                // java.time: Monday=1 … Sunday=7 → map to Saturday=0
                val offset = (firstOfMonth.dayOfWeek.value % 7) // Sun=0 in ISO? value 7→0, Mon=1…
                // Actually: Saturday should be 0 for Persian week start
                // dayOfWeek: MON=1 … SAT=6, SUN=7
                // Persian week: SAT=0, SUN=1, … FRI=6
                val persianOffset = (firstOfMonth.dayOfWeek.value % 7) // SAT(6)%7=6, SUN(7)%7=0 → wrong
                // Correct: (dayOfWeek.value + 1) % 7  → SAT(6)+1=7%7=0, SUN=1, MON=2 …
                val startOffset = (firstOfMonth.dayOfWeek.value + 1) % 7

                val totalCells = startOffset + daysInMonth
                val rows = (totalCells + 6) / 7

                Column {
                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (col in 0..6) {
                                val cell = row * 7 + col
                                val day = cell - startOffset + 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (day in 1..daysInMonth) {
                                        val isSelected = day == selectedDay
                                        val isToday = year == todayJ.year && month == todayJ.month && day == todayJ.day
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { selectedDay = day },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = day.toString(),
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Year picker overlay
                if (showYearPicker) {
                    Spacer(Modifier.height(8.dp))
                    Text("انتخاب سال", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val current = todayJ.year
                        ((current - 40)..(current + 5)).forEach { y ->
                            val selected = y == year
                            Text(
                                text = y.toString(),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        year = y
                                        showYearPicker = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Month picker overlay
                if (showMonthPicker) {
                    Spacer(Modifier.height(8.dp))
                    Text("انتخاب ماه", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Jalali.monthNames.forEachIndexed { idx, name ->
                            val m = idx + 1
                            val selected = m == month
                            Text(
                                text = name,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        month = m
                                        showMonthPicker = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(Jalali.toGregorian(year, month, selectedDay))
                },
            ) { Text("تأیید") }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) { Text("پاک کردن") }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
}
