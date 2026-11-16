package ru.jeland.os.data.cluster

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercises the real Commons Net passive data connection against a local FTP peer. */
class QnxClusterReadTransportTest {
    private class Peer(private val body:String,private val holdOpen:Boolean=false):AutoCloseable {
        val release=java.util.concurrent.CountDownLatch(1)
        val server=ServerSocket(0)
        val commands=CopyOnWriteArrayList<String>()
        val worker=Executors.newSingleThreadExecutor()
        @Volatile var control:Socket?=null
        init {worker.submit {
            try {
                server.accept().use {socket->
                    control=socket;socket.soTimeout=4000
                    val input=socket.getInputStream().bufferedReader()
                    val out=socket.getOutputStream().bufferedWriter()
                    fun reply(line:String){out.write(line+"\r\n");out.flush()}
                    var passive:ServerSocket?=null
                    try {
                        reply("220 OEM fixture")
                        while(true) {
                            val line=input.readLine()?:break;commands+=line
                            when(line.substringBefore(' ')) {
                                "USER"->reply("331 Password")
                                "PASS"->reply("230 OK")
                                "TYPE"->reply("200 OK")
                                "PASV"->{passive?.close();passive=ServerSocket(0)
                                    val port=passive.localPort
                                    reply("227 Entering Passive Mode (127,0,0,1,${port/256},${port%256})")}
                                "LIST"->{reply("150 Data follows")
                                    passive!!.accept().use {data->
                                        val output=data.getOutputStream()
                                        for(chunk in body.toByteArray().toList().chunked(13)) {output.write(chunk.toByteArray());output.flush()}
                                        if(holdOpen)release.await(8,TimeUnit.SECONDS)
                                    }
                                    passive.close();passive=null;reply("226 Complete")}
                                else->reply("502 Not supported")
                            }
                        }
                    }finally {passive?.close()}
                }
            }catch(_:Exception) { /* Closed explicitly by test cleanup. */ }
        }}
        override fun close(){release.countDown();control?.close();server.close();worker.shutdownNow();worker.awaitTermination(2,TimeUnit.SECONDS)}
    }
    @Test(timeout=12000) fun fixedReadCommandReturnsFramedSnapshotsAndReusesTheConnection() {
        Peer("listing noise\r\n__JOS_NATIVE_BEGIN__\r\n__JOS_GAUGE__\r\ndata:json:[1,2]\r\n__JOS_WARNINGS__\r\ndata:json:[3,4]\r\n__JOS_NATIVE_END__\r\n").use {peer->
            val client=QnxClusterWindow("127.0.0.1",peer.server.localPort)
            try {
                repeat(2){assertEquals("__JOS_GAUGE__\ndata:json:[1,2]\n__JOS_WARNINGS__\ndata:json:[3,4]\n",client.readTelemetry())}
                assertEquals(1,peer.commands.count {it.startsWith("USER ")})
                val reads=peer.commands.filter {it.startsWith("LIST ")}
                assertEquals(2,reads.size)
                assertTrue(reads.all {it.contains("/bin/cat /tmp/pps/spi_service/a000")&&it.contains("/bin/cat /tmp/pps/spi_service/a00d")&&it.contains("/bin/cat /tmp/pps/spi_service/a005")&&it.contains("/bin/cat /tmp/pps/spi_service/a001")})
                assertFalse(peer.commands.any {it.startsWith("STOR ")||it.contains("screencmd")||it.contains("?wait")})
            }finally {client.close()}
        }
    }
    @Test(timeout=12000) fun incompleteFramingIsNotPublished() {
        Peer("__JOS_NATIVE_BEGIN__\n__JOS_GAUGE__\ndata:json:[1,2]\n").use {peer->
            val client=QnxClusterWindow("127.0.0.1",peer.server.localPort)
            try {try {client.readTelemetry();fail("Expected framing failure")}catch(_:java.io.IOException){}}
            finally {client.close()}
        }
    }

    @Test(timeout=12000) fun completeNativeJsonReadReachesPanelThroughTheRealFtpClient() {
        val body="__JOS_NATIVE_BEGIN__\n"+javaClass.getResource("/cluster-pps/field-warm.txt")!!.readText()+"__JOS_NATIVE_END__\n"
        Peer(body).use {peer->
            val client=QnxClusterWindow("127.0.0.1",peer.server.localPort)
            try {
                val native=ru.jeland.os.core.ClusterPpsBatch.parse(client.readTelemetry()).telemetry(19_900)
                assertNull(native.error)
                val state=ru.jeland.os.core.UiState(car=ru.jeland.os.core.VehicleLinkState(demo=false,connected=true,clusterTelemetry=native))
                val panel=ru.jeland.os.core.ClusterPanelState.from(state,null,null,20_000)
                assertEquals(800.0,panel.rpm!!,.001);assertEquals(0.0,panel.speed!!,.001)
                assertNull(panel.coolant);assertNull(panel.message)
                assertEquals(11,panel.indicators!!.size)
                assertTrue(peer.commands.single {it.startsWith("LIST ")}.contains("__JOS_GAUGE_EXIT__:$?"))
            }finally {client.close()}
        }
    }
    @Test(timeout=12000) fun nativeVisibilityRequiresBothCompleteTransferAndSuccessfulCommand() {
        for(exit in listOf(0,5))Peer("__QNX_FTP_OUTPUT_BEGIN__\n\n__QNX_FTP_OUTPUT_END__:$exit\n").use {peer->
            val client=QnxClusterWindow("127.0.0.1",peer.server.localPort)
            try {
                if(exit==0)client.setVisible(true,{})else {
                    try {client.setVisible(true,{});fail("An unconfirmed native window must not close the custom scene")}
                    catch(_:java.io.IOException){}
                }
                assertTrue(peer.commands.any {it.contains("SCREEN_PROPERTY_VISIBLE 1")})
            }finally {client.close()}
        }
    }

}
