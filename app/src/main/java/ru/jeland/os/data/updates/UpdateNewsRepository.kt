package ru.jeland.os.data.updates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.*

class UpdateNewsRepository(private val store:SettingsStore,private val installed:Long,private val report:(String)->Unit={}) {
    private val tracker=UpdateNewsTracker(store)
    private val mutable=MutableStateFlow(tracker.pending(installed))
    val notice=mutable.asStateFlow()
    // Keep the currently visible dialog during Activity recreation, but never reopen it after process restart.
    fun shown(version:Long){runCatching {tracker.shown(version)}.onFailure {report("UPDATE_NEWS_SAVE_ERROR $it")}}
    fun dismiss(){mutable.value?.let {shown(it.versionCode)};mutable.value=null}
}
