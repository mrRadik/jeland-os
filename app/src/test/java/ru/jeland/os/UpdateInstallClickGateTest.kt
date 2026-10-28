package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.UpdateInstallClickGate

class UpdateInstallClickGateTest {
    private fun click(g:UpdateInstallClickGate,pkg:String="com.android.packageinstaller",title:Boolean=true,
        id:String="android:id/button1",text:String="Установить",enabled:Boolean=true,visible:Boolean=true,
        clickable:Boolean=true,at:Long=1000)=g.claim(pkg,title,id,text,enabled,visible,clickable,at)
    @Test fun confirmationRequiresExplicitArmAndIsConsumedOnce() {
        val g=UpdateInstallClickGate();assertFalse(click(g))
        g.arm("Jeland OS",1000);assertTrue(click(g));assertFalse(click(g))
    }
    @Test fun otherAppsPermissionsAndCancellationAreNeverConfirmed() {
        val g=UpdateInstallClickGate();g.arm("Jeland OS",1000)
        assertFalse(click(g,pkg="com.android.settings"))
        assertFalse(click(g,title=false))
        assertFalse(click(g,id="android:id/button2",text="Отмена"))
        assertFalse(click(g,text="Разрешить"));assertFalse(click(g,text="Настройки"))
        assertTrue(click(g))
    }
    @Test fun disabledOrHiddenButtonIsNotForcedAndCanBeRetriedWhenReady() {
        val g=UpdateInstallClickGate();g.arm("Jeland OS",1000)
        assertFalse(click(g,enabled=false));assertFalse(click(g,visible=false));assertFalse(click(g,clickable=false))
        assertTrue(click(g,pkg="com.google.android.packageinstaller",text="Update"))
    }
    @Test fun cancellationAndExpiryPreventLaterBackgroundInstallations() {
        val g=UpdateInstallClickGate();g.arm("Jeland OS",1000);g.clear();assertFalse(click(g))
        g.arm("Jeland OS",1000);assertFalse(click(g,at=181_000));assertNull(g.pendingLabel(181_001))
        g.arm("Jeland OS",200_000);assertTrue(click(g,text="Обновить",at=200_001))
    }
}
