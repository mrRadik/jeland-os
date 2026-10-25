package ru.jeland.os.core

enum class TyreReadPhase(val title:String) {
    IDLE("Ожидание подключения ГУ"), INTERFACE("Проверка внутренней сети"), CONNECT("Подключение TCP"),
    GREETING("Ожидание приветствия QCONN"), BROKER_VERSION("Версия broker"), FILE_VERSION("Версия file"),
    FILE_SERVICE("Открытие сервиса file"), OPEN("Открытие a005 только для чтения"),
    READ_HEADER("Ожидание заголовка данных"), READ_PAYLOAD("Чтение данных a005"),
    READ_TRAILER("Завершение чтения"), CLOSE("Закрытие файла"), PARSE("Разбор температуры"),
    POLLING("Ожидание следующего чтения"), STOPPED("Чтение остановлено")
}
data class TyreProtocolTrace(
    val phase:TyreReadPhase=TyreReadPhase.IDLE,val command:String="",val receivedBytes:Long=0,val sentBytes:Long=0,
    val textTail:String="",val hexTail:String=""
)
data class TyreDebugSample(val raw:Int,val status:Int,val celsius:Int?,val atMillis:Long)
/** Own read-only transport diagnostics; monotonic timestamps, bounded data only, no synthetic sensor values. */
data class TyreTemperatureDiagnostics(
    val active:Boolean=false,val phase:TyreReadPhase=TyreReadPhase.IDLE,val attempts:Int=0,
    val interfaceName:String="",val tcpConnected:Boolean=false,val trace:TyreProtocolTrace=TyreProtocolTrace(),
    val lastError:String="",val failedPhase:TyreReadPhase?=null,val errorAtMillis:Long?=null,val retryAtMillis:Long?=null,
    val payload:String="",val packetCount:Long=0,val lastPacketAtMillis:Long?=null,
    val samples:Map<Wheel,TyreDebugSample> = emptyMap()
)
