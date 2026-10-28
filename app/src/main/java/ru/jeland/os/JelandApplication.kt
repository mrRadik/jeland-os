package ru.jeland.os

import android.app.Application
import ru.jeland.os.data.AppContainer

class JelandApplication : Application() {
    override fun onCreate(){super.onCreate();ru.jeland.os.data.updates.InstalledUpdateCleanup.run(this)}
    val container: AppContainer by lazy { AppContainer(this) }
}
