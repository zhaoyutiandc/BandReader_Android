package com.example.bandReader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bandReader.data.AppConfig
import com.example.bandReader.data.AppDatabase
import com.example.bandReader.data.BandMessage
import com.example.bandReader.data.Book
import com.example.bandReader.data.Chapter
import com.example.bandReader.data.Cover
import com.example.bandReader.data.SyncStatus
import com.example.bandReader.data.SyncType
import com.example.bandReader.data.ChapterWithoutContent
import com.example.bandReader.data.getRaw
import com.example.bandReader.data.toChunk
import com.xiaomi.xms.wearable.Status
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

val format = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext val appContext: Context) :
    ViewModel() {
    // mutable stat-flow books
    val appDatabase = AppDatabase.getInstance(appContext)
    var hasGrantedFlow = MutableStateFlow(false)
    private var hasListener = false
    var cancelSync = false
    var books = appDatabase.bookDao().getAllFlow()

    //    var chapters: Flow<List<Chapter>> = flow { }
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
    val isUpdateting = MutableStateFlow(false)
    val chapterLoading = MutableStateFlow(false)
    private val granted = MutableStateFlow("")
    val grantedErrFlow = MutableStateFlow(false)
    var syncFail = false
    val syncFlow = MutableStateFlow(Pair(0, 0))
    val receiveFlow = MutableStateFlow("receiveFlow")
    val restartFlow = MutableStateFlow(false)
    val bandBooksFlow = MutableStateFlow<List<Book>>(emptyList())
    val currentBookFlow = MutableStateFlow<Book?>(null)
    var notInstall = false
    val shareUri = MutableStateFlow(Uri.EMPTY)
    var bufferedReaderFlow = MutableStateFlow<BufferedReader?>(null)
    var printArr = MutableStateFlow<List<String>>(emptyList<String>().toMutableList())
    var appConfigFlow = MutableStateFlow(AppConfig(showLog = false, boost = false))
    val avergeLengthFlow = MutableStateFlow(0)
    val messageFlow = MutableStateFlow("")
    var syncingList = false
    var chapterListFlow = MutableStateFlow<List<ChapterWithoutContent>>(emptyList())
    var rangeChapterListFlow = MutableStateFlow<List<ChapterWithoutContent>>(emptyList())
    var currentSendChapterFlow = MutableStateFlow<Chapter?>(null)
    var currentSyncType:SyncType = SyncType.UnSync

    suspend fun getChapters(bookId: Int) {
        syncFlow.value = Pair(
            appDatabase.chapterDao().countSynced(bookId),
            appDatabase.chapterDao().countChapterBy(bookId)
        )
//        chapters = appDatabase.chapterDao().getChaptersByBookId(bookId)
        chapterLoading.value = true
        chapterListFlow.value = appDatabase.chapterDao().getChaptersWithoutContent(bookId)
//        delay(850)
        chapterLoading.value = false
        receiveFlow.value =
            "syncFlow.value.first == syncFlow.value.second ${syncFlow.value.first} ${syncFlow.value.second} ${syncFlow.value.first == syncFlow.value.second}"
        if (syncFlow.value.first == syncFlow.value.second) {
            syncStatus.value = SyncStatus.SyncRe
        } else {
            syncStatus.value = SyncStatus.SyncDef
        }
    }


    init {
        viewModelScope.launch(Dispatchers.IO) {
            appContext.dataStore.data
                .map { preferences ->
                    // No type safety.
                    preferences[APP_CONFIG] ?: ""
                }.collect { value ->
                    if (value.contains("boost"))
                        appConfigFlow.value = Json.decodeFromString(value)
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            appConfigFlow.collectLatest {
                receiveFlow.value = "appConfigFlow $it"
                appContext.dataStore.edit { settings ->
                    settings[APP_CONFIG] = Json.encodeToString(it.copy(boost = false))
                }
            }
        }
        nodeApi = Wearable.getNodeApi(appContext)
        authApi = Wearable.getAuthApi(appContext)
        serviceApi = Wearable.getServiceApi(appContext)
        notifyApi = Wearable.getNotifyApi(appContext)
        messageApi = Wearable.getMessageApi(appContext)
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
            syncStatus.collectLatest {
                if (syncFail) {
                    receiveFlow.value = "syncFail"
                    syncStatus.value = SyncStatus.SyncFail
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            receiveFlow.collectLatest {
                Log.i("TAG", "======== receiveFlow:$it")
                //把日志放到printArr.value的第一个位置
                printArr.value = printArr.value.toMutableList().apply {
                    add(0, it)
                }
//                printArr去重
//                printArr.value = printArr.value.distinct()
                //如果printarr的长度大于就删除最后一个
                if (printArr.value.size > 200) {
                    printArr.value = printArr.value.dropLast(1)
                }
            }
        }
        /*viewModelScope.launch(Dispatchers.IO) {
            if (chapterList.isEmpty()) return@launch
            avergeLengthFlow.value = chapterList.map { it.name.length }.average().toInt()
        }*/
        viewModelScope.launch(Dispatchers.IO) {
            messageFlow.collectLatest {
                //消息接收
                handleMessage(it)
            }
        }
    }


    suspend fun getConnectedDevice(): Boolean {
        val connectedFuture = CompletableFuture<Boolean>()
        nodeApi?.connectedNodes?.addOnSuccessListener {
            if (it.size > 0) {
                curNode = it[0]
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
        val isReady = connected && checkPermissionGranted() && isBandAppInstalled()
        if (isReady) {
            if (!printArr.value.contains("手环应用已就绪")) {
                receiveFlow.value = "手环应用已就绪"
                registerMessageListener()
                viewModelScope.launch(Dispatchers.IO) {
                    reqBookInfo()
                }
            }
            grantedErrFlow.value = false
        } else {
            receiveFlow.value = "手环应用未就绪"
            hasGrantedFlow.value = false
            hasListener = false
            if (syncStatus.value == SyncStatus.Syncing) {
                restartFlow.value = true
            }
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
            //rug printarr `没有手环应用已安装`这句话才执行
            if (!printArr.value.contains("手环应用已安装")) {
                receiveFlow.value = "手环应用已安装"
            }
        } else {
            receiveFlow.value = "手环应用未安装"
        }
        return installed
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

    suspend fun reqBookInfo(launch: Boolean = true) {
        val future = CompletableFuture<Boolean>()
        if (launch) {
            launchBandApp().let {
                if (!it) {
                    return
                }
            }
            delay(2000)
        }
        curNode?.let {
            messageApi?.sendMessage(
                curNode!!.id, Json.encodeToString(BandMessage.BookInfo()).toByteArray()
            )?.addOnSuccessListener {
                receiveFlow.value =
                    "发送reqBookInfo ${Json.encodeToString(BandMessage.BookInfo())}"
                future.complete(true)
            }
                ?.addOnFailureListener {
                    receiveFlow.value = "发送reqBookInfo失败 ${it.message}"
                    future.complete(false)
                }
        }
        future.await()
    }

    var sendLock = CompletableFuture<Boolean>()
    var sendTime = 0L
    fun handleMessage(message: String) {
        receiveFlow.value = "handleMessage $message"
        if (message.isEmpty() || message.isBlank() || message == "null") return
        val json = try {
            Json.parseToJsonElement(message)
        } catch (e: Exception) {
            receiveFlow.value = "handleMessage err ${e.message}"
            return
        }
        var rawdata = (json.jsonObject["data"]!!).toString()
            .replace("""\""", """""")
        rawdata = rawdata.substring(1, rawdata.length - 1)
        val data = try {
            Json.parseToJsonElement(rawdata)
        } catch (e: Exception) {
            receiveFlow.value = "handleMessage err ${e.message}"
            return
        }
        receiveFlow.value = "handleMessage data $data"
        val type = data.jsonObject["type"]!!.jsonPrimitive.content
        receiveFlow.value = "handleMessage type $type"
        when (type) {
            "book_info" -> {
                val content =
                    data.jsonObject["content"]!!.jsonArray.map {
                        val jbook = format.decodeFromJsonElement<Book>(it)
                        val list = it.jsonObject["list"]?.jsonPrimitive?.booleanOrNull
                        if (list == null || !list) {
                            viewModelScope.launch(Dispatchers.IO) {
                                syncingList = true
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        appContext,
                                        "正在同步${jbook.name}章节列表",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                listInfo(
                                    bookId = jbook.id,
                                    chapters = appDatabase.chapterDao()
                                        .getChaptersWithoutContent(jbook.id)
                                )
                                delay(1000)
                                syncingList = false
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        appContext,
                                        "${jbook.name}章节列表同步完成",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        return@map jbook
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

            "test_saved" -> {

            }

            "chunk_saved" -> {
                sendLock.complete(true)
                receiveFlow.value = "手环已接收chunk"
                sendLock = CompletableFuture<Boolean>()
            }

            "chapter_saved" -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val savedIndex = data.jsonObject["content"]!!.jsonPrimitive.int
                    val chapterWithoutContent =
                        chapterListFlow.value.find { it.index == savedIndex } ?: return@launch
                    val curChapter = appDatabase.chapterDao().getOneById(chapterWithoutContent.id)
                    appDatabase.chapterDao()
                        .update(
                            curChapter.copy(sync = true)
                        )
                    syncFlow.value = syncFlow.value.copy(
                        first = appDatabase.chapterDao()
                            .countSynced(curChapter.bookId)
                    )

                    //如果是最后一章 就不再同步下一章
                    val lastChapter = if (currentSyncType is SyncType.UnSync) {
                        chapterListFlow.value.last()
                    } else {
                        rangeChapterListFlow.value.last()
                    }
                    if (savedIndex == lastChapter.index) {
                        if (currentSyncType is SyncType.UnSync) syncStatus.value = SyncStatus.SyncRe
                        else syncStatus.value = SyncStatus.SyncDef
                        isSyncing.value = false
                        cancelSync = true
                        return@launch
                    }
                    if (!cancelSync) {
                        syncNextChapter(savedIndex + 1)
                        sendTime = System.currentTimeMillis()
                        delay(2000)
                        val curTime = System.currentTimeMillis()
                        receiveFlow.value = "===间隔 ${curTime - sendTime}"
                        if ((curTime - sendTime) >= 2000 && savedIndex != chapterListFlow.value.last().index && !cancelSync && syncStatus.value != SyncStatus.SyncRe) retry(
                            savedIndex
                        )
                    }
                }
            }

            "log" -> {
                receiveFlow.value = "band log.txt: $data"
            }

            else -> {
                receiveFlow.value = "receive message:type other $type message $json"
            }
        }
    }

    suspend fun listInfo(bookId: Int, chapters: List<ChapterWithoutContent>) {
        delay(140)
        //json arrar
        Log.i("TAG", "listInfo: ${chapters.size}")
        for ((index, chunk) in chapters.chunked(200).withIndex()) {
            val future = CompletableFuture<Boolean>()
            val jsonArr = chunk.map {
                Json.encodeToString(
                    JsonObject(
                        mapOf(
                            "title" to JsonPrimitive(it.name),
                            "id" to JsonPrimitive(it.id),
                            "index" to JsonPrimitive(it.index),
                            "paging" to JsonPrimitive(it.paging),
                        )
                    )
                )
            }
            val info = JsonObject(
                mapOf(
                    "index" to JsonPrimitive(index),
                    "bid" to JsonPrimitive(bookId),
                    "first" to JsonPrimitive(index == 0),
                    "end" to JsonPrimitive(chunk.last().id == chapters.last().id),
                    "list" to JsonPrimitive(jsonArr.joinToString("\n"))
                )
            )
            val msg = Json.encodeToString(BandMessage.ListInfo(info))
            curNode?.let {
                messageApi?.sendMessage(
                    curNode!!.id,
                    msg.toByteArray()
                )?.addOnSuccessListener {
                    receiveFlow.value =
                        "发送ListInfo ${index} ${chunk.size}"
                    future.complete(true)
                }?.addOnFailureListener {
                    receiveFlow.value = "发送ListInfo失败 ${it.message}"
                    future.complete(false)
                }
            }
            future.await()
            delay(200)
        }
    }

    private val messageListener: OnMessageReceivedListener =
        OnMessageReceivedListener { did, message ->
            this.messageFlow.value = message.decodeToString()
        }

    fun registerMessageListener() {
        curNode?.let { node ->
            messageApi?.addListener(node.id, messageListener)?.addOnSuccessListener {
                receiveFlow.value = "listener success"
                hasListener = true
            }?.addOnFailureListener {
                hasListener = false
                receiveFlow.value = "listener err ${it.message}"
            }
        }
    }

    suspend fun syncBook(bookId: Int): Boolean {
        val future = CompletableFuture<Boolean>()
        val book = appDatabase.bookDao().getBookById(bookId)
        book?.let {
            appDatabase.bookDao().update(book.copy(synced = true))
            val msg = Json.encodeToString(BandMessage.AddBook(book))
            messageApi?.sendMessage(
                curNode!!.id, msg.toByteArray()
            )?.addOnSuccessListener {
                receiveFlow.value = "已同步书籍信息 ${book.name}"
                future.complete(true)
            }?.addOnFailureListener {
                future.complete(false)
            }
        }
        delay(100)
        return future.await()
    }

    suspend fun syncTest() {
        //50万个 ‘啊’
        val largeString = "啊".repeat(500000)
        //5000个一个chunk
        val chunks = largeString.chunked(5000)
        chunks.forEach { chunk ->

        }
    }

    suspend fun syncChapterInfo(bookId: Int) {
        receiveFlow.value =
            "send chapters ${appDatabase.chapterDao().countUnSynced(bookId)}"
        if (appDatabase.chapterDao()
                .countUnSynced(bookId) == appDatabase.chapterDao()
                .countChapterBy(bookId)
        ) {
            receiveFlow.value = "开始同步章节列表"
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "开始同步章节列表",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
            listInfo(
                bookId,
                appDatabase.chapterDao().getChaptersWithoutContent(bookId)
            )
            receiveFlow.value = "同步章节列表完成"
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "同步章节列表完成",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
            delay(1000)
        }
    }

    suspend fun retry(index: Int) {
        launchBandApp()
        delay(1400)
        syncNextChapter(index)
    }

    suspend fun restartBandApp() {
        val msg = Json.encodeToString(BandMessage.Exit())
        messageApi?.sendMessage(curNode!!.id, msg.toByteArray())
        delay(1400)
        launchBandApp()
        delay(2000)
    }

    suspend fun syncNextChapter(index: Int) {
        Log.e("TAG", "syncNextChapter: $index ${Date()}")
        receiveFlow.value = "进入syncNextChapter $index"
        syncStatus.value = SyncStatus.Syncing
        isSyncing.value = true
        //每20章点亮手环
        /*if (index % 20 == 0) {
            if (!launchBandApp()) {
                syncStatus.value = SyncStatus.SyncDef
                return
            }
        }*/

        val chapterWithoutContent =
            chapterListFlow.value.find { it.index == index }
        val curChapter = appDatabase.chapterDao().getOneById(chapterWithoutContent!!.id)
        currentSendChapterFlow.value = curChapter
        if (curChapter == null) {
            receiveFlow.value = "找不到curChapter $index"
            return
        }
        receiveFlow.value = "找到curChapter $index"
        curChapter.toChunk().forEachIndexed { chunkIdx, chapterByChunk ->
            val future = CompletableFuture<Boolean>()
            receiveFlow.value = "进入forEach index $index chunkIdx $chunkIdx"
            val temp = chapterByChunk.copy(raw = null)
            val chapterMsg =
                format.encodeToString(BandMessage.AddChapter(temp))
            //receiveFlow.value = "发送 chapterByChunk | temp : $temp | temp json $chapterMsg"
            messageApi?.sendMessage(curNode!!.id, chapterMsg.toByteArray())
                ?.addOnSuccessListener {
                    receiveFlow.value =
                        "发送 chapterByChunk ${temp.bookId}/${temp.paging}/${index}_${temp.name}"
                    future.complete(true)
                }
                ?.addOnFailureListener {
                    receiveFlow.value = "发送 chapterByChunk err ${it.message}"
                    syncStatus.value = SyncStatus.SyncFail
                    isSyncing.value = false
                    future.complete(false)
                }
            sendLock.await()
            future.await()
        }
    }

    suspend fun syncv2(bookId: Int, syncType: SyncType = SyncType.UnSync) {
        currentSyncType = syncType
        cancelSync = false
        if (!launchBandApp()) {
            withContext(Dispatchers.IO) {
                Toast.makeText(appContext, "未连接手环", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (syncStatus.value is SyncStatus.Syncing) {
            cancelSync = true
            syncStatus.value = SyncStatus.SyncDef
            return
        }

        if (!syncBook(bookId)) {
            receiveFlow.value = "syncBook err"
            return
        }
        syncChapterInfo(bookId)

        if (syncType is SyncType.UnSync) {
            currentSendChapterFlow.value = null
            //chapterListFlow.value中第一个sync是false的
            if (syncStatus.value is SyncStatus.SyncRe) {
                receiveFlow.value = "firstUnSync is null"
                appDatabase.chapterDao().setAllUnSync(bookId)
            }
            //421 241
            chapterListFlow.value = appDatabase.chapterDao().getChaptersWithoutContent(bookId)
            val firstUnSync = chapterListFlow.value.find { !it.sync }
            if (firstUnSync != null) {
                syncNextChapter(firstUnSync.index)
            }
        } else if (syncType is SyncType.Range) {
            //这个range start会>0并且end <=最后一章的index 但是 肯会出现 start = 1 end = 1的情况这种就是只有第一章
            rangeChapterListFlow.value = appDatabase.chapterDao().getChaptersWithoutContent(bookId)
                .filter { it.index in syncType.start..syncType.end }
            receiveFlow.value = "分段同步 " + rangeChapterListFlow.value.map { it.index }.toString()
            syncNextChapter(rangeChapterListFlow.value.first().index)
            //按范围过滤章节
            /*chapterListFlow.value = appDatabase.chapterDao().getChaptersWithoutContent(bookId)
                .filter {
                    return it.index
                }
            syncNextChapter(syncType.start)*/
        }
    }

    fun syncToBand(bookId: Int, syncType: SyncType = SyncType.UnSync) =
        viewModelScope.launch(Dispatchers.IO) {
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
                if (syncFlow.value.first == syncFlow.value.second) {
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
                        syncJob = viewModelScope.launch(Dispatchers.IO) {
                            receiveFlow.value =
                                "send chapters ${appDatabase.chapterDao().countUnSynced(bookId)}"
                            if (appDatabase.chapterDao()
                                    .countUnSynced(bookId) == appDatabase.chapterDao()
                                    .countChapterBy(bookId)
                            ) {
                                receiveFlow.value = "开始同步章节列表"
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        appContext,
                                        "开始同步章节列表",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                                listInfo(
                                    bookId,
                                    appDatabase.chapterDao().getChaptersWithoutContent(bookId)
                                )
                                receiveFlow.value = "同步章节列表完成"
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        appContext,
                                        "同步章节列表完成",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                                delay(1000)
                            }
                            /*chapterListFlow.value = appDatabase.chapterDao().getChaptersByBookIdSync(bookId)*/
                            chapterListFlow.value.filter { !it.sync }
                                .map { it.getRaw(appDatabase).toChunk() }
                                .flatten()
                                .forEach loop@{ chapterByChunk ->
                                    if (cancelSync) {
                                        syncStatus.value = SyncStatus.SyncDef
                                        return@loop
                                    }
                                    //如果chapter的index 是10的倍数就发送一次launchbandapp
                                    if (chapterByChunk.index % 20 == 0) {
                                        launchBandApp().let {
                                            if (!it) {
                                                syncStatus.value = SyncStatus.SyncDef
                                                return@loop
                                            }
                                        }
                                    }
                                    syncStatus.value = SyncStatus.Syncing
                                    val future = CompletableFuture<Boolean>()
                                    val format = Json { encodeDefaults = true }
                                    val chapterMsg =
                                        format.encodeToString(BandMessage.AddChapter(chapterByChunk))
                                    messageApi?.sendMessage(curNode!!.id, chapterMsg.toByteArray())
                                        ?.addOnSuccessListener {
                                            receiveFlow.value =
                                                "发送完成 ${chapterByChunk.id} ${chapterByChunk.index}章"
                                            viewModelScope.launch {
                                                /*withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        appContext,
                                                        "发送完成 ${chapterByChunk.id} ${chapterByChunk.index}章",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }*/
                                                /*if (chapterByChunk.last) {
                                                    appDatabase.chapterDao()
                                                        .update(
                                                            chapterByChunk.raw!!.copy(sync = true)
                                                        )
                                                    syncFlow.value = syncFlow.value.copy(
                                                        first = appDatabase.chapterDao()
                                                            .countSynced(chapterByChunk.bookId)
                                                    )
                                                }*/
                                                syncFail = false
                                                future.complete(true)
                                            }
                                        }?.addOnFailureListener {
                                            syncStatus.value = SyncStatus.SyncFail
                                            Log.e(
                                                "TAG",
                                                "send chapter err ${it.stackTraceToString()}"
                                            )
                                            viewModelScope.launch(Dispatchers.IO) {
                                                appDatabase.chapterDao()
                                                    .update(
                                                        chapterByChunk.raw!!.copy(sync = false)
                                                    )
                                            }
                                            cancelSync = true
                                            syncFail = true
                                            future.complete(false)
                                        }
                                    if (!future.await()) {
                                        sendLock.complete(true)
                                        syncStatus.value = SyncStatus.SyncFail
                                        return@loop
                                    } else if (chapterByChunk.last && chapterByChunk.index == book.chapters - 1) {
                                        syncStatus.value = SyncStatus.SyncRe
                                        isSyncing.value = false
                                    } else {
                                        sendLock.await()
                                        /*if (chapterByChunk.index>0&&chapterByChunk.last&&chapterByChunk.index%100==0){
                                            sendLock.await()
                                            delay(300)
                                            restartBandApp()
                                        }
                                        if (chapterByChunk.content.length < 1000) {
                                            delay(60)
                                        } else {
                                            delay(140)
                                        }*/
                                    }
//                                    receiveFlow.value = "到达wait ${chapterByChunk.id} ${chapterByChunk.index}章"
//                                    sendLock.await()
                                }
                        }
                    }?.addOnFailureListener {
                        syncStatus.value = SyncStatus.SyncFail
                        isSyncing.value = false
                    }
                }
            }
        }

    fun changeBook(book: Book, cover: Bitmap? = null) = viewModelScope.launch(Dispatchers.IO) {
        appDatabase.bookDao().update(book)
        if (launchBandApp()) {
            delay(1500)
            curNode?.let {
                val addFuture = CompletableFuture<Boolean>()
                val msg = Json.encodeToString(BandMessage.AddBook(book))
                messageApi?.sendMessage(
                    curNode!!.id, msg.toByteArray()
                )?.addOnSuccessListener { addFuture.complete(true) }
                    ?.addOnFailureListener { addFuture.complete(false) }
                addFuture.await()
                messageApi?.sendMessage(
                    curNode!!.id,
                    Json.encodeToString(BandMessage.UpdateBook(book)).toByteArray()
                )?.addOnSuccessListener {
                    receiveFlow.value =
                        "UpdateBook  start  ${Json.encodeToString(BandMessage.UpdateBook(book))}"
                    viewModelScope.launch(Dispatchers.IO) {
                        delay(1600)
                        cover?.let {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(appContext, "开始发送封面", Toast.LENGTH_SHORT)
                                    .show()
                            }
                            val stream = ByteArrayOutputStream()
                            cover.compress(Bitmap.CompressFormat.PNG, 90, stream)
                            val byteArray = stream.toByteArray()
                            //byteArray按3000长度切割
                            val chunks = byteArray.toList().chunked(5000)
                            chunks.forEachIndexed { index, chunk ->
                                var temp = chunk.joinToString(",")
                                if (index == chunks.size - 1) {
                                    val random = (0..99).random()
                                    temp = "$temp,$random,$random,$random,$random"
                                }
                                val future = CompletableFuture<Boolean>()
                                messageApi?.sendMessage(
                                    curNode!!.id,
                                    Json.encodeToString(
                                        BandMessage.UpdateCover(
                                            Cover(
                                                book.id,
                                                first = index == 0,
                                                temp,
                                                end = index == chunks.size - 1
                                            )
                                        )
                                    ).toByteArray(),
                                )?.addOnSuccessListener {
                                    receiveFlow.value = "UpdateCover ${book.id}"
                                    future.complete(true)
                                }?.addOnFailureListener {
                                    receiveFlow.value = "UpdateCover失败 ${it.message}"
                                    future.complete(false)
                                }
                                future.await()
                                delay(100)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(appContext, "封面发送完成", Toast.LENGTH_SHORT)
                                    .show()
                            }
                            Log.i("TAG", "byteArray:${byteArray.size}")
                        }
                    }
                }
                    ?.addOnFailureListener {
                        receiveFlow.value = "UpdateBook失败 ${it.message}"
                    }
            }
        }

    }

    fun delBook(book: Book) = viewModelScope.launch(Dispatchers.IO) {
        appDatabase.bookDao().delete(book)
        appDatabase.chapterDao().deleteBy(book.id)
    }


}



