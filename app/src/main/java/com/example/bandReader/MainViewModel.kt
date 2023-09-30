package com.example.bandReader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bandReader.data.AppDatabase
import com.example.bandReader.data.BandMessage
import com.example.bandReader.data.Book
import com.example.bandReader.data.Chapter
import com.example.bandReader.data.SyncStatus
import com.example.bandReader.data.toChunk
import com.xiaomi.xms.wearable.Status
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.notify.NotifyApi
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import com.xiaomi.xms.wearable.service.ServiceApi
import com.xiaomi.xms.wearable.tasks.OnFailureListener
import com.xiaomi.xms.wearable.tasks.OnSuccessListener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext val appContext: Context) :
    ViewModel() {
    // mutable stat-flow books
    val appDatabase = AppDatabase.getInstance(appContext)
    var hasGrantedFlow = MutableStateFlow(false)
    private var hasListener = false
    var cancelSync = false
    var books = appDatabase.bookDao().getAllFlow()
    var chapters: Flow<List<Chapter>> = flow { }
    private var nodeApi: NodeApi? = null
    var curNode: Node? = null
    var messageApi: MessageApi? = null
    private var authApi: AuthApi? = null
    var notifyApi: NotifyApi? = null
    private var serviceApi: ServiceApi
    val bandConnected = MutableStateFlow(false)
    val bandAppInstalledFlow = MutableStateFlow(false)
    val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.SyncDef)
    val isSyncing = MutableStateFlow(false)
    val isImporting = MutableStateFlow(false)
    val chapterLoading = MutableStateFlow(false)
    private val granted = MutableStateFlow("")
    val grantedErrFlow = MutableStateFlow(false)
    val syncFlow = MutableStateFlow(Pair(0, 0))
    val receiveFlow = MutableStateFlow("receiveFlow")
    val restartFlow = MutableStateFlow(false)
    private val bandBooksFlow = MutableStateFlow<List<Book>>(emptyList())
    val currentBookFlow = MutableStateFlow<Book?>(null)
    var notInstall = false
    val shareUri = MutableStateFlow(Uri.EMPTY)
    var bufferedReaderFlow = MutableStateFlow<BufferedReader?>(null)

    fun getChapters(bookId: Int) = viewModelScope.launch {
        chapterLoading.value = true
        chapters = appDatabase.chapterDao().getChaptersByBookId(bookId)
        syncFlow.value = Pair(
            appDatabase.chapterDao().countSynced(bookId),
            appDatabase.chapterDao().countChapterBy(bookId)
        )
        receiveFlow.value =
            "syncFlow.value.first == syncFlow.value.second ${syncFlow.value.first} ${syncFlow.value.second} ${syncFlow.value.first == syncFlow.value.second}"
        if (syncFlow.value.first == syncFlow.value.second) {
            syncStatus.value = SyncStatus.SyncRe
        }
    }



    init {
        nodeApi = Wearable.getNodeApi(appContext)
        messageApi = Wearable.getMessageApi(appContext)
        authApi = Wearable.getAuthApi(appContext)
        serviceApi = Wearable.getServiceApi(appContext)
        notifyApi = Wearable.getNotifyApi(appContext)
        serviceApi.registerServiceConnectionListener(object : OnServiceConnectionListener {
            override fun onServiceConnected() {
                viewModelScope.launch(Dispatchers.IO) {
                    receiveFlow.value = "手环判断开始"
                    while (true) {
                        getConnectedDevice()
                        delay(3000)
                    }
                }
            }

            override fun onServiceDisconnected() {
                receiveFlow.value = "onServiceDisconnected"
                bandConnected.value = false
                syncStatus.value = SyncStatus.SyncNoConn
            }
        })

        viewModelScope.launch(Dispatchers.IO) {
            receiveFlow.collectLatest {
                Log.i("TAG", "======== receiveFlow:$it")
            }
        }
    }

    suspend fun getConnectedDevice(): Boolean {
        val connectedFuture = CompletableFuture<Boolean>()
        nodeApi?.connectedNodes?.addOnSuccessListener {
            if (it.size > 0) {
                curNode = it[0]
//                registerMessageListener()
                bandConnected.value = true
                connectedFuture.complete(true)
            } else {
                bandConnected.value = false
                connectedFuture.complete(false)
            }
        }?.addOnFailureListener {
            bandConnected.value = false
            connectedFuture.complete(false)
        }
        val connected = connectedFuture.await()
        if (connected) {
//            receiveFlow.value = "手环已连接"
        } else {
            receiveFlow.value = "手环未连接"
        }
        val isReady = connected && isBandAppInstalled()
        if (isReady) {
//            receiveFlow.value = "手环应用已就绪"
            registerMessageListener()
        } else {
            receiveFlow.value = "手环应用未就绪"
            hasGrantedFlow.value = false
            hasListener = false
        }
        return isReady
    }

    private suspend fun launchBandApp(): Boolean {
        val future = CompletableFuture<Boolean>()
        curNode?.let { node ->
            nodeApi?.launchWearApp(node.id, "/home")?.addOnSuccessListener {
                receiveFlow.value = "启动手环APP成功"
                future.complete(true)
            }?.addOnFailureListener {
                viewModelScope.launch(Dispatchers.Main) {
                    future.complete(false)
                    Toast.makeText(appContext, "启动手环APP失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return future.await()
    }


    suspend fun isBandAppInstalled(): Boolean {
        val future = CompletableFuture<Boolean>()
        curNode?.let { node ->
            nodeApi?.isWearAppInstalled(node.id)?.addOnSuccessListener {
                bandAppInstalledFlow.value = it
                if (it) {
                    future.complete(true)
                } else {
                    future.complete(false)
                    syncStatus.value = SyncStatus.SyncNoApp
                }
            }?.addOnFailureListener {
                receiveFlow.value = "isBandAppInstalled err ${it.message}"
                bandAppInstalledFlow.value = false
                syncStatus.value = SyncStatus.SyncNoApp
                future.complete(false)
            }
        }
        val installed = future.await()
        if (installed) {
//            receiveFlow.value = "手环应用已安装"
            return checkPermissionGranted()
        } else {
            receiveFlow.value = "手环应用未安装"
            return installed
        }
    }

    private suspend fun checkPermissionGranted(): Boolean {
        val future = CompletableFuture<Boolean>()
        if (hasGrantedFlow.value) {
            return true
        }
        curNode?.let { node ->
            val permissions = arrayOf<Permission>(Permission.DEVICE_MANAGER, Permission.NOTIFY)
            authApi?.checkPermissions(node.id, permissions)?.addOnSuccessListener { it ->
                receiveFlow.value = "进入checkPermissions"
                val isPermissionGranted = mutableListOf<String>()
                for ((index, permission) in permissions.withIndex()) {
                    isPermissionGranted.add("${permission.name} grant status is ${it[index]}")
                }
                Log.i("TAB", "check permissions result is $isPermissionGranted")
                granted.value = "check permissions result is $isPermissionGranted"
                if ("$isPermissionGranted".contains("false")) {
                    receiveFlow.value = "进入contains(\"false\")"
                    curNode?.let { node ->
                        authApi?.requestPermission(
                            node.id, Permission.DEVICE_MANAGER, Permission.NOTIFY
                        )?.addOnSuccessListener { permissions ->
                            receiveFlow.value = "进入requestPermission"
                            val permissionGrantedList = mutableListOf<String>()
                            for (permission in permissions) {
                                permissionGrantedList.add(permission.name)
                            }
                            granted.value = "granted permission is $permissionGrantedList"
                            receiveFlow.value = "granted permission is $permissionGrantedList"

                            hasGrantedFlow.value = true
                            future.complete(true)
                        }?.addOnFailureListener {
                            granted.value = "request permission failed:${it.message}"
                            receiveFlow.value = "request permission failed:${it.message}"
                            future.complete(false)
                        }
                    }
                } else {
//                    receiveFlow.value = "权限已获取无需请求"
                    hasGrantedFlow.value = true
                    future.complete(true)
                }
            }?.addOnFailureListener {
                Log.i("TAG", "check permissions failed:${it.message}")
                granted.value = "check permissions failed:${it.message}"
                receiveFlow.value = "check permissions failed:${it.message}"
                grantedErrFlow.value = true
                future.complete(false)
            }
        } ?: let {
            receiveFlow.value = "权限获取时无curNode"
            future.complete(false)
        }
        val grantedResult = future.await()
        if (hasGrantedFlow.value) {
//            receiveFlow.value = "手环权限已获取"
        } else {
            receiveFlow.value = "手环权限无法获取"
        }
        return grantedResult
    }

    fun sendNotify(notify: Pair<String, String>) {
        notifyApi?.sendNotify(curNode!!.id, notify.first, notify.second)
            ?.addOnSuccessListener(OnSuccessListener<Status> { status ->

                if (status.isSuccess) {
                    //发送通知成功,⼿表上会看到消息通知内容，表端应⽤⽆感知
                    receiveFlow.value = "发送通知成功"
                }
            })?.addOnFailureListener(OnFailureListener {
                //发送通知失败
                receiveFlow.value = "发送通知失败"
            })
    }

    fun reqBookInfo(launch: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
        if (launch) {
            launchBandApp()
            delay(2000)
        }
        curNode?.let {
            messageApi?.sendMessage(
                curNode!!.id, Json.encodeToString(BandMessage.BookInfo()).toByteArray()
            )?.addOnSuccessListener {
                receiveFlow.value = "发送reqBookInfo ${Json.encodeToString(BandMessage.BookInfo())}"
            }
                ?.addOnFailureListener {
                    receiveFlow.value = "发送reqBookInfo失败 ${it.stackTraceToString()}"
                }
        }
    }

    fun handleMessage(message: ByteArray) {
        val raw = message.decodeToString()
        val type =
            Json.parseToJsonElement(raw).jsonObject["type"]!!.jsonPrimitive.content
        receiveFlow.value = "registerMessageListener type $type"
        when (type) {
            "book_info" -> {
                val content =
                    Json.parseToJsonElement(raw).jsonObject["content"]!!.jsonArray.map {
                        Json.decodeFromJsonElement<Book>(it)
                    }
                receiveFlow.value = "receive message:type $type message $content"
                bandBooksFlow.value = content
                viewModelScope.launch(Dispatchers.IO) {
                    val books = appDatabase.bookDao().getAll()
                    books.forEach { book ->
                        if (!(content.any { it.id == book.id })) {
                            appDatabase.chapterDao().setAllUnSync(book.id)
                            if (book.synced) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        appContext,
                                        "检测到${book.name}被删除",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                syncFlow.value = Pair(
                                    appDatabase.chapterDao().countSynced(book.id),
                                    appDatabase.chapterDao().countChapterBy(book.id)
                                )
                                appDatabase.bookDao().update(book.copy(synced = false))
                            }
                        }
                    }
                }
            }

            else -> {
                val content =
                    Json.parseToJsonElement(raw).jsonObject["content"]!!.jsonPrimitive.content
                receiveFlow.value = "receive message:type other $type message $content"
            }
        }
    }

    private fun registerMessageListener() {
        if (hasListener) return
        curNode?.let { node ->
            messageApi?.addListener(node.id) { _, message ->
                handleMessage(message)
            }?.addOnSuccessListener {
                receiveFlow.value = "listener success"
                hasListener = true
            }?.addOnFailureListener {
                hasListener = false
                receiveFlow.value = "listener err ${it.message}"
            }
        }
    }

    fun syncToBand(bookId: Int) = viewModelScope.launch(Dispatchers.IO) {
        when (syncStatus.value) {
            SyncStatus.SyncRe -> syncStatus.value = SyncStatus.Syncing
                .also { isSyncing.value = true }
                .also { cancelSync = false }

            SyncStatus.Syncing -> {
                cancelSync = true
                syncStatus.value = SyncStatus.SyncDef.also { isSyncing.value = false }
                return@launch
            }

            SyncStatus.SyncDef -> syncStatus.value =
                SyncStatus.Syncing
                    .also { cancelSync = false }
                    .also { isSyncing.value = true }

            else -> {}
        }
        if (!launchBandApp()) {
            return@launch
        }
        var syncJob: Job? = null
        if (hasGrantedFlow.value) {
            isSyncing.value = true
            syncStatus.value = SyncStatus.Syncing
            val book = appDatabase.bookDao().getBookById(bookId)
            receiveFlow.value = "syncToBand"
            if (syncFlow.value.first.equals(syncFlow.value.second)) {
//                syncStatus.value = SyncStatus.SyncRe
                appDatabase.chapterDao().setAllUnSync(bookId)
                syncFlow.value = Pair(
                    appDatabase.chapterDao().countSynced(bookId),
                    appDatabase.chapterDao().countChapterBy(bookId)
                )
            }

            book?.let {
                receiveFlow.value = "syncToBand $book"
                appDatabase.bookDao().update(book.copy(synced = true))
                val msg = Json.encodeToString(BandMessage.AddBook(book))
                messageApi?.sendMessage(
                    curNode!!.id, msg.toByteArray()
                )?.addOnSuccessListener {
                    var count = 0
                    receiveFlow.value = "syncToBand book added"
                    /*viewModelScope.launch(Dispatchers.IO) {
                        while (true) {
                            count = syncFlow.value.first
                            delay(1000)
                            if (count.equals(syncFlow.value.first)) {
                                syncStatus.value = SyncStatus.SyncDef
                                isSyncing.value = false
                            }
                            if (count < syncFlow.value.first) {
                                syncStatus.value = SyncStatus.Syncing
                                isSyncing.value = true
                            }
                        }
                    }*/

                    syncJob = viewModelScope.launch(Dispatchers.IO) {
                        receiveFlow.value =
                            "send chapters ${appDatabase.chapterDao().countUnSynced(bookId)}"

                        appDatabase.chapterDao().getUnSyncChapters(bookId)
                            .map { it.toChunk() }
                            .flatten()
                            .forEach loop@{ chapterByChunk ->
                                if (cancelSync) {
                                    syncStatus.value = SyncStatus.SyncDef
                                    return@loop
                                }
                                //如果chapter的index 是10的倍数就发送一次launchbandapp
                                if (chapterByChunk.index % 10 == 0) {
                                    launchBandApp().let {
                                        if (!it) {
                                            syncStatus.value = SyncStatus.SyncDef
                                            return@loop
                                        }
                                    }
                                }


                                val future = CompletableFuture<Boolean>()
                                val format = Json { encodeDefaults = true }
                                val chapterMsg =
                                    format.encodeToString(BandMessage.AddChapter(chapterByChunk))
                                Log.i("TAG", "序列化chapter $chapterMsg")
                                messageApi?.sendMessage(curNode!!.id, chapterMsg.toByteArray())
                                    ?.addOnSuccessListener {
                                        viewModelScope.launch {
                                            if (chapterByChunk.last) {
                                                appDatabase.chapterDao()
                                                    .update(
                                                        chapterByChunk.raw!!.copy(sync = true)
                                                    )
                                                syncFlow.value = syncFlow.value.copy(
                                                    first = appDatabase.chapterDao()
                                                        .countSynced(chapterByChunk.bookId)
                                                )
                                            }
                                            future.complete(true)
                                        }
                                    }?.addOnFailureListener {
                                        syncStatus.value = SyncStatus.SyncFail
                                        Log.e("TAG", "send chapter err ${it.stackTraceToString()}")
                                        future.complete(false)
                                    }
                                if (!future.await()) {
                                    syncStatus.value = SyncStatus.SyncFail
                                    return@loop
                                } else if (chapterByChunk.last && chapterByChunk.index == book.chapters - 1) {
                                    syncStatus.value = SyncStatus.SyncRe
                                    isSyncing.value = false
                                }
                                if (chapterByChunk.content.length > 2000) {
                                    delay(80)
                                } else {
                                    delay(60)
                                }
                            }
                    }
                }?.addOnFailureListener {
                    syncStatus.value = SyncStatus.SyncFail
                    isSyncing.value = false
                }
            }
        }
    }

    fun delBook(book: Book) = viewModelScope.launch(Dispatchers.IO) {
        appDatabase.bookDao().delete(book)
        appDatabase.chapterDao().deleteBy(book.id)
    }


}



