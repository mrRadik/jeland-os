package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import java.io.*

class QnxTyreReadProtocolTest {
    private val broker="<qconn-broker> "
    private val file="<qconn-file> "
    private val path=QnxTyreTemperatureDecoder.PPS_PATH
    private val handshake="QCONN\r\n$broker"+"256\r\n$broker"+"256\r\n$broker"+"OK\r\n$file"
    private val body="@a005\ndata:json:[0,0,0,80,180,180,180,180]\n"
    private fun reply(data:String=body,opened:String="o:3:81b6:0:\"$path\"",count:Int=data.toByteArray().size)=
        "$opened\r\n$file"+"o:${count.toString(16)}:0:0\r\n$data$file"+"o\r\n$file"
    private class Fixture(text:String,val guard:()->Unit={}) {
        val out=ByteArrayOutputStream()
        val p=QnxTyreReadProtocol(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),out,guard)
        fun sent()=out.toString("US-ASCII")
    }
    private fun rejects(block:()->Unit){try{block();fail("Accepted malformed/unsupported protocol")}catch(expected:IOException){}}
    @Test fun onlyIdentifiedBrokerFileServiceAndOneReadOnlyPath() {
        val f=Fixture(handshake+reply()+reply());f.p.begin()
        assertEquals(body,f.p.readTpms());assertEquals(body,f.p.readTpms())
        val lines=f.sent().split("\r\n").filter {it.isNotBlank()}
        assertEquals(listOf("versions broker","versions file","service file","o:\"$path\":0","r:3:0:800","c:3","o:\"$path\":0","r:3:0:800","c:3"),lines)
        assertFalse(f.sent().contains("launcher"));assertFalse(f.sent().contains("/tx"));assertFalse(f.sent().contains("start"))
    }
    @Test fun unsupportedPeerFailsBeforeFileRequests() {
        for(text in listOf("SOME OTHER SERVICE\r\n$broker","QCONN\r\n$broker"+"255\r\n$broker",
            "QCONN\r\n$broker"+"256\r\n$broker"+"0\r\n$broker",handshake.replace("OK\r\n$file","error denied\r\n$file"))) {
            val f=Fixture(text);rejects {f.p.begin()};assertFalse(f.sent().contains("o:\""))
        }
    }
    @Test fun wrongPathDescriptorsDeviceAndOversizeFailBeforeRead() {
        for(open in listOf("o:3:81b6:0:\"/etc/passwd\"","o:10000:81b6:0:\"$path\"","o:3:61b6:0:\"$path\"","o:3:81b6:10000000:\"$path\"","o:-1:0:0:\"$path\"")) {
            val f=Fixture(handshake+reply(opened=open));f.p.begin();rejects {f.p.readTpms()};assertFalse(f.sent().contains("r:3:"))
        }
    }
    @Test fun malformedReadLengthEofAndUnexpectedTrailerFailClosed() {
        val over=Fixture(handshake+reply(count=2049));over.p.begin();rejects {over.p.readTpms()}
        val short=Fixture(handshake+reply().substringBefore(body)+body.take(4));short.p.begin();rejects {short.p.readTpms()}
        val bad=Fixture(handshake+reply().replace(body+file,body+"UNEXPECTED"+file));bad.p.begin();rejects {bad.p.readTpms()}
        val huge=Fixture("QCONN\n"+"a".repeat(4096));rejects {huge.p.begin()}
    }
    @Test fun cancellationStopsBeforeAnyWriteAndMidReply() {
        val f=Fixture(handshake){throw IOException("cancel")};rejects {f.p.begin()};assertEquals(0,f.out.size())
        var count=0;val g=Fixture(handshake){if(++count>8)throw IOException("cancel")};rejects {g.p.begin()}
        assertFalse(g.sent().contains("service file"))
    }
    @Test fun telnetTerminalOptionsDeclinedWithoutCommandExecution() {
        // Construct raw transport bytes rather than UTF-8-encoding IAC.
        val out=ByteArrayOutputStream();val input=ByteArrayInputStream(byteArrayOf(255.toByte(),251.toByte(),1)+handshake.toByteArray())
        val p=QnxTyreReadProtocol(input,out,check={});p.begin()
        assertTrue(out.toByteArray().toList().windowed(3).any {it==listOf(255.toByte(),254.toByte(),1.toByte())})
    }
    @Test fun timeoutKeepsPartialGreetingStageAndExactByteCounts() {
        val partial="QCONN\r\n<qconn-".toByteArray();var index=0
        val input=object:InputStream(){override fun read():Int=if(index<partial.size)partial[index++].toInt()else throw java.net.SocketTimeoutException("Read timed out")}
        val out=ByteArrayOutputStream();val p=QnxTyreReadProtocol(input,out,check={})
        rejects {p.begin()}
        val debug=p.diagnosticSnapshot()
        assertEquals(TyreReadPhase.GREETING,debug.phase)
        assertEquals(partial.size.toLong(),debug.receivedBytes);assertEquals(2L,debug.sentBytes)
        assertEquals("<CRLF>",debug.command);assertEquals("QCONN\\r\\n<qconn-",debug.textTail)
        assertEquals("\r\n",out.toString("US-ASCII")) // diagnostics do not cause extra requests
    }
    @Test fun diagnosticsRemainBoundedOnUnrecognizedOversizedReply() {
        val f=Fixture("QCONN\n"+"a".repeat(4096));rejects {f.p.begin()}
        val debug=f.p.diagnosticSnapshot()
        assertEquals(TyreReadPhase.GREETING,debug.phase)
        assertTrue(debug.textTail.length<=256);assertTrue(debug.hexTail.length<=48*3)
        assertTrue(debug.receivedBytes>=4096)
    }
    @Test fun tracesCoverReadStagesWithoutChangingReadOnlyCommands() {
        val stages=mutableListOf<TyreReadPhase>();val out=ByteArrayOutputStream()
        val p=QnxTyreReadProtocol(ByteArrayInputStream((handshake+reply()).toByteArray()),out,{},onTrace={stages+=it.phase})
        p.begin();assertEquals(body,p.readTpms())
        assertTrue(stages.containsAll(listOf(TyreReadPhase.GREETING,TyreReadPhase.BROKER_VERSION,TyreReadPhase.FILE_VERSION,
            TyreReadPhase.FILE_SERVICE,TyreReadPhase.OPEN,TyreReadPhase.READ_HEADER,TyreReadPhase.READ_PAYLOAD,TyreReadPhase.READ_TRAILER,TyreReadPhase.CLOSE)))
        assertEquals(out.size().toLong(),p.diagnosticSnapshot().sentBytes)
        assertEquals((handshake+reply()).toByteArray().size.toLong(),p.diagnosticSnapshot().receivedBytes)
    }
}
