package ru.jeland.os.core

enum class ClusterNavigationMode(val title:String) {
    GUIDANCE("Подсказки по маршруту"),
    MAP("Карта");

    companion object {
        fun fromStored(value:String)=entries.firstOrNull {it.name==value}?:MAP
    }
}

/** Only active guidance produces turn cues; a visible map is independent of this state. */
val ClusterPanelState.activeNavigation:NavigationState?
    get()=navigation?.takeIf {it.active&&!it.preview&&!it.calculating&&!it.arrived}
