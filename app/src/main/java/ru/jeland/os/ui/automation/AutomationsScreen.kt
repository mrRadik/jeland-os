package ru.jeland.os.ui.automation

import ru.jeland.os.localization.LocalizedText as Text

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.R
import ru.jeland.os.core.automation.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.util.UUID
import kotlin.math.roundToInt

@Composable fun AutomationsScreen(model:AutomationViewModel,onEdit:(UUID?)->Unit) {
    val rules by model.rules.collectAsStateWithLifecycle();val loading by model.loading.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle();val results by model.results.collectAsStateWithLifecycle()
    val trip by model.trip.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val temperature by model.temperature.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().testTag("screen-automations"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SectionTitle("Автоматизации","Персональные сценарии комфорта")
        Panel(Modifier.fillMaxWidth().testTag("automation-trip-monitor")) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                SettingsToggle("Автоматический запуск сценариев","По двигателю или движению — выбирается в каждом сценарии",trip.enabled,
                    Modifier.testTag("automation-trip-enabled")){model.tripDetection(!trip.enabled)}
                if(ru.jeland.os.BuildConfig.DEMO_FEATURES)Label(trip.detail,small=true,muted=true)
                Label(if(link.demo)"DEMO: команды автомобилю недоступны. Переключитесь в режим ГУ."
                    else if(!link.connected||!link.writesEnabled)"Нет активного подключения к ГУ."
                    else "Реальное управление подключено. Запуск и пробуждение приложения сами по себе сценарии не выполняют.",small=true,muted=true)
                Label(temperature?.takeIf {it.fresh(System.currentTimeMillis())}?.let {"За бортом: ${formatCelsius(it.celsius)}"}?:"Нет свежей наружной температуры: температурные сценарии не выполнятся.",small=true,muted=true)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("+ Создать автоматизацию",Modifier.weight(1f).testTag("automation-create"),selected=true){onEdit(null)}
        }
        error?.let {Label(it,small=true)}
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("automations-scroll"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Label("Событие запуска выбирается в каждом сценарии. Шторка работает и при запуске двигателя, и после начала движения.",small=true,muted=true)
            when {loading->Label("Загружаем сохранённые правила…")
                rules.isEmpty()->AutomationEmptyState {onEdit(null)}
                else->rules.forEach {rule->key(rule.id) {AutomationCard(rule,{model.enabled(rule.id,it)},{onEdit(rule.id)},{deleteId=rule.id.toString()},!busy&&!link.demo&&link.connected&&link.writesEnabled,{model.runManually(rule.id)},results.firstOrNull {it.ruleId==rule.id})}}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    rules.firstOrNull {it.id.toString()==deleteId}?.let {rule->DeleteAutomationDialog(rule.name,{deleteId=null}){model.delete(rule.id);deleteId=null}}
}
@Composable fun AutomationCard(rule:AutomationRule,onEnabled:(Boolean)->Unit,onEdit:()->Unit,onDelete:()->Unit,
    canRun:Boolean,onRun:()->Unit,result:AutomationRun?=null) {
    Panel(Modifier.fillMaxWidth().animateContentSize().testTag("automation-card-${rule.id}")) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(rule.name,color=Jeland.colors.text,style=Jeland.heading)
            Label(rule.conditionSummary(),Modifier.testTag("automation-condition-${rule.id}"),small=true)
            rule.actions.forEach {action->
                Row(Modifier.fillMaxWidth().testTag("automation-device-${rule.id}-${action.component.name.lowercase()}"),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    AutomationComponentArt(action.component,Modifier.size(60.dp,65.dp),action as? SunshadeAction?:SunshadeAction.OPEN_HALF)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Label(action.component.title)
                        Label(action.summary(),small=true,muted=true)
                    }
                }
            }
            if(rule.triggerType!=TriggerType.MANUAL)SettingsToggle("Автоматический запуск","Приоритет ${rule.priority} · ручная кнопка доступна всегда",rule.enabled,
                Modifier.testTag("automation-enabled-${rule.id}")){onEnabled(!rule.enabled)}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Control("Запустить вручную",Modifier.weight(1f).testTag("automation-run-${rule.id}"),selected=true,enabled=canRun,onClick=onRun)
                RoundControl(JIcons.Edit,"Редактировать ${rule.name}",Modifier.testTag("automation-edit-${rule.id}"),onClick=onEdit)
                RoundControl(JIcons.Delete,"Удалить ${rule.name}",Modifier.testTag("automation-delete-${rule.id}"),onClick=onDelete)
            }
            if(result!=null)Label("Последний запуск · ${result.status.title} · ${result.message}",Modifier.testTag("automation-card-result-${rule.id}"),small=true,muted=true)
        }
    }
}
@Composable fun AutomationEmptyState(onCreate:()->Unit) {
    Panel(Modifier.fillMaxWidth().testTag("automations-empty")) {
        Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Glyph(JIcons.Automations,"Автоматизации",Modifier.size(64.dp),active=true)
            SectionTitle("Комфорт по вашим правилам","Четыре отдельных сиденья, панорамная шторка и подогрев руля")
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                AutomationComponentArt(ComponentType.DRIVER_SEAT,Modifier.size(90.dp,105.dp))
                AutomationComponentArt(ComponentType.SUNSHADE,Modifier.size(125.dp,105.dp))
                AutomationComponentArt(ComponentType.STEERING_WHEEL,Modifier.size(90.dp,105.dp))
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
        SectionTitle("Настройка автоматизации","Название, способ запуска и устройства сценария")
        if(state.errors.isNotEmpty())Panel(Modifier.fillMaxWidth().testTag("automation-errors")) {
            Label(state.errors.joinToString("\n"),Modifier.padding(14.dp),small=true)
        }
        if(state.loading)Label("Загружаем правило…")
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("automation-editor-scroll"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            AutomationTextField("Название · ${draft.name.length}/50",draft.name,model::name,"automation-name",enabled=!state.saving&&!state.loading)
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Label("Способ запуска")
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Control("Вручную",Modifier.weight(1f).testTag("automation-mode-manual"),draft.trigger==TriggerType.MANUAL,enabled=!state.saving){model.trigger(TriggerType.MANUAL)}
                        Control("По температуре",Modifier.weight(1f).testTag("automation-mode-temperature"),draft.trigger in listOf(TriggerType.BELOW,TriggerType.ABOVE),enabled=!state.saving){if(draft.trigger !in listOf(TriggerType.BELOW,TriggerType.ABOVE))model.trigger(TriggerType.BELOW)}
                        Control("Без условий",Modifier.weight(1f).testTag("automation-mode-auto"),draft.trigger==TriggerType.SESSION_START,enabled=!state.saving){model.trigger(TriggerType.SESSION_START)}
                    }
                    Label(if(draft.trigger==TriggerType.MANUAL)"Только по кнопке, без проверки температуры."
                        else "Проверка один раз по выбранному событию. Пробуждение ГУ само по себе не является запуском двигателя.",small=true,muted=true)
                }
            }
            if(draft.trigger!=TriggerType.MANUAL)Panel(Modifier.fillMaxWidth().testTag("automation-launch-event")) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Label("Когда запускать")
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        LaunchEvent.entries.forEach {event->Control(event.title,Modifier.weight(1f).testTag("automation-event-${event.name.lowercase()}"),
                            selected=draft.launchEvent==event,enabled=!state.saving){model.launchEvent(event)}}
                    }
                    Label(if(draft.launchEvent==LaunchEvent.ENGINE_START)"После подтверждения работы двигателя, не дожидаясь движения. Для всех выбранных устройств, включая шторку."
                        else "После остановки и устойчивого начала движения. Открытие приложения на ходу не считается новым стартом.",small=true,muted=true)
                }
            }
            if(draft.trigger in listOf(TriggerType.BELOW,TriggerType.ABOVE))TemperatureTriggerEditor(draft.trigger,draft.threshold,!state.saving,model::trigger,model::threshold)
            ComponentPicker(draft.actions.map {it.component},!state.saving,model::toggleDevice)
            if(draft.actions.isEmpty())Label("Добавьте устройства из списка выше и настройте их действия.",small=true,muted=true)
            draft.actions.forEach {action->key(action.component) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Label(action.component.title,Modifier.weight(1f))
                        RoundControl(JIcons.Delete,"Убрать ${action.component.title}",Modifier.testTag("automation-remove-${action.component.name.lowercase()}"),enabled=!state.saving){model.removeDevice(action.component)}
                    }
                    when(action) {
                        is SeatAction->SeatActionEditor(action,!state.saving,model::seat)
                        is SunshadeAction->SunshadeActionEditor(action,!state.saving,model::shade)
                        is SteeringWheelAction->Panel(Modifier.fillMaxWidth().testTag("automation-steering-actions")) {
                            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                                AutomationComponentArt(ComponentType.STEERING_WHEEL,Modifier.size(72.dp))
                                Column(Modifier.weight(1f)) {
                                    Label(action.title)
                                    Label("Если подогрев уже включён, повторная команда не нужна.",small=true,muted=true)
                                }
                            }
                        }
                    }
                }
            }}
            Panel(Modifier.fillMaxWidth()) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {Label("Приоритет: ${draft.priority}");Label("Выше — важнее. При равенстве побеждает последнее изменённое правило. Если устройство общее, уступивший сценарий пропускается целиком.",small=true,muted=true)}
                RoundControl(JIcons.Remove,"Уменьшить приоритет",Modifier.testTag("automation-priority-minus"),enabled=!state.saving&&draft.priority>0){model.priority(-1)}
                RoundControl(JIcons.Add,"Увеличить приоритет",Modifier.testTag("automation-priority-plus"),enabled=!state.saving&&draft.priority<10){model.priority(1)}
            }}
            Label("Сценарий управляет автомобилем. Перед отправкой проверяются связь и доступность устройств.",small=true,muted=true)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("Отмена",Modifier.weight(1f).testTag("automation-cancel"),enabled=!state.saving,onClick=onClose)
            Control(if(state.saving)"Сохраняем…"else"Сохранить",Modifier.weight(1f).testTag("automation-save"),true,
                enabled=!state.saving&&!state.loading&&!state.unavailable,onClick=model::save)
        }
    }
}
@Composable fun ComponentPicker(selected:List<ComponentType>,enabled:Boolean,onSelect:(ComponentType)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Label("Устройства сценария")
        AutomationDevices.available.chunked(2).forEach {devices->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            devices.forEach {component->
                val added=component in selected
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().semantics {this.selected=added}.clickable(enabled=enabled){onSelect(component)}
                        .testTag("automation-component-${component.name.lowercase()}").padding(14.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        AutomationComponentArt(component,Modifier.size(60.dp,65.dp))
                        Label(component.title,small=true)
                        Text(if(added)"− Убрать"else"+ Добавить",color=if(added)Jeland.colors.accent else Jeland.colors.muted,style=Jeland.caption)
                    }
                }
            }
            }
        }
    }
}
@Composable fun TemperatureTriggerEditor(trigger:TriggerType,value:String,enabled:Boolean,onTrigger:(TriggerType)->Unit,onValue:(String)->Unit) {
    val temperature=(value.trim().replace(',','.').toFloatOrNull()?.takeIf {it.isFinite()}?:5f).coerceIn(-60f,60f)
    Panel(Modifier.fillMaxWidth().testTag("automation-temperature-trigger")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Label("Наружная температура",Modifier.weight(1f))
                Text(formatCelsius(temperature.toDouble()),color=Jeland.colors.accent,style=Jeland.heading,
                    modifier=Modifier.testTag("automation-threshold-value"))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Control("Ниже",Modifier.weight(1f).testTag("automation-below"),trigger==TriggerType.BELOW,enabled=enabled){onTrigger(TriggerType.BELOW)}
                Control("Выше",Modifier.weight(1f).testTag("automation-above"),trigger==TriggerType.ABOVE,enabled=enabled){onTrigger(TriggerType.ABOVE)}
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                RoundControl(JIcons.Remove,"Уменьшить температурный порог",Modifier.testTag("automation-threshold-minus"),enabled=enabled&&temperature> -60f){onValue((temperature-1f).coerceIn(-60f,60f).toString())}
                Column(Modifier.weight(1f)) {
                    JelandSlider(temperature,{onValue(it.roundToInt().coerceIn(-60,60).toString())},-60f..60f,
                        Modifier.testTag("automation-threshold").semantics {contentDescription="Порог наружной температуры"},enabled=enabled,
                        gradient=Brush.horizontalGradient(listOf(Color(0xFF289CE4),Color(0xFF8CB4C4),Color(0xFFF4A35F))))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Label("−60 °C",small=true,muted=true)
                        Label("+60 °C",small=true,muted=true)
                    }
                }
                RoundControl(JIcons.Add,"Увеличить температурный порог",Modifier.testTag("automation-threshold-plus"),enabled=enabled&&temperature<60f){onValue((temperature+1f).coerceIn(-60f,60f).toString())}
            }
            Label("Источник — датчик наружной температуры ГУ. Нет свежего значения — сценарий пропускается.",small=true,muted=true)
        }
    }
}
@Composable fun SeatActionEditor(action:SeatAction,enabled:Boolean,onSet:(SeatParameter,SeatLevel)->Unit) {
    Column(Modifier.testTag("automation-${action.component.name.lowercase()}-actions"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                AutomationComponentArt(action.component,Modifier.size(65.dp,100.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    action.values().forEach {(p,value)->SeatActionRow(p,value,enabled){onSet(p,it)}}
                }
            }
        }
        Label(if(action.component.seatParameters().size==1)"Заднее сиденье — только подогрев. 0 выключает его; «Не изменять» сохраняет текущий режим."
            else "0 выключает режим; «Не изменять» сохраняет текущий. Подогрев и вентиляция взаимоисключаются только у этого сиденья.",small=true,muted=true)
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
        SectionTitle("Панорамная шторка","Откроется по выбранному событию запуска. Остановка автомобиля не требуется.")
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
    if(type.isSeat)Image(painterResource(R.drawable.seat_jeland),null,modifier,contentScale=ContentScale.Fit)
    else if(type==ComponentType.STEERING_WHEEL)Glyph(JIcons.Steering,"Подогрев руля",modifier,active=true)
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
