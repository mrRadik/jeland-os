package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Color
import android.os.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.media.bean.*
import com.desaysv.localmediasdk.bean.*
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.MockMediaRepository
import ru.jeland.os.data.media.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.ByteArrayOutputStream
import java.io.File

class AutoMusicInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun isolate(){
        ui.runOnIdle {(ui.activity.application as JelandApplication).container.car.useVehicle(false)}
        ui.waitUntil(10000){ui.onRoot().fetchSemanticsNode().boundsInRoot.let {it.height>it.width}}
    }
    @Test fun usbListAndCommandsUseExactStockSdkLayout() {
        val entry=RemoteBean().apply {source="usb0";title="Actual USB track";artist="Artist";path="/storage/usb/test.mp3";duration=240000}
        val commands=mutableListOf<String>()
        val binder=object:Binder() {
            init {attachInterface(null,OemMediaProtocol.USB_DESCRIPTOR)}
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                data.enforceInterface(OemMediaProtocol.USB_DESCRIPTOR);assertEquals("usb0",data.readString())
                when(code){
                    6->{assertEquals("list_all",data.readString());assertEquals(0,flags);reply!!.writeNoException();reply.writeInt(1)
                        RemoteBeanList().apply {source="usb0";type="list_all";remoteBeanList=listOf(entry)}.writeToParcel(reply,0)}
                    3->{reply!!.writeNoException();reply.writeByteArray(null)}
                    1->{assertEquals(IBinder.FLAG_ONEWAY,flags);val action=data.readString()!!;commands+=action
                        val json=data.readString()!!;if(action=="set_media"){assertTrue(json.contains("mPath"));assertTrue(json.contains("test.mp3"))}else assertEquals("",json)}
                    else->fail("Unexpected transaction $code")
                }
                assertEquals(0,data.dataAvail());return true
            }
        }
        val queue=OemMediaProtocol.playlist(binder,PhoneMusicSource.USB0)
        assertEquals(1,queue.size);assertEquals(240,queue.single().track.duration)
        for(action in listOf(1,2,4,5))OemMediaProtocol.usbCommand(binder,PhoneMusicSource.USB0,action)
        OemMediaProtocol.usbCommand(binder,PhoneMusicSource.USB0,1,queue.single())
        assertEquals(listOf("play","pause","pre","next","set_media"),commands)
        try {OemMediaProtocol.usbCommand(binder,PhoneMusicSource.USB0,6);fail()}catch(expected:IllegalStateException){}
        assertNull(OemMediaProtocol.cover(binder,PhoneMusicSource.USB0))
    }
    @Test fun unifiedMediaWireAndArtworkAreBoundedAndLocalOnly() {
        val info=VDMediaInfo().apply {putId(7);putMediaType(3);putTitle("Title");putArtist("Artist");putFileUrl("/storage/usb/test.mp3")}
        val event=VDEvent(OemMediaProtocol.INFO,Bundle().apply {putParcelable("info",VDMediaItem(3).apply {mediaInfo=info})})
        val parcel=Parcel.obtain()
        try {
            event.writeToParcel(parcel,0);parcel.setDataPosition(0)
            val decoded=OemMediaProtocol.value<VDMediaItem>(VDEvent.CREATOR.createFromParcel(parcel),OemMediaProtocol.INFO,"info")!!
            assertEquals(3,decoded.mediaType);assertEquals("Title",decoded.mediaInfo.title);assertEquals("/storage/usb/test.mp3",decoded.mediaInfo.fileUrl)
        }finally {parcel.recycle()}
        val bmp=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        val bytes=ByteArrayOutputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bmp.recycle()
        val art=MediaArtworkLoader.encoded(bytes)!!
        val small=android.graphics.BitmapFactory.decodeByteArray(art.bytes,0,art.bytes.size)
        assertTrue(small.width<=512);assertTrue(small.height<=512);small.recycle()
        assertNull(MediaArtworkLoader.encoded(ByteArray(MediaArtworkLoader.MAX_BYTES+1)))
        assertNull(MediaArtworkLoader.encoded(byteArrayOf(1,2,3)))
        for(uri in listOf("https://example.com/art.jpg","file:///sdcard/pic.jpg","content://untrusted/art"))assertNull(MediaArtworkLoader.local(ui.activity,uri))
    }
    @Test fun fullPlayerDisplaysArtworkUsbQueueAndNoInstructionsInBothThemes() {
        val bmp=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(12,80,130))}
        val art=MediaArtworkLoader.bitmap(bmp);bmp.recycle()
        val queue=listOf(MediaQueueItem("one",Track("Night drive","Jeland collection",240)),MediaQueueItem("two",Track("Mountain road","Jeland collection",200)))
        var sample by mutableStateOf(MediaState(live=true,source="USB",liveTrack=queue[0].track,playing=true,position=84,artwork=art,playlist=queue,currentId="one"))
        var dark by mutableStateOf(true)
        val selected=mutableListOf<Int>();val delegate=MockMediaRepository(MemorySettingsStore())
        val repo=object:MediaRepository by delegate {override fun selectTrack(index:Int){selected+=index}}
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark){FullMusicPlayer(sample,repo)}}}
        for(theme in listOf(true,false)) {
            ui.runOnIdle {dark=theme;sample=sample.copy(playing=true)}
            ui.waitUntil(5000){ui.onAllNodesWithContentDescription("Обложка альбома").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("music-active-source").assertTextEquals("USB")
            ui.onNodeWithTag("music-waves-playing").assertExists()
            ui.onNodeWithTag("media-status").assertDoesNotExist()
            ui.onNodeWithTag("music-open-playlist").performClick()
            ui.onNodeWithTag("music-playlist-panel").assertIsDisplayed()
            ui.onNodeWithTag("media-track-1").performClick()
            ui.runOnIdle {assertEquals(if(theme)listOf(1)else listOf(1,1),selected)}
            ui.onNodeWithTag("music-close-playlist").performClick()
            ui.onNodeWithTag("media-title").performScrollTo()
            val file=File(ui.activity.getExternalFilesDir(null),"qa-0.7.0/music-${if(theme)"dark"else"light"}.png").apply {parentFile!!.mkdirs()}
            file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            ui.runOnIdle {sample=sample.copy(playing=false)}
            ui.onNodeWithTag("music-waves-paused").assertExists()
        }
        assertEquals(listOf(1,1),selected)
    }
}
