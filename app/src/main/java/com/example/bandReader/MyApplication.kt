package com.example.bandReader

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleCoroutineScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val EXAMPLE_COUNTER = intPreferencesKey("example_counter")
val KEY_ERR = stringPreferencesKey("key_err")
val APP_CONFIG = stringPreferencesKey("app_config")
val HAS_LISTENER = booleanPreferencesKey("has_listener")

@HiltAndroidApp
class MyApplication :Application(), Thread.UncaughtExceptionHandler {
    private val applicationScope = CoroutineScope(Dispatchers.Main.immediate)
    lateinit var  clipboardManager: ClipboardManager
    override fun onCreate() {
        super.onCreate()
        Log.e("TAG", "application onCreate: ", )

        /*val strictMode = StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
        StrictMode.setThreadPolicy(strictMode)*/

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // collect the flow in a CoroutineScope
        applicationScope.launch {
            this@MyApplication.dataStore.data
                .map { preferences ->
                    // No type safety.
                    preferences[KEY_ERR] ?: ""
                }.collect { value ->
                // Update the UI.
                if (value != "") {
                    logFlow.value = value
                    withContext(Dispatchers.Main){
                        Toast.makeText(this@MyApplication, "上次异常退出已复制剪切板", Toast.LENGTH_SHORT).show()
                        Log.e("TAG",value)
                    }
                    // 创建一个包含文本的 ClipData
                    val clipData = ClipData.newPlainText("err", value)
                    // 将 ClipData 写入剪贴板
                    clipboardManager.setPrimaryClip(clipData)
                    this@MyApplication.dataStore.edit { settings ->
                        settings.remove(KEY_ERR)
                    }

                }
            }
        }

        // 设置全局异常捕捉
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun clearErrLog() {
        runBlocking {
            this@MyApplication.dataStore.edit { settings ->
                settings[KEY_ERR] = ""
            }
        }
    }
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 处理未捕获的异常
        Log.e("MyApplication", "Uncaught exception: $throwable")
        runBlocking {
            this@MyApplication.dataStore.edit { settings ->
                settings[KEY_ERR] = throwable.stackTraceToString()
            }
            withContext(Dispatchers.Main){
                Toast.makeText(this@MyApplication,  throwable.toString(), Toast.LENGTH_LONG).show()
            }
        }
        // 退出应用程序
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }
}