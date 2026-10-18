package ru.jeland.os

import android.app.Application
import ru.jeland.os.data.AppContainer

class JelandApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
