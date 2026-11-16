package ru.jeland.os.data.cluster

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Models installed Carota ftp_server: one fixed PASV port; LIST output is buffered
 * until the finite shell command reaches EOF (fread(...,1,9999,...) at 0x8028).
 * Unlike the old generic streaming peer, it rejects a second owner of the passive port.
 */
class QnxClusterSharedPortTest {
    private class OemPeer(private val native:String,blocked:Boolean=false):AutoCloseable {
        val server=ServerSocket(0)
        private val passive=ServerSocket(0)
        private val pool=Executors.newCachedThreadPool()
        private val closed=AtomicBoolean(false)
        private val busy=AtomicBoolean(blocked)
        private val sockets=CopyOnWriteArrayList<Socket>()
        val commands=CopyOnWriteArrayList<String>()
        val passiveRejected=AtomicInteger()
        val firstList=CountDownLatch(1)
        val activeCommands=AtomicInteger()
        init {pool.execute {
            while(!closed.get())try {val s=server.accept();sockets+=s;pool.execute {serve(s)}}catch(_:Exception){break}
        }}
        private fun serve(socket:Socket) {
            var lease=false;var active:InetSocketAddress?=null
            try {socket.use {s->
                s.soTimeout=6000
                val input=s.getInputStream().bufferedReader();val out=s.getOutputStream().bufferedWriter()
                fun reply(value:String){out.write(value+"\r\n");out.flush()}
                reply("220 OEM fixed passive port fixture")
                while(true) {
                    val cmd=input.readLine()?:break;commands+=cmd
                    when(cmd.substringBefore(' ')) {
                        "USER"->reply("331 Password")
                        "PASS"->reply("230 OK")
                        "TYPE"->reply("200 OK")
                        "PASV"->{
                            if(!busy.compareAndSet(false,true)){passiveRejected.incrementAndGet();reply("426 pasv failure")}
                            else {lease=true;active=null;val port=passive.localPort
                                reply("227 Entering Passive Mode (127,0,0,1,${port/256},${port%256})")}
                        }
                        "PORT"->{
                            val numbers=cmd.substringAfter(' ').split(',').map(String::toInt)
                            require(numbers.size==6&&numbers.all {it in 0..255})
                            require(numbers.take(4)==listOf(127,0,0,1))
                            active=InetSocketAddress("127.0.0.1",numbers[4]*256+numbers[5])
                            activeCommands.incrementAndGet();reply("200 PORT command successful.")
                        }
                        "LIST"->{
                            require(!cmd.contains("?wait")){"Infinite commands cannot produce EOF in the OEM LIST handler"}
                            val data=active?.let {Socket().apply {connect(it,1500)}}?:passive.accept()
                            sockets+=data;reply("150 Data connection accepted; transfer starting.")
                            firstList.countDown()
                            // Model the OEM fread waiting for finite command completion before publishing bytes.
                            Thread.sleep(90)
                            val response=if(cmd.contains("screencmd"))"__QNX_FTP_OUTPUT_BEGIN__\n\n__QNX_FTP_OUTPUT_END__:0\n"
                                else "__JOS_NATIVE_BEGIN__\n"+native+"__JOS_NATIVE_END__\n"
                            data.use {it.getOutputStream().write(response.toByteArray())}
                            if(lease){busy.set(false);lease=false}
                            reply("226 Transfer complete")
                        }
                        else->reply("502 Unsupported")
                    }
                }
            }}catch(_:Exception){}finally {if(lease)busy.set(false)}
        }
        override fun close(){closed.set(true);server.close();passive.close();sockets.forEach {runCatching {it.close()}};pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS)}
    }
    private fun native()=javaClass.getResource("/cluster-pps/field-warm.txt")!!.readText()
    private fun assertPanel(text:String) {
        val native=ClusterPpsBatch.parse(text).telemetry(19_900)
        assertNull(native.error)
        val panel=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=native)),null,null,20_000)
        assertEquals(800.0,panel.rpm!!,.001);assertEquals(0.0,panel.speed!!,.001)
        assertEquals(11,panel.indicators!!.size)
    }
    @Test(timeout=15000) fun nativeReadAndWindowCommandsShareTheSinglePassivePortWithoutCollision() {
        OemPeer(native()).use {peer->
            val readings=QnxClusterWindow("127.0.0.1",peer.server.localPort)
            val window=QnxClusterWindow("127.0.0.1",peer.server.localPort)
            val pool=Executors.newFixedThreadPool(2)
            val reports=CopyOnWriteArrayList<String>()
            try {
                val read=pool.submit<String> {readings.readTelemetry()}
                assertTrue(peer.firstList.await(3,TimeUnit.SECONDS))
                val hide=pool.submit {window.setVisible(false,reports::add)}
                assertPanel(read.get(5,TimeUnit.SECONDS));hide.get(5,TimeUnit.SECONDS)
                repeat(3) {
                    val frame=pool.submit<String> {readings.readTelemetry()}
                    val toggle=pool.submit {window.setVisible(it%2==0,reports::add)}
                    assertPanel(frame.get(5,TimeUnit.SECONDS));toggle.get(5,TimeUnit.SECONDS)
                }
                assertEquals(0,peer.passiveRejected.get());assertEquals(0,peer.activeCommands.get())
                assertEquals(4,reports.count {it.startsWith("QNX_VISIBILITY_RESULT")&&it.contains("exit=0")&&it.contains("transfer=true")})
                assertTrue(peer.commands.any {it.contains("SCREEN_PROPERTY_VISIBLE 0")})
                assertFalse(peer.commands.any {it.contains("?wait")||it.contains("a00c")||it.startsWith("STOR ")})
            }finally {readings.close();window.close();pool.shutdownNow()}
        }
    }
}
