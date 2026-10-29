package com.shivam.glasslauncher.widgets

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.ui.glass.*
import java.util.UUID

@Composable
fun ChecklistWidget(inst: WidgetInstance, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val preview = inst.id.startsWith(PREVIEW_PREFIX)
    val tasks = remember(inst.config, preview) {
        if (preview) listOf(ChecklistItem("1", "Plan the day", true), ChecklistItem("2", "Make time for a walk"), ChecklistItem("3", "Finish one important thing"))
        else Checklist.decode(inst[WidgetKeys.CHECKLIST])
    }
    val repo = remember(context) { WidgetRepository.get(context) }
    val interactive = !preview && env?.editMode != true
    WidgetFrame(modifier, padding = 14.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GlassText("My checklist", style = GlassType.Headline)
                    GlassText(if (tasks.isEmpty()) "Make room for what matters" else "${tasks.count { it.done }} of ${tasks.size} complete", style = GlassType.Caption, secondary = true)
                }
                GlassIconButton(GlassIcons.Plus, onClick = { if (interactive) env?.editWidget?.invoke(inst.id) }, size = 48.dp, modifier = Modifier.semantics { contentDescription = "Edit checklist" })
            }
            if (tasks.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    GlassText("Tap + to add your first task", style = GlassType.Callout, secondary = true)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(tasks, key = { it.id }) { task ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .semantics { stateDescription = if (task.done) "Completed" else "Not completed" }
                                .clickable(enabled = interactive, role = Role.Checkbox) {
                                    val latest = Checklist.decode(repo.instance(inst.id)?.get(WidgetKeys.CHECKLIST))
                                    repo.setConfig(inst.id, WidgetKeys.CHECKLIST, Checklist.encode(latest.map { if (it.id == task.id) it.copy(done = !it.done) else it }))
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassText(if (task.done) "✓" else "○", Modifier.width(28.dp), style = GlassType.Title, color = if (task.done) currentAccent() else LocalGlassContentColor.current)
                            GlassText(task.text, style = GlassType.Callout.copy(textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None), secondary = task.done, maxLines = if (size == WidgetSize.LARGE) 2 else 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    val tasks = remember(inst.config) { Checklist.decode(inst[WidgetKeys.CHECKLIST]) }
    var draft by rememberSaveable(inst.id) { mutableStateOf("") }
    fun save(items: List<ChecklistItem>) = widgets.repo.setConfig(inst.id, WidgetKeys.CHECKLIST, Checklist.encode(items))
    fun add() {
        val latest = Checklist.decode(widgets.repo.instance(inst.id)?.get(WidgetKeys.CHECKLIST))
        if (draft.isNotBlank() && latest.size < Checklist.MAX_ITEMS) {
            save(latest + ChecklistItem(UUID.randomUUID().toString(), draft.trim()))
            draft = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassTextField(draft, { draft = it.take(Checklist.MAX_TEXT) }, placeholder = "Add a task", leadingIcon = null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }))
        GlassButton("Add task", onClick = { add() }, enabled = draft.isNotBlank() && tasks.size < Checklist.MAX_ITEMS, prominent = true)
        GlassListGroup(title = "${tasks.size} / ${Checklist.MAX_ITEMS} tasks", footer = "Saved on this device. Tap a task on the widget to complete it.") {
            tasks.forEach { task ->
                GlassRow(task.text, subtitle = if (task.done) "Complete" else "To do", showChevron = false) {
                    GlassIconButton(GlassIcons.Minus, onClick = { save(tasks.filterNot { it.id == task.id }) }, size = 48.dp, modifier = Modifier.semantics { contentDescription = "Remove ${task.text}" })
                }
            }
            if (tasks.any { it.done }) GlassRow("Clear completed", onClick = { save(tasks.filterNot { it.done }) })
        }
    }
}

/** The Clock app owns the actual alarm, so a launcher restart cannot lose a running timer. */
@Composable
fun FocusWidget(inst: WidgetInstance, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val repo = remember(context) { WidgetRepository.get(context) }
    val presets = listOf(25, 50, 5)
    val minutes = inst[WidgetKeys.FOCUS_MINUTES]?.toIntOrNull()?.takeIf { it in presets } ?: 25
    val interactive = env?.editMode != true && !inst.id.startsWith(PREVIEW_PREFIX)
    WidgetFrame(modifier, padding = 14.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GlassText(if (minutes == 5) "Take a breath" else "Time to focus", style = GlassType.Headline)
                    GlassText("Timer opens in your Clock app", style = GlassType.Caption, secondary = true)
                }
                GlassText("$minutes", style = GlassType.LargeTitle.copy(fontSize = 36.sp))
                GlassText(" min", style = GlassType.Caption, secondary = true)
            }
            GlassSegmented(listOf("25 min", "50 min", "5 min"), presets.indexOf(minutes), {
                if (interactive) repo.setConfig(inst.id, WidgetKeys.FOCUS_MINUTES, presets[it].toString())
            })
            GlassButton("Open timer", onClick = {
                if (interactive) openWidgetAction(context, env, Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, if (minutes == 5) "Take a break" else "Focus time")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false))
            }, modifier = Modifier.fillMaxWidth(), prominent = true, enabled = interactive || inst.id.startsWith(PREVIEW_PREFIX))
        }
    }
}

@Composable
fun QuickActionsWidget(inst: WidgetInstance, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = LocalWidgetEnv.current
    val interactive = env?.editMode != true && !inst.id.startsWith(PREVIEW_PREFIX)
    val actions = remember {
        listOf("Internet" to Settings.Panel.ACTION_INTERNET_CONNECTIVITY, "Sound" to Settings.Panel.ACTION_VOLUME,
            "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "Alarms" to AlarmClock.ACTION_SHOW_ALARMS)
    }
    WidgetFrame(modifier, padding = 14.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassText("Quick actions", style = GlassType.Headline)
            actions.chunked(2).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, action) ->
                        GlassButton(label, onClick = { if (interactive) openWidgetAction(context, env, Intent(action)) },
                            modifier = Modifier.weight(1f).fillMaxHeight(), enabled = interactive || inst.id.startsWith(PREVIEW_PREFIX))
                    }
                }
            }
        }
    }
}

private fun openWidgetAction(context: Context, env: WidgetEnv?, intent: Intent) {
    try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { env?.toast?.invoke("This action isn’t available on this device") }
}
