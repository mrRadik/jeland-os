package ru.jeland.os.navigation

enum class NavigationJourneyEvent(val speech:String) {
    STARTED("Маршрут построен. Начинаем движение."),
    REBUILT("Маршрут перестроен."),
    ARRIVED("Вы прибыли. Маршрут завершён."),
    STOPPED("Ведение по маршруту остановлено.")
}
