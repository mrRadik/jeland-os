package ru.jeland.os.core

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A narrow qconn FILE client. Only versions, service=file, O_RDONLY open, read, close.
 * No launcher, debugger, shell, auth, writes to remote files, or caller-provided paths.
 * Protocol reference: https://github.com/zayfod/qcl (MIT); implemented independently here.
 */
class QnxTyreReadProtocol(private val input:InputStream,private val output:OutputStream,private val check:()->Unit,
    private val onTrace:(TyreProtocolTrace)->Unit={}):Closeable {
    private var ready=false
    private var phase=TyreReadPhase.IDLE
    private var lastCommand=""
    private var received=0L
    private var sent=0L
    private val tail=java.util.ArrayDeque<Int>()
    fun diagnosticSnapshot()=TyreProtocolTrace(phase,lastCommand,received,sent,
        tail.joinToString("") {when(it){10->"\\n";13->"\\r";9->"\\t";in 32..126->it.toChar().toString();else->"."}},
        tail.toList().takeLast(48).joinToString(" "){it.toString(16).padStart(2,'0')})
    private fun changed(){runCatching {onTrace(diagnosticSnapshot())}}
    private fun stage(value:TyreReadPhase){phase=value;tail.clear();changed()}
    private fun rawByte():Int {
        val value=input.read()
        if(value>=0){received++;if(tail.size==128)tail.removeFirst();tail.addLast(value)}
        return value
    }
    private fun write(bytes:ByteArray){output.write(bytes);sent+=bytes.size;output.flush()}
    fun begin() {
        check()
        stage(TyreReadPhase.GREETING)
        // A harmless empty broker line also wakes implementations that delay their first prompt.
        send("")
        val greeting=prompt(BROKER,4096)
        requireProtocol(Regex("\\bQCONN\\b").containsMatchIn(greeting),"Not a QCONN greeting: ${brief(greeting)}")
        for(service in listOf("broker","file")) {
            stage(if(service=="broker")TyreReadPhase.BROKER_VERSION else TyreReadPhase.FILE_VERSION)
            val response=command("versions $service",BROKER)
            val version=response.lineSequence().map {it.trim()}.firstOrNull {it.matches(Regex("[0-9]+"))}?.toIntOrNull()
            requireProtocol(version!=null&&version>=256,"Unsupported qconn $service version: ${brief(response)}")
        }
        stage(TyreReadPhase.FILE_SERVICE)
        requireProtocol(lines(command("service file",FILE)).any {it=="OK"},"File service rejected")
        ready=true
    }
    fun readTpms():String {
        check();check(ready){"File service not identified"}
        val path=QnxTyreTemperatureDecoder.PPS_PATH
        stage(TyreReadPhase.OPEN)
        val opened=lines(command("o:\"$path\":0",FILE)).firstOrNull {it.startsWith("o:")}?:throw IOException("No open reply")
        val match=Regex("o:([0-9a-fA-F]+):([0-9a-fA-F]+):([0-9a-fA-F]+):\"([^\"]+)\"").matchEntire(opened)?:throw IOException("PPS open rejected: ${brief(opened)}")
        val fd=match.groupValues[1].toLongOrNull(16)?:throw IOException("Invalid fd")
        val mode=match.groupValues[2].toLongOrNull(16)?:throw IOException("Invalid mode")
        val size=match.groupValues[3].toLongOrNull(16)?:throw IOException("Invalid length")
        requireProtocol(fd in 0..65535&&mode and 0xf000==0x8000L&&size in 0..2048&&match.groupValues[4]==path,"Wrong PPS open metadata")
        // No ?wait / ?delta flags. Default PPS full-mode read is a nonblocking snapshot.
        stage(TyreReadPhase.READ_HEADER)
        send("r:${fd.toString(16)}:0:800")
        var header=line(1024)
        if(header=="r:${fd.toString(16)}:0:800")header=line(1024) // optional terminal echo
        val read=Regex("o:([0-9a-fA-F]+):([0-9a-fA-F]+):([0-9a-fA-F]+)").matchEntire(header)?:throw IOException("PPS read rejected: ${brief(header)}")
        val count=read.groupValues[1].toLongOrNull(16)?:throw IOException("Invalid read length")
        requireProtocol(count in 0..2048,"PPS response exceeds bound")
        val bytes=ByteArray(count.toInt())
        stage(TyreReadPhase.READ_PAYLOAD)
        for(i in bytes.indices)bytes[i]=next().toByte()
        stage(TyreReadPhase.READ_TRAILER)
        requireProtocol(prompt(FILE,256).isBlank(),"Unexpected bytes after PPS data")
        stage(TyreReadPhase.CLOSE)
        requireProtocol(lines(command("c:${fd.toString(16)}",FILE)).any {it=="o"},"PPS close rejected")
        return bytes.toString(Charsets.UTF_8)
    }
    private fun lines(text:String)=text.lineSequence().map {it.trim()}.filter {it.isNotEmpty()}.toList()
    private fun brief(text:String)=text.take(160).replace(Regex("[^ -~]")," ")
    private fun command(value:String,marker:String):String {send(value);return prompt(marker,4096)}
    private fun send(value:String) {check();lastCommand=if(value.isEmpty())"<CRLF>"else value;write((value+"\r\n").toByteArray(Charsets.US_ASCII));changed()}
    private fun next():Int {
        while(true) {
            check();val b=rawByte();if(b<0)throw EOFException("Qconn closed")
            if(b!=255)return b
            check();val action=rawByte();if(action<0)throw EOFException("Incomplete Telnet control")
            if(action==255)return 255
            if(action in 251..254) {
                val option=rawByte();if(option<0)throw EOFException("Incomplete Telnet option")
                // Decline all terminal options. No interpreter/launcher requests are permitted.
                if(action==251||action==253){check();write(byteArrayOf(255.toByte(),(if(action==251)254 else 252).toByte(),option.toByte()))}
            } else if(action==250) {
                var previous=-1;var ended=false
                repeat(256){check();if(!ended){val c=rawByte();if(c<0)throw EOFException("Incomplete Telnet subnegotiation");if(previous==255&&c==240)ended=true;previous=c}}
                requireProtocol(ended,"Telnet subnegotiation exceeds bound")
            } else requireProtocol(action in listOf(240,241,249),"Unsupported Telnet control")
        }
    }
    private fun line(limit:Int):String {
        val b=StringBuilder()
        repeat(limit){val c=next();if(c==10)return b.toString().trim('\r');requireProtocol(c in 9..13||c in 32..126,"Invalid response text");b.append(c.toChar())}
        throw IOException("Response line exceeds bound")
    }
    private fun prompt(marker:String,limit:Int):String {
        val b=StringBuilder()
        repeat(limit) {
            val c=next();requireProtocol(c in 9..13||c in 32..126,"Invalid prompt text");b.append(c.toChar())
            // Consume the one trailing space included in the QNX prompt, not a subsequent payload byte.
            if(b.endsWith("$marker "))return b.dropLast(marker.length+1).toString().trim('\r','\n')
        }
        throw IOException("Qconn prompt exceeds bound")
    }
    override fun close(){ready=false} // Socket owner closes transport; disconnect also releases server descriptors.
    private fun requireProtocol(value:Boolean,message:String){if(!value)throw IOException(message)}
    companion object {private const val BROKER="<qconn-broker>";private const val FILE="<qconn-file>"}
}
