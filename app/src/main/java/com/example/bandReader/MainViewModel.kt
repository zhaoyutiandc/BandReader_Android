package com.example.bandReader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bandReader.data.AppDatabase
import com.example.bandReader.data.BandMessage
import com.example.bandReader.data.Book
import com.example.bandReader.data.Chapter
import com.example.bandReader.data.SyncStatus
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import com.xiaomi.xms.wearable.service.ServiceApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
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
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext private val appContext: Context) :
    ViewModel() {
    // mutable stat-flow books
    private val appDatabase = AppDatabase.getInstance(appContext)
    private var hasGranted = false
    private var hasListener = false

    var books = appDatabase.bookDao().getAllFlow()
    var chapters: Flow<List<Chapter>> = flow { }
    private var nodeApi: NodeApi? = null
    private var curNode: Node? = null
    private var messageApi: MessageApi? = null
    private var authApi: AuthApi? = null
    private var serviceApi: ServiceApi
    val bandConnected = MutableStateFlow(false)
    private val bandAppInstalled = MutableStateFlow(false)
    val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.SyncDef)
    val isSyncing = MutableStateFlow(false)
    private val granted = MutableStateFlow("")
    val grantedErrFlow = MutableStateFlow(false)
    val syncFlow = MutableStateFlow(Pair(0, 0))
    val receiveFlow = MutableStateFlow("receiveFlow")
    val restartFlow = MutableStateFlow(false)
    private val bandBooksFlow = MutableStateFlow<List<Book>>(emptyList())
    val currentBookFlow = MutableStateFlow<Book?>(null)
    var notInstall = false

    fun getChapters(bookId: Int) = viewModelScope.launch {
        chapters = appDatabase.chapterDao().getChaptersByBookId(bookId)
        syncFlow.value = Pair(
            appDatabase.chapterDao().countSynced(bookId),
            appDatabase.chapterDao().countChapterBy(bookId)
        )
    }

    fun importBook(bookName: String, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        logFlow.value = "split in"
        if (appDatabase.bookDao().getBookByName(bookName) != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "书名重复", Toast.LENGTH_SHORT).show()
            }
            return@launch
        }
        val book = Book(0, bookName, 0, 1, synced = false)
        val bookId = appDatabase.bookDao().insert(book)
        book.id = bookId.toInt()
        try {
            val chapters = readTxtFile(book, appContext, uri)
            book.chapters = chapters.size
            book.pages = chapters.last().paging
            appDatabase.bookDao().update(book)
            appDatabase.chapterDao().insertAll(chapters)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "导入成功 快去同步吧~", Toast.LENGTH_LONG).show()
            }
            filePath.value = ""
            openDialog.value = false
        } catch (e: Exception) {
            Log.i("TAG", "importBook: ${e.message}")
        }
    }

    init {
        nodeApi = Wearable.getNodeApi(appContext)
        messageApi = Wearable.getMessageApi(appContext)
        authApi = Wearable.getAuthApi(appContext)
        serviceApi = Wearable.getServiceApi(appContext)
        serviceApi.registerServiceConnectionListener(object : OnServiceConnectionListener {
            override fun onServiceConnected() {
                /*syncStatus.value = SyncStatus.SyncDef
                bandConnected.value = true
                reqBookInfo()*/
            }

            override fun onServiceDisconnected() {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(appContext, "手环应用已断开", Toast.LENGTH_SHORT).show()
                }
                bandConnected.value = false
                syncStatus.value = SyncStatus.SyncNoConn
            }
        })


        viewModelScope.launch(Dispatchers.IO) {
            repeat(Int.MAX_VALUE) {
                getConnectedDevice()
                delay(3000)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            delay(2000)
            if (isBandAppInstalled()) {
                withContext(Dispatchers.Main) {
//                        Toast.makeText(appContext, "app installed", Toast.LENGTH_SHORT).show()
                }
                    checkPermissionGranted()
            } else {
                notInstall = true
                while (syncStatus.value == SyncStatus.SyncNoApp) {
                    withContext(Dispatchers.Main) {
//                        Toast.makeText(appContext, "check installed", Toast.LENGTH_SHORT).show()
                    }
                    delay(2000L)
                    isBandAppInstalled()
                }
            }
        }
    }

    fun getConnectedDevice() = viewModelScope.launch(Dispatchers.IO) {
        nodeApi?.connectedNodes?.addOnSuccessListener {
            if (it.size > 0) {
                curNode = it[0]
                registerMessageListener()
                bandConnected.value = true
                viewModelScope.launch(Dispatchers.IO){
                    isBandAppInstalled()
                }
            } else {
                bandConnected.value = false
            }
        }?.addOnFailureListener {
            bandConnected.value = false
        }
    }

    private suspend fun launchBandApp(): Boolean {
        val future = CompletableFuture<Boolean>()
        curNode?.let { node ->
            nodeApi?.launchWearApp(node.id, "/home")?.addOnSuccessListener {
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
        if (checkPermissionGranted()){
            curNode?.let { node ->
                nodeApi?.isWearAppInstalled(node.id)?.addOnSuccessListener {
                    bandAppInstalled.value = it
                    if (it) {
                        if (notInstall) {
                            restartFlow.value = true
                        }
                        future.complete(true)
                    } else {
                        future.complete(false)
                        syncStatus.value = SyncStatus.SyncNoApp
                    }
                }?.addOnFailureListener {
                    receiveFlow.value = "isBandAppInstalled err ${it.message}"
                    bandAppInstalled.value = false
                    syncStatus.value = SyncStatus.SyncNoApp
                    future.complete(false)
                }
            }
        }else{
            future.complete(false)
        }

        return future.await()
    }

    private suspend fun checkPermissionGranted():Boolean {
        val future = CompletableFuture<Boolean>()
        curNode?.let { node ->
            val permissions = arrayOf<Permission>(Permission.DEVICE_MANAGER, Permission.NOTIFY)
            authApi?.checkPermissions(node.id, permissions)?.addOnSuccessListener { it ->
                val isPermissionGranted = mutableListOf<String>()
                for ((index, permission) in permissions.withIndex()) {
                    isPermissionGranted.add("${permission.name} grant status is ${it[index]}")
                }
                Log.i("TAB", "check permissions result is $isPermissionGranted")
                granted.value = "check permissions result is $isPermissionGranted"
                if ("$isPermissionGranted".contains("false")) {
                    curNode?.let { node ->
                        authApi?.requestPermission(
                            node.id, Permission.DEVICE_MANAGER, Permission.NOTIFY
                        )?.addOnSuccessListener { permissions ->
                            val permissionGrantedList = mutableListOf<String>()
                            for (permission in permissions) {
                                permissionGrantedList.add(permission.name)
                            }
                            granted.value = "granted permission is $permissionGrantedList"
                            hasGranted = true
                            future.complete(true)
                        }?.addOnFailureListener {
                            granted.value = "request permission failed:${it.message}"
                            future.complete(false)
                        }
                    }
                }else{
                    hasGranted = true
                    future.complete(true)
                }
            }?.addOnFailureListener {
                Log.i("TAG", "check permissions failed:${it.message}")
                granted.value = "check permissions failed:${it.message}"
                receiveFlow.value = "check permissions failed:${it.message}"
                grantedErrFlow.value = true
                future.complete(false)
            }
        }?:run{
            future.complete(false)
        }
        return future.await()
    }

    fun reqBookInfo() = viewModelScope.launch(Dispatchers.IO) {
        launchBandApp().run {
            curNode?.let {
                messageApi?.sendMessage(
                    curNode!!.id, Json.encodeToString(BandMessage.BookInfo()).toByteArray()
                )
            }
        }
    }

    private fun registerMessageListener() {
        receiveFlow.value = "start registerMessageListener"
        if (hasListener) return
        val messageListener = OnMessageReceivedListener { _, message ->
            val raw = message.decodeToString()
            receiveFlow.value = "registerMessageListener message $raw"
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
                                appDatabase.chapterDao().setAllUnSync(book.id)
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
        curNode?.let { node ->
            messageApi?.addListener(node.id, messageListener)?.addOnSuccessListener {
//                receiveFlow.value = "listener success"
                hasListener = true
            }?.addOnFailureListener {
//                receiveFlow.value = "listener err"

            }
        }
    }

    fun syncToBand(bookId: Int) = viewModelScope.launch(Dispatchers.IO) {
        if (hasGranted) {
            isSyncing.value = true
            syncStatus.value = SyncStatus.Syncing
            val book = appDatabase.bookDao().getBookById(bookId)
            receiveFlow.value = "syncToBand"

            book?.let {
                receiveFlow.value = "syncToBand $book"
                appDatabase.bookDao().update(book.copy(synced = true))
                val msg = Json.encodeToString(BandMessage.AddBook(book))
                messageApi?.sendMessage(
                    curNode!!.id, msg.toByteArray()
                )?.addOnSuccessListener {
                    var count = 0
                    receiveFlow.value = "syncToBand book added"
                    viewModelScope.launch(Dispatchers.IO) {
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
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        receiveFlow.value = "send chapters ${appDatabase.chapterDao().countUnSynced(bookId)}"

                        appDatabase.chapterDao().getUnSyncChapters(bookId).map { chapter ->
                            val future = CompletableFuture<Boolean>()
                            val chapterMsg =
                                Json.encodeToString(BandMessage.AddChapter(chapter))
                            Log.i("TAG", "序列化chapter $chapterMsg")
                            messageApi?.sendMessage(curNode!!.id, chapterMsg.toByteArray())
                                ?.addOnSuccessListener {
                                    viewModelScope.launch {
                                        appDatabase.chapterDao()
                                            .update(chapter.copy(sync = true))
                                        syncFlow.value = syncFlow.value.copy(
                                            first = appDatabase.chapterDao()
                                                .countSynced(chapter.bookId)
                                        )
                                        future.complete(true)
                                    }
                                }?.addOnFailureListener {
                                    syncStatus.value = SyncStatus.SyncFail
                                }
                            future.await()
                            delay(140)
                        }
                    }
                }?.addOnFailureListener {
                    syncStatus.value = SyncStatus.SyncFail
                }
            }
        }
    }

    fun delBook(book: Book) = viewModelScope.launch(Dispatchers.IO) {
        appDatabase.bookDao().delete(book)
        appDatabase.chapterDao().deleteBy(book.id)
    }
}


fun readTxtFile(book: Book, context: Context, uri: Uri): List<Chapter> {
    if (uri == Uri.EMPTY) {
        return emptyList()
    }
    logFlow.value = "uri:$uri"
    val inputStream = context.contentResolver.openInputStream(uri)
    val bufferedReader = BufferedReader(InputStreamReader(inputStream))
    var chapters = mutableListOf(Pair("", ""))
    val regex = Regex(""".{0,10}第.*章.{0,30}""")
//    logDialog.value = true
    logFlow.value = "开始读取"
    var counter = 0
    var counter2 = 0
    var temp = Pair("", "")
    bufferedReader.lines().forEach {
        counter++
        temp = if (regex.matches(it)) {
            counter2++
            if (temp.first.isNotEmpty()) {
                chapters.add(temp)
                Pair(it, "")
            } else {
                temp.copy(first = it)
            }
        } else {
            temp.copy(second = temp.second + it + "\n")
        }
    }
    if (temp.second.isNotBlank()) {
        chapters.add(temp)
    }
    logFlow.value = "读取完毕"
    inputStream?.close()
    chapters = chapters.filter { it.second.length > 50 }.toMutableList()
    val titles =
        chapters.mapIndexed { index, it -> "index:$index title:${it.first}\n" }.joinToString { it }
    showFlow.value = "共${counter}行 匹配${counter2}行 \n $titles"
    val chapterEntity = chapters.mapIndexed { index, it ->
        Chapter(
            index = index,
            bookId = book.id,
            name = it.first,
            content = it.second,
            paging = (index / 50) + 1
        )
    }
    return chapterEntity
}

