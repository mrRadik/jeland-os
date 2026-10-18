package ru.jeland.os.ui.automation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.BuildConfig
import ru.jeland.os.R
import ru.jeland.os.core.automation.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.util.UUID

@Composable fun AutomationsScreen(model:AutomationViewModel,onEdit:(UUID?)->Unit) {
    val rules by model.rules.collectAsStateWithLifecycle();val loading by model.loading.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable {mutableStateOf<String?>(null)}
    var debug by rememberSaveable {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().testTag("screen-automations"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SectionTitle("Автоматизации","Персональные сценарии комфорта")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("+ Создать автоматизацию",Modifier.weight(1f).testTag("automation-create"),selected=true){onEdit(null)}
            if(BuildConfig.DEBUG)Control("Тестовая поездка · MOCK",Modifier.testTag("automation-debug-toggle"),selected=debug){debug=!debug}
        }
        error?.let {Label(it,small=true)}
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("automations-scroll"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(debug&&BuildConfig.DEBUG)AutomationDebugPanel(model)
            Label("Применяются при новой сессии. Пока только MOCK — без управления автомобилем.",small=true,muted=true)
            when {loading->Label("Загружаем сохранённые правила…")
                rules.isEmpty()->AutomationEmptyState {onEdit(null)}
                else->rules.forEach {rule->key(rule.id) {AutomationCard(rule,{model.enabled(rule.id,it)},{onEdit(rule.id)},{deleteId=rule.id.toString()})}}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    rules.firstOrNull {it.id.toString()==deleteId}?.let {rule->DeleteAutomationDialog(rule.name,{deleteId=null}){model.delete(rule.id);deleteId=null}}
}
@Composable fun AutomationCard(rule:AutomationRule,onEnabled:(Boolean)->Unit,onEdit:()->Unit,onDelete:()->Unit) {
    Panel(Modifier.fillMaxWidth().animateContentSize().testTag("automation-card-${rule.id}")) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                AutomationComponentArt(rule.componentType,Modifier.size(105.dp,135.dp),rule.action as? SunshadeAction?:SunshadeAction.OPEN_HALF)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(rule.name,color=Jeland.colors.text,style=Jeland.heading)
                    Label("${rule.componentType.title} · приоритет ${rule.priority}",small=true,muted=true)
                    Label(rule.conditionSummary(),Modifier.testTag("automation-condition-${rule.id}"),small=true)
                    Label(rule.action.summary(),small=true,muted=true)
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                SettingsToggle("Активна","Изменения — со следующей сессии",rule.enabled,Modifier.weight(1f).testTag("automation-enabled-${rule.id}")){onEnabled(!rule.enabled)}
                RoundControl(JIcons.Edit,"Редактировать ${rule.name}",Modifier.testTag("automation-edit-${rule.id}"),onClick=onEdit)
                RoundControl(JIcons.Delete,"Удалить ${rule.name}",Modifier.testTag("automation-delete-${rule.id}"),onClick=onDelete)
            }
        }
    }
}
@Composable fun AutomationEmptyState(onCreate:()->Unit) {
    Panel(Modifier.fillMaxWidth().testTag("automations-empty")) {
        Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Glyph(JIcons.Automations,"Автоматизации",Modifier.size(64.dp),active=true)
            SectionTitle("Комфорт по вашим правилам","Создайте первый сценарий для сидений или панорамной шторки")
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                AutomationComponentArt(ComponentType.SEATS,Modifier.size(90.dp,105.dp))
                AutomationComponentArt(ComponentType.SUNSHADE,Modifier.size(125.dp,105.dp))
            }
            Control("Создать первую автоматизацию",Modifier.testTag("automation-empty-create"),selected=true,onClick=onCreate)
        }
    }
}
@Composable fun DeleteAutomationDialog(name:String,onDismiss:()->Unit,onDelete:()->Unit) {
    Dialog(onDismissRequest=onDismiss) {
        Panel(Modifier.fillMaxWidth().testTag("automation-delete-dialog")) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                SectionTitle("Удалить автоматизацию?","«$name» будет удалена. Журнал прошлых сессий останется.")
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Control("Отмена",Modifier.weight(1f).testTag("automation-delete-cancel"),onClick=onDismiss)
                    Control("Удалить",Modifier.weight(1f).testTag("automation-delete-confirm"),true,accent=Jeland.colors.critical,onClick=onDelete)
                }
            }
        }
    }
}
@Composable fun AutomationEditorScreen(model:AutomationEditorViewModel,onClose:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val draft=state.draft
    LaunchedEffect(state.saved){if(state.saved)onClose()}
    BackHandler(enabled=!state.saving,onBack=onClose)
    Column(Modifier.fillMaxSize().testTag("automation-editor"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("Настройка автоматизации","Сохранённые изменения применятся со следующей новой сессии")
        if(state.errors.isNotEmpty())Panel(Modifier.fillMaxWidth().testTag("automation-errors")) {
            Label(state.errors.joinToString("\n"),Modifier.padding(14.dp),small=true)
        }
        if(state.loading)Label("Загружаем правило…")
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("automation-editor-scroll"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            AutomationTextField("Название · ${draft.name.length}/50",draft.name,model::name,"automation-name",enabled=!state.saving&&!state.loading)
            ComponentPicker(draft.component,!state.saving,model::component)
            if(draft.component==ComponentType.SEATS) {
                TemperatureTriggerEditor(draft.trigger,draft.threshold,!state.saving,model::trigger,model::threshold)
                SeatsActionEditor(draft.seats,!state.saving,model::seat)
            }else SunshadeActionEditor(draft.shade,!state.saving,model::shade)
            Panel(Modifier.fillMaxWidth()) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {Label("Приоритет: ${draft.priority}");Label("Выше — важнее. При равенстве побеждает последнее изменённое правило для всего компонента.",small=true,muted=true)}
                RoundControl(JIcons.Remove,"Уменьшить приоритет",Modifier.testTag("automation-priority-minus"),enabled=!state.saving&&draft.priority>0){model.priority(-1)}
                RoundControl(JIcons.Add,"Увеличить приоритет",Modifier.testTag("automation-priority-plus"),enabled=!state.saving&&draft.priority<10){model.priority(1)}
            }}
            Label("MOCK: реальные команды автомобилю не отправляются.",small=true,muted=true)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("Отмена",Modifier.weight(1f).testTag("automation-cancel"),enabled=!state.saving,onClick=onClose)
            Control(if(state.saving)"Сохраняем…"else"Сохранить",Modifier.weight(1f).testTag("automation-save"),true,
                enabled=!state.saving&&!state.loading&&!state.unavailable,onClick=model::save)
        }
    }
}
@Composable fun ComponentPicker(selected:ComponentType,enabled:Boolean,onSelect:(ComponentType)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ComponentType.entries.forEach {component->
            Panel(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().semantics {this.selected=component==selected}.clickable(enabled=enabled){onSelect(component)}.testTag("automation-component-${component.name.lowercase()}").padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AutomationComponentArt(component,Modifier.size(60.dp,70.dp))
                    Column(Modifier.weight(1f)) {Label(component.title);Text(if(component==selected)"Выбрано"else"Выбрать",color=if(component==selected)Jeland.colors.accent else Jeland.colors.muted,style=Jeland.caption)}
                }
            }
        }
    }
}
@Composable fun TemperatureTriggerEditor(trigger:TriggerType,value:String,enabled:Boolean,onTrigger:(TriggerType)->Unit,onValue:(String)->Unit) {
    Panel(Modifier.fillMaxWidth().testTag("automation-temperature-trigger")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Label("Наружная температура · °C")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Control("Ниже",Modifier.weight(1f).testTag("automation-below"),trigger==TriggerType.BELOW,enabled=enabled){onTrigger(TriggerType.BELOW)}
                Control("Выше",Modifier.weight(1f).testTag("automation-above"),trigger==TriggerType.ABOVE,enabled=enabled){onTrigger(TriggerType.ABOVE)}
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                RoundControl(JIcons.Remove,"Уменьшить температурный порог",enabled=enabled){onValue(((value.replace(',','.').toDoubleOrNull()?:0.0)-1).coerceIn(-60.0,60.0).toString())}
                Box(Modifier.weight(1f)){AutomationTextField("Порог от −60 до +60 °C",value,onValue,"automation-threshold",KeyboardType.Text,enabled)}
                RoundControl(JIcons.Add,"Увеличить температурный порог",enabled=enabled){onValue(((value.replace(',','.').toDoubleOrNull()?:0.0)+1).coerceIn(-60.0,60.0).toString())}
            }
            Label("Источник пока MOCK. Нет свежей температуры — сценарий пропускается.",small=true,muted=true)
        }
    }
}
@Composable fun SeatsActionEditor(action:SeatsAction,enabled:Boolean,onSet:(SeatParameter,SeatLevel)->Unit) {
    Column(Modifier.testTag("automation-seats-actions"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf("Водитель" to listOf(SeatParameter.DRIVER_HEATING,SeatParameter.DRIVER_VENTILATION),
            "Передний пассажир" to listOf(SeatParameter.PASSENGER_HEATING,SeatParameter.PASSENGER_VENTILATION),
            "Заднее левое" to listOf(SeatParameter.REAR_LEFT_HEATING),"Заднее правое" to listOf(SeatParameter.REAR_RIGHT_HEATING)).forEach {(name,parameters)->
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    AutomationComponentArt(ComponentType.SEATS,Modifier.size(65.dp,100.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Label(name)
                        parameters.forEach {p->SeatActionRow(p,action.values()[p.ordinal],enabled){onSet(p,it)}}
                    }
                }
            }
        }
        Label("Не изменять сохраняет текущий режим; 0 выключает его. Включение нагрева выбирает 0 для вентиляции и наоборот. Задний ряд — только подогрев.",small=true,muted=true)
    }
}
@Composable fun SeatActionRow(parameter:SeatParameter,selected:SeatLevel,enabled:Boolean,onSelect:(SeatLevel)->Unit) {
    val accent=if(parameter.heating)Jeland.colors.warm else Jeland.colors.accent
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(if(parameter.heating)"Подогрев"else"Вентиляция",color=accent,style=Jeland.caption)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            SeatLevel.entries.forEach {level->Control(level.title,Modifier.weight(if(level==SeatLevel.KEEP)2.2f else 1f)
                .testTag("automation-${parameter.name.lowercase()}-${level.name.lowercase()}"),selected==level,accent,enabled){onSelect(level)}}
        }
    }
}
@Composable fun SunshadeActionEditor(selected:SunshadeAction,enabled:Boolean,onSelect:(SunshadeAction)->Unit) {
    Column(Modifier.testTag("automation-sunshade-actions"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SectionTitle("Панорамная шторка","Один раз при новой сессии, только при безопасных условиях. Без температуры.")
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            SunshadeAction.entries.forEach {action->
                Panel(Modifier.weight(1f).border(1.dp,if(selected==action)Jeland.colors.accent else Jeland.colors.outline,Jeland.card)
                    .semantics {this.selected=selected==action}.clickable(enabled=enabled){onSelect(action)}.testTag("automation-shade-${action.name.lowercase()}")) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        AutomationComponentArt(ComponentType.SUNSHADE,Modifier.fillMaxWidth().height(140.dp),action)
                        Label(action.title);Label(if(action==selected)"Выбрано"else"Выбрать положение",small=true,muted=action!=selected)
                    }
                }
            }
        }
    }
}
@Composable fun AutomationComponentArt(type:ComponentType,modifier:Modifier,position:SunshadeAction=SunshadeAction.OPEN_HALF) {
    if(type==ComponentType.SEATS)Image(painterResource(R.drawable.seat_jeland),null,modifier,contentScale=ContentScale.Fit)
    else {
        val c=Jeland.colors
        val fraction by animateFloatAsState(if(position==SunshadeAction.OPEN_FULL)1f else .5f,tween(Jeland.animation.door),label="automation-roof")
        Canvas(modifier) {
            val frame=Rect(size.width*.10f,size.height*.14f,size.width*.9f,size.height*.86f)
            drawRoundRect(Brush.linearGradient(listOf(c.outline,c.raised,c.outline)),frame.topLeft,frame.size,CornerRadius(size.minDimension*.14f))
            val glass=Rect(frame.left+4.dp.toPx(),frame.top+5.dp.toPx(),frame.right-4.dp.toPx(),frame.bottom-5.dp.toPx())
            drawRoundRect(Brush.linearGradient(listOf(c.deep,c.map,c.raised)),glass.topLeft,glass.size,CornerRadius(size.minDimension*.10f))
            drawLine(c.artMuted.copy(alpha=.4f),Offset(glass.left+glass.width*.12f,glass.top+glass.height*.12f),Offset(glass.right-glass.width*.15f,glass.bottom-glass.height*.12f),2.dp.toPx())
            if(fraction<.99f) {
                val cover=Size(glass.width*(1-fraction),glass.height)
                drawRoundRect(Brush.horizontalGradient(listOf(c.raised,c.outline,c.raised)),glass.topLeft,cover,CornerRadius(6.dp.toPx()))
                drawLine(c.accent,Offset(glass.left+cover.width,glass.top+4.dp.toPx()),Offset(glass.left+cover.width,glass.bottom-4.dp.toPx()),2.dp.toPx())
            }
            drawRoundRect(c.accent.copy(alpha=.65f),frame.topLeft,frame.size,CornerRadius(size.minDimension*.14f),style=Stroke(1.dp.toPx()))
        }
    }
}
@Composable fun AutomationTextField(title:String,value:String,onValue:(String)->Unit,tag:String,keyboard:KeyboardType=KeyboardType.Text,enabled:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Label(title,small=true,muted=true)
        BasicTextField(value,onValue,Modifier.fillMaxWidth().heightIn(min=56.dp).clip(Jeland.control).background(Jeland.colors.raised)
            .border(1.dp,Jeland.colors.outline,Jeland.control).padding(14.dp).testTag(tag),enabled=enabled,singleLine=true,
            textStyle=Jeland.body.copy(color=Jeland.colors.text),cursorBrush=SolidColor(Jeland.colors.accent),keyboardOptions=KeyboardOptions(keyboardType=keyboard),
            decorationBox={inner->Box {if(value.isEmpty())Text("Введите значение",color=Jeland.colors.unknown,style=Jeland.body);inner()}})
    }
}
@Composable private fun AutomationDebugPanel(model:AutomationViewModel) {
    val temperature by model.temperature.collectAsStateWithLifecycle();val comfort by model.comfort.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle();val summary by model.summary.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle();val results by model.results.collectAsStateWithLifecycle()
    var input by rememberSaveable {mutableStateOf("5")}
    Panel(Modifier.fillMaxWidth().testTag("automation-debug")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SectionTitle("Тестовая поездка · MOCK","Отдельная модель. Кнопки ниже не управляют автомобилем.")
            Label(temperature?.let {"Температура: ${formatCelsius(it.celsius)} · ${if(it.fresh(System.currentTimeMillis()))"актуальна"else"устарела"}"}?:"Температура неизвестна",Modifier.testTag("automation-debug-temperature"),small=true)
            AutomationTextField("Тестовая температура · °C",input,{input=it.take(8)},"automation-debug-input",KeyboardType.Text,!busy)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Control("Применить",Modifier.weight(1f).testTag("automation-debug-apply"),enabled=!busy){model.setTemperature(input)}
                Control("Нет данных",Modifier.weight(1f).testTag("automation-debug-unknown"),enabled=!busy,onClick=model::noTemperature)
                Control("Просрочить",Modifier.weight(1f).testTag("automation-debug-stale"),enabled=!busy&&temperature!=null,onClick=model::staleTemperature)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Control(if(comfort.connected)"Связь есть"else"Нет связи",Modifier.weight(1f),comfort.connected,enabled=!busy,onClick=model::toggleConnection)
                Control(if(comfort.known)"Состояние известно"else"Неизвестное состояние",Modifier.weight(1f),comfort.known,enabled=!busy,onClick=model::toggleKnown)
                Control(if(comfort.parked)"Стоянка"else"Движение",Modifier.weight(1f).testTag("automation-debug-parked"),comfort.parked,enabled=!busy,onClick=model::toggleParked)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Control("Новая сессия",Modifier.weight(1f).testTag("automation-debug-new"),true,enabled=!busy){model.simulate(true)}
                Control("Повторить текущую",Modifier.weight(1f).testTag("automation-debug-repeat"),enabled=!busy&&session!=null){model.simulate(false)}
            }
            Label(summary,Modifier.testTag("automation-debug-summary"),small=true)
            session?.let {Label("Сессия ${it.id.toString().take(8)}",small=true,muted=true)}
            results.forEach {run->
                Panel(Modifier.fillMaxWidth().testTag("automation-result-${run.ruleId}")) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Label("${run.ruleName}: ${run.status.title}",small=true)
                        Label(run.message,small=true,muted=true)
                        if(run.commandSummary.isNotBlank())Label(run.commandSummary,small=true)
                    }
                }
            }
            Label("Состояние тестовой модели",small=true)
            Label(SeatParameter.entries.joinToString("\n"){"${it.title}: ${comfort.seats[it]}"}+"\nШторка: ${comfort.shade?.title?:"не изменялась"}",Modifier.testTag("automation-debug-comfort"),small=true,muted=true)
            Control("Ручное изменение · подогрев водителя",Modifier.testTag("automation-debug-manual"),enabled=!busy,onClick=model::manualDriverHeat)
            Label("KEEP сохраняет ручное значение. Повтор сессии не перезаписывает его. Исполнение не является подтверждением физического результата.",small=true,muted=true)
        }
    }
}
