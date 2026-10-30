package ru.jeland.os.navigation.comaps

/** Presentation only: the native router supplies both the turn and the exit count. */
internal fun coMapsTurnInstruction(direction:String,exit:Int):String = when {
    direction=="REACHED_YOUR_DESTINATION"->"Место назначения"
    direction=="LEAVE_ROUND_ABOUT"->if(exit>0)"Съезжайте с кругового движения, съезд № $exit"else"Съезжайте с кругового движения"
    direction=="ENTER_ROUND_ABOUT"&&exit<=0->"Въезжайте на круговое движение"
    direction.contains("ROUND_ABOUT")->if(exit>0)"На круговом движении выберите съезд № $exit"else"Продолжайте движение по кругу"
    direction.contains("U_TURN")->"Развернитесь"
    direction.contains("LEFT")->"Поверните налево"
    direction.contains("RIGHT")->"Поверните направо"
    else->"Продолжайте движение прямо"
}
