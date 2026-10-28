package ru.jeland.os.core

/** One confirmation for a user-started, verified update. Never a general-purpose auto-clicker. */
class UpdateInstallClickGate {
    private var label:String?=null
    private var expiresAt=0L
    fun arm(appLabel:String,now:Long) {require(appLabel.isNotBlank());label=appLabel;expiresAt=now+180_000}
    fun clear(){label=null;expiresAt=0}
    fun pendingLabel(now:Long):String? {if(now>=expiresAt)clear();return label}
    fun claim(packageName:String,hasExpectedTitle:Boolean,viewId:String,buttonText:String,
        enabled:Boolean,visible:Boolean,clickable:Boolean,now:Long):Boolean {
        if(pendingLabel(now)==null||packageName !in INSTALLERS||!hasExpectedTitle||
            viewId!="android:id/button1"||buttonText.trim().lowercase() !in INSTALL_LABELS||
            !enabled||!visible||!clickable)return false
        clear();return true
    }
    companion object {
        val INSTALLERS=setOf("com.android.packageinstaller","com.google.android.packageinstaller")
        private val INSTALL_LABELS=setOf("установить","обновить","install","update","安装","更新")
    }
}
