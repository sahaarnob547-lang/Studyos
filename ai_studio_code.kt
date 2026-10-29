package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.DailyNoteEntity
import com.example.data.models.StudySessionEntity
import com.example.data.models.SubjectEntity
import com.example.data.models.TaskEntity
import com.example.logic.PlannerWorkloadBalancer
import com.example.ui.components.PastelMetricCard
import com.example.ui.components.SectionHeader
import com.example.ui.viewmodel.StudyOSViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PlannerTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DAILY_TASKS("Daily Tasks", Icons.Default.Checklist),
    DAILY_REPORT("Daily Report", Icons.Default.Assessment),
    PACING("Pacing & Burnout", Icons.Default.Speed)
}

data class DailyStudyReportData(
    val dateString: String,
    val dayLabel: String,
    val totalPlannedTasks: Int,
    val completedTasksCount: Int,
    val taskCompletionRate: Float,
    val totalFocusMinutes: Int,
    val targetDailyMinutes: Int,
    val focusGoalRate: Float,
    val performanceGrade: String,
    val performanceSummary: String,
    val topSubjectName: String,
    val topSubjectMinutes: Int,
    val mood: String,
    val energyRating: Int
)

@Composable
fun PlannerScreen(
    viewModel: StudyOSViewModel,
    modifier: Modifier = Modifier
) {
    val subjects by viewModel.subjects.collectAsState()
    val topics by viewModel.topics.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val sessions by viewModel.studySessions.collectAsState()
    val dailyNotes by viewModel.dailyNotes.collectAsState()
    val isBangla by viewModel.isBangla.collectAsState()

    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(PlannerTab.DAILY_TASKS) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskFilter by remember { mutableStateOf("All") } // All, High, Pending, Done
    var selectedDayOffset by remember { mutableStateOf(0) } // 0 = Today, -1 = Yesterday, -2 = 2 days ago

    val workload = PlannerWorkloadBalancer.calculateWorkload(subjects, topics, tasks, exams)

    // Calculate daily report data for the selected day offset
    val dailyReport = remember(tasks, sessions, dailyNotes, workload, selectedDayOffset, subjects) {
        computeDailyReport(
            dayOffset = selectedDayOffset,
            tasks = tasks,
            sessions = sessions,
            dailyNotes = dailyNotes,
            workloadHours = workload.recommendedDailyHours,
            subjects = subjects
        )
    }

    // Filter tasks for the checklist
    val displayTasks = remember(tasks, taskFilter) {
        when (taskFilter) {
            "High" -> tasks.filter { it.priority == "High" }
            "Pending" -> tasks.filter { !it.isCompleted }
            "Done" -> tasks.filter { it.isCompleted }
            else -> tasks
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("planner_screen"),
        floatingActionButton = {
            if (selectedTab == PlannerTab.DAILY_TASKS) {
                ExtendedFloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (isBangla) "লক্ষ্য কাজ যোগ করুন" else "Add Daily Task") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("planner_add_task_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isBangla) "স্মার্ট স্টাডি প্ল্যানার" else "Smart Study Planner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isBangla) "দৈনিক লক্ষ্য, চেকলিস্ট ও বিশ্লেষণমূলক রিপোর্ট" else "Daily checklist, pacing & daily report system",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Balance Workload button trigger
                OutlinedButton(
                    onClick = { viewModel.openCatchUpDialog() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("planner_quick_balance_btn")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isBangla) "ব্যালেন্স" else "Catch-up")
                }
            }

            // Material 3 Primary Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                PlannerTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                PlannerTab.DAILY_TASKS -> DailyTasksTabContent(
                    tasks = displayTasks,
                    allTasks = tasks,
                    subjects = subjects,
                    isBangla = isBangla,
                    currentFilter = taskFilter,
                    onFilterChange = { taskFilter = it },
                    onToggleTask = { viewModel.toggleTask(it) },
                    onDeleteTask = { viewModel.deleteTaskWithUndo(it) },
                    onOpenAddDialog = { showAddTaskDialog = true },
                    onOpenCatchUp = { viewModel.openCatchUpDialog() }
                )

                PlannerTab.DAILY_REPORT -> DailyReportTabContent(
                    report = dailyReport,
                    subjects = subjects,
                    selectedOffset = selectedDayOffset,
                    onSelectOffset = { selectedDayOffset = it },
                    isBangla = isBangla,
                    onCopyReport = { reportText ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("StudyOS Daily Report", reportText))
                        viewModel.showNotification("Daily study report copied to clipboard!")
                    }
                )

                PlannerTab.PACING -> PacingAndBurnoutTabContent(
                    workload = workload,
                    subjects = subjects,
                    topics = topics,
                    isBangla = isBangla,
                    onOpenCatchUp = { viewModel.openCatchUpDialog() }
                )
            }
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: 1L) }
        var estimatedMin by remember { mutableStateOf("45") }
        var priority by remember { mutableStateOf("High") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text(if (isBangla) "নতুন দৈনিক লক্ষ্য কাজ" else "Add Daily Study Task", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Description") },
                        placeholder = { Text("e.g. Master Binary Search Trees") },
                        modifier = Modifier.fillMaxWidth().testTag("add_task_title_input")
                    )

                    // Subject Selector Chips
                    Text("Subject:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subjects.take(3).forEach { sub ->
                            FilterChip(
                                selected = selectedSubjectId == sub.id,
                                onClick = { selectedSubjectId = sub.id },
                                label = { Text(sub.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = estimatedMin,
                        onValueChange = { estimatedMin = it },
                        label = { Text("Target Duration (Minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Priority:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Low", "Medium", "High").forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.addTask(
                                title = title.trim(),
                                subjectId = selectedSubjectId,
                                dueDateEpoch = System.currentTimeMillis() + 86400000L,
                                estimatedMin = estimatedMin.toIntOrNull() ?: 45,
                                priority = priority
                            )
                            showAddTaskDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_task_btn")
                ) {
                    Text("Save Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ------------------------------------------------------------------------------------------------
// TAB 1: DAILY TASKS CHECKLIST CONTENT
// ------------------------------------------------------------------------------------------------
@Composable
private fun DailyTasksTabContent(
    tasks: List<TaskEntity>,
    allTasks: List<TaskEntity>,
    subjects: List<SubjectEntity>,
    isBangla: Boolean,
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenCatchUp: () -> Unit
) {
    val totalCount = allTasks.size
    val completedCount = allTasks.count { it.isCompleted }
    val progressRatio = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val animatedProgress by animateFloatAsState(targetValue = progressRatio, label = "dailyProgress")

    val overdueCount = allTasks.count { !it.isCompleted && it.dueDateEpoch < System.currentTimeMillis() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Daily Progress Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            strokeWidth = 7.dp
                        )
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 7.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "${(progressRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBangla) "আজকের কাজের সমাপ্তি" else "Daily Completion Track",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$completedCount of $totalCount daily targets cleared",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        if (overdueCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.clickable { onOpenCatchUp() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$overdueCount overdue • Tap to balance",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Pending", "High", "Done").forEach { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter) },
                        leadingIcon = if (currentFilter == filter) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }
        }

        // Tasks Checklist
        if (tasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (currentFilter == "Done") "No completed tasks yet" else "All caught up!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ Add Daily Task' to schedule your next milestone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                val sub = subjects.firstOrNull { it.id == task.subjectId }
                val isOverdue = !task.isCompleted && task.dueDateEpoch < System.currentTimeMillis()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            task.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox for completion state
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { onToggleTask(task) },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("task_checkbox_${task.id}")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Subject tag
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = parseHexColor(sub?.colorHex ?: "#6C5CE7").copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = sub?.code ?: "SUB",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = parseHexColor(sub?.colorHex ?: "#6C5CE7"),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = "${task.estimatedMinutes}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text("•", style = MaterialTheme.typography.labelSmall)

                                // Priority tag
                                Text(
                                    text = task.priority,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = when (task.priority) {
                                        "High" -> MaterialTheme.colorScheme.error
                                        "Medium" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )

                                if (task.isOptional) {
                                    Text("•", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "OPTIONAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onDeleteTask(task) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// TAB 2: DAILY REPORT SYSTEM CONTENT
// ------------------------------------------------------------------------------------------------
@Composable
private fun DailyReportTabContent(
    report: DailyStudyReportData,
    subjects: List<SubjectEntity>,
    selectedOffset: Int,
    onSelectOffset: (Int) -> Unit,
    isBangla: Boolean,
    onCopyReport: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("daily_report_view"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Date Switcher Pills (Today, Yesterday, 2 Days Ago)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair(0, if (isBangla) "আজ" else "Today"),
                    Pair(-1, if (isBangla) "গতকাল" else "Yesterday"),
                    Pair(-2, if (isBangla) "২ দিন আগে" else "2 Days Ago")
                ).forEach { (offset, label) ->
                    FilterChip(
                        selected = selectedOffset == offset,
                        onClick = { onSelectOffset(offset) },
                        label = { Text(label) },
                        leadingIcon = if (selectedOffset == offset) {
                            { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }
        }

        // Daily Master Performance Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Report: ${report.dayLabel}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = report.dateString,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Daily Performance Grade Badge
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = report.performanceGrade,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "EFFICIENCY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = report.performanceSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Metric Bars
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Focus Time Bar
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Focus Time: ${report.totalFocusMinutes}m / ${report.targetDailyMinutes}m",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${(report.focusGoalRate * 100).toInt()}% Goal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { report.focusGoalRate },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Task Clearance Bar
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Task Clearance: ${report.completedTasksCount} of ${report.totalPlannedTasks} tasks",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${(report.taskCompletionRate * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { report.taskCompletionRate },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Daily Breakdown Grid
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PastelMetricCard(
                    title = "Primary Subject",
                    value = report.topSubjectName,
                    subtitle = "${report.topSubjectMinutes}m focused",
                    icon = Icons.Default.AutoStories,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                PastelMetricCard(
                    title = "Mindful State",
                    value = "${report.mood} (${report.energyRating}/5)",
                    subtitle = "Energy level",
                    icon = Icons.Default.SelfImprovement,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Formatted Text Report Preview & Copy Button
        item {
            val reportSummaryText = remember(report) {
                """
                📊 StudyOS 2.0 Daily Performance Report
                📅 Date: ${report.dateString} (${report.dayLabel})
                🏆 Daily Grade: ${report.performanceGrade}
                ⏱️ Focus Time: ${report.totalFocusMinutes}m / ${report.targetDailyMinutes}m (${(report.focusGoalRate * 100).toInt()}%)
                ✅ Tasks Completed: ${report.completedTasksCount}/${report.totalPlannedTasks} (${(report.taskCompletionRate * 100).toInt()}%)
                📚 Top Focus Area: ${report.topSubjectName} (${report.topSubjectMinutes}m)
                ⚡ Mood & Energy: ${report.mood} (${report.energyRating}/5)
                💡 Summary: ${report.performanceSummary}
                """.trimIndent()
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Formatted Daily Brief",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(
                            onClick = { onCopyReport(reportSummaryText) },
                            modifier = Modifier.testTag("copy_daily_report_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reportSummaryText,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onCopyReport(reportSummaryText) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy / Share Daily Report")
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// TAB 3: PACING & BURNOUT CONTENT
// ------------------------------------------------------------------------------------------------
@Composable
private fun PacingAndBurnoutTabContent(
    workload: com.example.logic.WorkloadSummary,
    subjects: List<SubjectEntity>,
    topics: List<com.example.data.models.TopicEntity>,
    isBangla: Boolean,
    onOpenCatchUp: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Burnout Shield Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (workload.burnoutRiskLevel) {
                        "Burnout Risk" -> MaterialTheme.colorScheme.errorContainer
                        "High Pressure" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pacing Status: ${workload.burnoutRiskLevel}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Nearest Exam Horizon: ${workload.daysUntilNearestExam} days",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (workload.burnoutRiskLevel.contains("Burnout") || workload.burnoutRiskLevel.contains("High")) Icons.Default.Shield else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = workload.burnoutAdvice,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOpenCatchUp,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBangla) "কাজের চাপ ব্যালেন্স করুন" else "Open Catch-up Balancer (3 Options)")
                    }
                }
            }
        }

        // Metrics Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PastelMetricCard(
                    title = "Optimal Daily",
                    value = "${workload.recommendedDailyHours}h / day",
                    subtitle = "Recommended pace",
                    icon = Icons.Default.Speed,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                PastelMetricCard(
                    title = "Syllabus Done",
                    value = "${(workload.syllabusCompletionRate * 100).toInt()}%",
                    subtitle = "${topics.count { it.status == "Completed" }} / ${topics.size} Topics",
                    icon = Icons.Default.MenuBook,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Subject Pacing Distribution
        item {
            SectionHeader(title = "Subject Syllabus Allocation")
        }

        items(subjects) { sub ->
            val subTopics = topics.filter { it.subjectId == sub.id }
            val completed = subTopics.count { it.status == "Completed" }
            val total = subTopics.size.coerceAtLeast(1)
            val subRatio = completed.toFloat() / total.toFloat()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sub.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${sub.completedHours}h / ${sub.targetHours}h (${(subRatio * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { subRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = parseHexColor(sub.colorHex)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HELPER: DAILY REPORT CALCULATION
// ------------------------------------------------------------------------------------------------
private fun computeDailyReport(
    dayOffset: Int,
    tasks: List<TaskEntity>,
    sessions: List<StudySessionEntity>,
    dailyNotes: List<DailyNoteEntity>,
    workloadHours: Float,
    subjects: List<SubjectEntity>
): DailyStudyReportData {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, dayOffset)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayFormat = SimpleDateFormat("EEE, MMM dd", Locale.US)
    val dateString = dateFormat.format(cal.time)
    val dayLabel = when (dayOffset) {
        0 -> "Today"
        -1 -> "Yesterday"
        else -> displayFormat.format(cal.time)
    }

    // Start and end of the target day in epoch ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val dayStartEpoch = cal.timeInMillis
    val dayEndEpoch = dayStartEpoch + 86_400_000L

    // Completed tasks completed on this day or due on this day
    val dayTasks = tasks.filter {
        (it.dueDateEpoch in dayStartEpoch until dayEndEpoch) ||
        (it.completedAtEpoch != null && it.completedAtEpoch in dayStartEpoch until dayEndEpoch)
    }
    val plannedCount = dayTasks.size.coerceAtLeast(1)
    val completedCount = dayTasks.count { it.isCompleted }
    val taskRate = completedCount.toFloat() / plannedCount.toFloat()

    // Sessions logged within this day
    val daySessions = sessions.filter { it.startedAtEpoch in dayStartEpoch until dayEndEpoch }
    val totalFocusMins = daySessions.sumOf { it.actualMinutes }
    val targetDailyMins = (workloadHours * 60).toInt().coerceAtLeast(90)
    val focusGoalRate = (totalFocusMins.toFloat() / targetDailyMins.toFloat()).coerceIn(0f, 1.5f)

    // Primary subject for the day
    val subjectMinutesMap = daySessions.groupBy { it.subjectId }
        .mapValues { (_, sList) -> sList.sumOf { it.actualMinutes } }
    val topSubjectId = subjectMinutesMap.maxByOrNull { it.value }?.key
    val topSubject = subjects.firstOrNull { it.id == topSubjectId }
    val topSubName = topSubject?.name ?: "General Focus"
    val topSubMins = subjectMinutesMap[topSubjectId] ?: totalFocusMins

    // Daily reflection entry
    val note = dailyNotes.firstOrNull { it.dateString == dateString }
    val mood = note?.mood ?: "Focused"
    val energy = note?.energyRating ?: 4

    // Calculate overall grade
    val combinedScore = (taskRate * 0.5f) + ((focusGoalRate.coerceAtMost(1f)) * 0.5f)
    val (grade, summary) = when {
        combinedScore >= 0.88f -> Pair(
            "A+",
            "Exceptional execution! You exceeded study benchmarks with deep focus."
        )
        combinedScore >= 0.72f -> Pair(
            "A",
            "High momentum day! Solid task clearance and focused attention."
        )
        combinedScore >= 0.55f -> Pair(
            "B+",
            "Steady progress made. A balanced effort keeping your syllabus moving forward."
        )
        combinedScore >= 0.35f -> Pair(
            "B",
            "Moderate study logged. Focus on clearing remaining high-priority items."
        )
        else -> Pair(
            "C+",
            "Light study day. Use tomorrow's target windows to regain rhythm."
        )
    }

    return DailyStudyReportData(
        dateString = dateString,
        dayLabel = dayLabel,
        totalPlannedTasks = plannedCount,
        completedTasksCount = completedCount,
        taskCompletionRate = taskRate.coerceIn(0f, 1f),
        totalFocusMinutes = totalFocusMins,
        targetDailyMinutes = targetDailyMins,
        focusGoalRate = focusGoalRate.coerceIn(0f, 1f),
        performanceGrade = grade,
        performanceSummary = summary,
        topSubjectName = topSubName,
        topSubjectMinutes = topSubMins,
        mood = mood,
        energyRating = energy
    )
}