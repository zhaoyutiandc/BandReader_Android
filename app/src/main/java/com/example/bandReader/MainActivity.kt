package com.example.bandReader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.bandReader.data.Book
import com.example.bandReader.data.Chapter
import com.example.bandReader.data.SyncStatus
import com.example.bandReader.ui.composable.Title
import com.example.bandReader.ui.theme.BandReaderTheme
import com.example.bandReader.ui.theme.BgColor
import com.example.bandReader.ui.theme.Blue80
import com.example.bandReader.ui.theme.BlueGray80
import com.example.bandReader.ui.theme.BtnColor
import com.example.bandReader.ui.theme.BtnGrayColor
import com.example.bandReader.ui.theme.ItemColor
import com.example.bandReader.ui.theme.FillBtnColor
import com.example.bandReader.util.FileUtils
import com.permissionx.guolindev.PermissionX
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.util.concurrent.CompletableFuture
import kotlin.streams.toList


val openDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)
val filePath: MutableStateFlow<String> = MutableStateFlow("")
val showFlow: MutableStateFlow<String> = MutableStateFlow("")
val logFlow: MutableStateFlow<Any> = MutableStateFlow("")
val logDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)
val printState: MutableState<Boolean> = mutableStateOf(false)
var coverBitmapFlow: MutableStateFlow<Bitmap?> = MutableStateFlow(null)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val mainViewModel by viewModels<MainViewModel>()
    var fromIntent = false
    private lateinit var navController: NavController
    private val uriFlow: MutableStateFlow<Uri> = MutableStateFlow(Uri.EMPTY)
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var countDown = 0L
    val editDialogFlow = MutableStateFlow(false)
    var hasListener = false
    var messageApi: MessageApi? = null

    val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            //clear list
            uriFlow.value = Uri.EMPTY
            if (result.data?.data != null) {
                result.data?.data?.let {
                    uriFlow.value = it
                    mainViewModel.receiveFlow.value = "pickFileLauncher $it"
                    if (editDialogFlow.value && "$it".contains("image")) {
                        val bitmap = FileUtils.getBitmapFromUri(this@MainActivity, it)
                        coverBitmapFlow.value = bitmap
                        mainViewModel.receiveFlow.value = "图片路径 $it"
                        return@let
                    }

                    val path = FileUtils.getRealPath(this@MainActivity, it) ?: "无路径"
                    filePath.value = path
                    //判断path是图片
                    val suffix = path.substring(path.lastIndexOf(".") + 1)
                    if (suffix == "jpg" || suffix == "png" && editDialogFlow.value) {
                        val bitmap = FileUtils.getBitmapFromUri(this@MainActivity, it)
                        coverBitmapFlow.value = bitmap
                        mainViewModel.receiveFlow.value = "图片路径 $path"
                    } else {
                        if (!filePath.value.endsWith(".txt")) {
                            Toast.makeText(
                                this@MainActivity,
                                "不支持的文件格式",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        } else {
                            openDialog.value = true
                        }
                    }
                }
            }
        }
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { result ->
        mainViewModel.receiveFlow.value = "openDocumentLauncher"
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.receiveFlow.value = "onResume"
        if (intent.action == Intent.ACTION_VIEW) {
            fromIntent = true
            onImport()
        }
    }

    /*override fun onStart() {
        super.onStart()
        if (intent.action == Intent.ACTION_VIEW) {
            onImport()
        }
    }*/

    /*override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        mainViewModel.receiveFlow.value = "onNewIntent"
        if (intent?.action == Intent.ACTION_VIEW) {
            onImport()
        }
    }*/

    fun onImport() {
        mainViewModel.receiveFlow.value = "onImport"
        Log.e("TAG", "ACTION_VIEW")
        // 获取 txt 文件的路径
        uriFlow.value = intent.data ?: Uri.EMPTY
        val uri = intent.data
        val path = uri?.path
        Log.e("TAG", "path: $path")
        if (path != null) {
            filePath.value = path
            if (!filePath.value.endsWith(".txt")) {
                Toast.makeText(this@MainActivity, "不支持的文件格式", Toast.LENGTH_SHORT).show()
            } else {
                openDialog.value = true
            }
        }
    }

    @SuppressLint("Recycle")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initBlue()
        Log.e("TAG", "intent.action: ${intent.action}")
        if (intent.action == Intent.ACTION_VIEW) {
//            onImport()
        }

        messageApi = Wearable.getMessageApi(this)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.restartFlow.collect {
                    if (it) {
                        restart()
                    }
                }
            }
        }

        setContent {
            BandReaderTheme(darkTheme = true) {
                navController = rememberNavController()
                (navController as NavHostController).setLifecycleOwner(this@MainActivity)
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.background)
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val uriState = uriFlow.collectAsState(initial = Uri.EMPTY)
                    val receiveState = mainViewModel.receiveFlow.collectAsState()
                    val printArrState = mainViewModel.printArr.collectAsState()
                    val animation by remember {
                        mutableStateOf(
                            Pair(
                                fadeIn(
                                    animationSpec = tween(
                                        300, easing = LinearEasing
                                    )
                                ) + androidx.compose.animation.slideInHorizontally(animationSpec = tween(
                                    300,
                                    easing = EaseIn
                                ),
                                    //compose 屏幕宽度
                                    initialOffsetX = { fullWidth -> fullWidth }), fadeOut(
                                    animationSpec = tween(
                                        300, easing = LinearEasing
                                    )
                                ) + androidx.compose.animation.slideOutHorizontally(animationSpec = tween(
                                    300,
                                    easing = EaseOut
                                ),
                                    targetOffsetX = { fullWidth -> fullWidth })
                            )
                        )
                    }
                    Column {
                        //日志
                        AnimatedVisibility(printState.value) {
                            Column(
                                Modifier
                                    .height(300.dp)
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                SelectionContainer {
                                    Text(text = printArrState.value.joinToString("\n"))
                                }
                            }
                        }
                        NavHost(
                            navController = navController as NavHostController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                Home(mainViewModel.books, toDetail = { book ->
                                    if (mainViewModel.syncStatus.value == SyncStatus.Syncing && mainViewModel.currentBookFlow.value!!.id != book.id) {
                                        Toast.makeText(
                                            context,
                                            "${mainViewModel.currentBookFlow.value!!.name}同步中请稍后",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        mainViewModel.currentBookFlow.value = book
                                        navController.navigate("detail")
                                        mainViewModel.reqBookInfo(true)
                                        mainViewModel.getChapters(book.id)
                                    }

                                }, pickFile = {
                                    PermissionX.init(this@MainActivity as FragmentActivity)
                                        .permissions(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(
                                                Manifest.permission.READ_MEDIA_VIDEO,
                                                Manifest.permission.READ_MEDIA_IMAGES,
                                            )
                                            else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        ).request { allGranted, _, deniedList ->
                                            if (allGranted) {
                                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                intent.type = "*/*"
                                                pickFileLauncher.launch(intent)
                                            }
                                        }

                                }, importBook = { input ->
                                    input?.let {
                                        if (mainViewModel.isImporting.value) {
                                            Toast.makeText(
                                                context, "正在导入中请稍后", Toast.LENGTH_SHORT
                                            ).show()
                                            return@let
                                        }
                                        if (input.isBlank()) {
                                            Toast.makeText(
                                                context, "请输入书名!", Toast.LENGTH_SHORT
                                            ).show()
                                            return@let
                                        }
                                        importBook(
                                            bookName(input),
                                            uriState.value,
                                        )
                                    }
                                })
                            }
                            composable("detail",
                                enterTransition = { animation.first },
                                exitTransition = { animation.second }) {
                                DetailScreen(
                                    mainViewModel.chapters,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mainViewModel.curNode?.let {
            messageApi?.removeListener(mainViewModel.curNode!!.id)
                ?.addOnSuccessListener { mainViewModel.receiveFlow.value = "listener 卸载" }
                ?.addOnFailureListener {
                    mainViewModel.receiveFlow.value =
                        "removeListener fail ${it.stackTraceToString()}"
                }
        }
        super.onDestroy()
    }

    private fun initBlue() {
        return
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        PermissionX.init(this@MainActivity as FragmentActivity).permissions(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT
            else Manifest.permission.BLUETOOTH
        ).request { allGranted, _, deniedList ->
            if (allGranted) {
                // 注册蓝牙状态改变的广播接收器
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        // 获取蓝牙状态
                        val state = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF
                        )

                        // 根据蓝牙状态进行处理
                        when (state) {
                            BluetoothAdapter.STATE_ON -> {
                                // 蓝牙已开启
                                // ...
                            }

                            BluetoothAdapter.STATE_OFF -> {
                                // 蓝牙已关闭
                                // ...
                            }

                            BluetoothAdapter.STATE_TURNING_ON -> {
                                // 蓝牙正在开启
                                // ...
                                Toast.makeText(
                                    this@MainActivity, "蓝牙正在开启", Toast.LENGTH_SHORT
                                ).show()
                                if (mainViewModel.grantedErrFlow.value) {
                                    restart()
                                } else {
                                    lifecycleScope.launch {
                                        delay(1000)
                                        val res = mainViewModel.getConnectedDevice()
                                        mainViewModel.receiveFlow.value =
                                            "activity判断手环状态 $res"
                                    }
                                }
                            }

                            BluetoothAdapter.STATE_TURNING_OFF -> {
                                // 蓝牙正在关闭
                                // ...
                                Toast.makeText(
                                    this@MainActivity, "蓝牙正在关闭", Toast.LENGTH_SHORT
                                ).show()


                            }
                        }
                    }
                }

                // 注册广播接收器
                registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "These permissions are denied: $deniedList",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun restart() {
        val launchIntent = packageManager.getLaunchIntentForPackage(application.packageName)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(launchIntent)
    }

    override fun onBackPressed() {
        if (fromIntent) {
            super.onBackPressed()
            return
        }
        if (navController.currentBackStackEntry?.destination?.route.equals("home")) {
            if (countDown !== 0L) {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(0)
            } else {
                Toast.makeText(this@MainActivity, "再次返回退出", Toast.LENGTH_SHORT).show()
                countDown = 2000L
                lifecycleScope.launch {
                    delay(2000)
                    countDown = 0
                }
            }
        } else {
            navController.popBackStack()

        }

    }

    @OptIn(ExperimentalFoundationApi::class)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @Composable
    fun Home(
        books: Flow<List<Book>>,
        toDetail: (Book) -> Unit = {},
        pickFile: () -> Unit = {},
        importBook: (result: String?) -> Unit = {},
    ) {
        val bookState = books.collectAsState(initial = emptyList())
        val grantedErrState = mainViewModel.grantedErrFlow.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        Scaffold(floatingActionButtonPosition = FabPosition.End, floatingActionButton = {
            if (false) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = Color.DarkGray, shape = RoundedCornerShape(32.dp)
                        )
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "",
                        Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "权限异常请手动关闭和开启蓝牙并重启软件",
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                val bandConnectedState = mainViewModel.bandConnected.collectAsState(initial = false)
                val bandAppInstalledState =
                    mainViewModel.bandAppInstalledFlow.collectAsState(initial = false)
                val hasGrantedState = mainViewModel.hasGrantedFlow.collectAsState(initial = false)
                Row(verticalAlignment = Alignment.Top) {
                    Title(
                        "书架", subStr = when {
                            (bandConnectedState.value && bandAppInstalledState.value) -> {
                                "手环APP已连接"
                            }

                            (bandConnectedState.value && !bandAppInstalledState.value) -> "手环已连接APP未安装"
                            (bandConnectedState.value && bandAppInstalledState.value && !hasGrantedState.value) -> "APP已安装但权限异常"
                            (!bandConnectedState.value) -> "手环未连接"
                            else -> ""
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // button with icon
                    ExtendedFloatingActionButton(
                        modifier = Modifier.height(40.dp),
                        onClick = { pickFile() },
                        containerColor = BtnColor,
                        icon = { Icon(Icons.Filled.Add, "Localized Description") },
                        text = { Text(text = "导入文件") },
                    )
                }
                Spacer(modifier = Modifier.padding(12.dp))
                ElevatedCard(
                    //flex1
                    //width 100%
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                    //border card
                ) {
                    //scroll state
                    val scrollState = rememberScrollState()
                    //list book

                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_empty))
                    var delDialogState by remember { mutableStateOf(false) }
                    val editDialogState by editDialogFlow.collectAsState(initial = false)
                    var curBook by remember { mutableStateOf<Book?>(null) }
                    var waitEdit by remember { mutableStateOf(false) }

                    if (bookState.value.isEmpty()) {
                        LottieAnimation(
                            composition,
                            modifier = Modifier.fillMaxSize(),
                            iterations = LottieConstants.IterateForever,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp, 14.dp)
                    ) {
                        if (delDialogState) {
                            AlertDialogExample(dialogTitle = "确认删除吗",
                                dialogSubText = "手表端请自行删除",
                                onConfirmation = {
                                    mainViewModel.delBook(curBook!!)
                                },
                                onDismissRequest = { },
                                finally = {
                                    delDialogState = false
                                    curBook = null
                                })
                        }
                        if (editDialogState) {
                            AlertDialogExample(dialogTitle = "修改信息",
                                dialogSubText = "将会同步至手环",
                                dialogText = curBook!!.name,
                                edit = true,
                                cover = true,
                                onConfirmation = {
                                    waitEdit = true
                                    if (it.isBlank()) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "请输入书名!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        waitEdit = false
                                    } else {
                                        mainViewModel.changeBook(
                                            curBook!!.copy(name = it),
                                            coverBitmapFlow.value
                                        )
                                    }
                                },
                                onDismissRequest = { },
                                finally = {
                                    editDialogFlow.value = false
                                    curBook = null
                                    coverBitmapFlow.value = null
                                }
                            )
                        }

                        bookState.value.forEach { book ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { toDetail(book) }, onLongClick = {
                                        if (book.id != curBook?.id) {
                                            curBook = book
                                        } else {
                                            curBook = null
                                        }
                                    }),
                                colors = CardDefaults.cardColors(
                                    containerColor = ItemColor,
                                ),
                            ) {
                                Row(
                                    //垂直居中
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(12.dp, 14.dp)
                                ) {
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = book.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 4.em,
                                        maxLines = 2,
                                        //超出省略号
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.padding(8.dp))
                                    Text("章节数: ${book.chapters}")
                                }
                                AnimatedVisibility(visible = book.id == curBook?.id) {
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp, 14.dp)
                                    ) {
                                        Text(
                                            text = "编辑",
                                            color = Blue80,
                                            modifier = Modifier.clickable(true) {
                                                editDialogFlow.value = true
                                            })
                                        Spacer(modifier = Modifier.padding(8.dp))
                                        Text(
                                            text = "删除",
                                            color = Blue80,
                                            modifier = Modifier.clickable(true) {
                                                delDialogState = true
                                            })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.padding(8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        val openDialogState = openDialog.collectAsState(initial = false)
        val logState = logDialog.collectAsState(initial = false)
        val filePathState = filePath.collectAsState(initial = "")
        val logFlowState = logFlow.collectAsState(initial = "")
        val application = LocalContext.current.applicationContext as MyApplication
        val isImportingState = mainViewModel.isImporting.collectAsState(initial = false)
        if (openDialogState.value) {
            AlertDialogExample(dialogTitle = "确认书名",
                dialogText = bookName(filePathState.value),
                onConfirmation = importBook,
                edit = true,
                wait = isImportingState.value,
                confirmText = if (isImportingState.value) "导入中" else "导入",
                onDismissRequest = {
                    openDialog.value = false
                })
        }
    }

    @OptIn(FlowPreview::class)
    @Composable
    fun DetailScreen(
        chapters: Flow<List<Chapter>>,
    ) {
        val chaptersState = chapters.collectAsState(initial = emptyList())
        val syncState = mainViewModel.syncStatus.collectAsState(initial = SyncStatus.SyncDef)
        val currentBookState = mainViewModel.currentBookFlow.collectAsState()
        val chapterLoadingState = mainViewModel.chapterLoading.collectAsState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            val isSyncing = mainViewModel.isSyncing.collectAsState(initial = false)
            //Refresh icon 旋转state
            var rotationAngle by remember { mutableFloatStateOf(0f) }
            val rotation = remember { Animatable(rotationAngle) }
            LaunchedEffect(key1 = syncState.value) {
                if (syncState.value.str == "同步中") {
                    // Infinite repeatable rotation when is playing
                    rotation.animateTo(
                        targetValue = rotationAngle + 360f, animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    ) {
                        rotationAngle = value
                    }
                }
            }
            val syncCount by mainViewModel.syncFlow.collectAsState()
            Row(verticalAlignment = Alignment.Top) {
                Title(
                    currentBookState.value!!.name,
                    subStr = "${syncCount.first}/${syncCount.second}",
                    modifier = Modifier.weight(1f),
                    subOffset = 8.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                // button with icon
                ExtendedFloatingActionButton(
                    modifier = Modifier.height(40.dp),
                    containerColor = BtnColor,
                    onClick = { mainViewModel.syncToBand(currentBookState.value!!.id) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Localized Description",
                            modifier = Modifier.rotate(rotationAngle)
                        )
                    },
                    text = {
                        Text(
                            text = syncState.value.str,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.padding(12.dp))

            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_load))
            //scroll state
            val listState = rememberLazyListState()
            // Remember a CoroutineScope to be able to launch
            val coroutineScope = rememberCoroutineScope()
            val avergeLengthState = mainViewModel.avergeLengthFlow.collectAsState()
            var toTop by remember { mutableStateOf(false) }
            var isCollecting by remember { mutableStateOf(false) }
            val rendered by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 1
                }
            }

            SideEffect {
                if (!toTop) {
                    coroutineScope.launch {
                        delay(200)
                        /*mainViewModel.receiveFlow.value =
                            "sideEffect time ${System.currentTimeMillis()}"*/

                        if (syncCount.first == syncCount.second) {
                            coroutineScope.launch {
                                listState.scrollToItem(syncCount.second + 1)
                            }
                        } else {
                            coroutineScope.launch {
                                val index = if (syncCount.first > 5) syncCount.first - 2 else 0
                                listState.scrollToItem(index)
                            }
                        }

                        if (!isCollecting) {
                            coroutineScope.launch {
                                mainViewModel.receiveFlow.value = "coroutineScope launch"
                                isCollecting = true
                                delay(850)
                                mainViewModel.chapterLoading.value = false
                                mainViewModel.syncFlow.collectLatest {
                                    val lastest = it.first
                                    if (lastest < it.second) listState.scrollToItem(if (lastest > 2) lastest - 2 else lastest)
                                    else listState.scrollToItem(lastest + 2)
                                }
                            }
                        }
                    }

                }
            }

            ElevatedCard(
                //flex1
                //width 100%
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                //border card
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {

                    // compose animation state for boolean

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 0.dp)

                    ) {
                        item {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        chaptersState.value.forEachIndexed { index, current ->
                            val longName = current.name.length > 20
                            item(contentType = longName) {
                                val chapter = chaptersState.value[index]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (longName) 88.dp else 62.dp)
                                        .padding(horizontal = 4.dp, 0.dp)
                                        .clickable {},
                                    colors = CardDefaults.cardColors(
                                        containerColor = ItemColor,
                                    ),
                                ) {
                                    Column(
                                        //垂直居中
                                        verticalArrangement = Arrangement.Top,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp, 12.dp)
                                        ) {
                                            Text(
                                                modifier = Modifier
                                                    .align(alignment = Alignment.CenterStart)
                                                    .fillMaxWidth(),
                                                text = chapter.name,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = if (longName) 2 else 1,
                                                //超出省略号
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )

                                            Icon(
                                                modifier = Modifier
                                                    .offset(
                                                        x = (10).dp, y = (-6).dp
                                                    )
                                                    .align(alignment = Alignment.TopEnd)
                                                    .size(18.dp),
                                                imageVector = Icons.Outlined.CheckCircle,
                                                tint = if (chapter.sync) Blue80 else Color.Transparent,
                                                contentDescription = ""
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        item {
                            Spacer(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth()
                                    .background(
                                        BtnGrayColor
                                    )
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = rendered && syncState.value !== SyncStatus.Syncing,
                        enter = slideInVertically(),
                        exit = slideOutVertically()
                    ) {
                        Text(
                            text = "回到顶部",
                            color = BlueGray80,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    color = BgColor,
                                    shape = RoundedCornerShape(32.dp)
                                )
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(1)
                                        toTop = true
                                        delay(2000)
                                        toTop = false
                                    }
                                },
                        )
                    }
                }

                if (currentBookState.value!!.chapters > 200) {
                    AnimatedVisibility(
                        chapterLoadingState.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        LottieAnimation(
                            composition,
                            modifier = Modifier
                                .fillMaxSize(),
                            iterations = LottieConstants.IterateForever,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    fun importBook(bookName: String, uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.receiveFlow.value = "importBook"
            if (mainViewModel.appDatabase.bookDao().getBookByName(bookName) != null) {
                mainViewModel.receiveFlow.value = "书名重复"
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(mainViewModel.appContext, "书名重复", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            mainViewModel.isImporting.value = true
            val book = Book(0, bookName, 0, 1, synced = false)
            val bookId = mainViewModel.appDatabase.bookDao().insert(book)
            book.id = bookId.toInt()
            try {
                val chapters =
                    readTxtFile(book, mainViewModel.appContext, uri, mainViewModel.receiveFlow)
                book.chapters = chapters.size
                book.pages = chapters.last().paging
                mainViewModel.appDatabase.bookDao().update(book)
                mainViewModel.appDatabase.chapterDao().insertAll(chapters)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        mainViewModel.appContext, "导入成功 快去同步吧~", Toast.LENGTH_LONG
                    ).show()
                }
                filePath.value = ""
                openDialog.value = false
                if (fromIntent) restart()
            } catch (e: Exception) {
                Log.i("TAG", "importBook: ${e.message}")
                mainViewModel.appDatabase.bookDao().delete(book)
            } finally {
                mainViewModel.isImporting.value = false
            }
        }
    }

    private fun readTxtFile(
        book: Book, context: Context, uri: Uri = Uri.EMPTY, receiveFlow: MutableStateFlow<String>
    ): List<Chapter> {
        receiveFlow.value = "readTxtFile"

        if (uri == Uri.EMPTY) {
            return emptyList()
        }
        receiveFlow.value = "readTxtFile uri:$uri"

        mainViewModel.receiveFlow.value = "uri:$uri"
        var inputStream = context.contentResolver.openInputStream(uri)
        val buf = ByteArray(4096)
        val detector = UniversalDetector()
        var nread: Int
        while (inputStream?.read(buf).also { nread = it!! } != -1 && !detector.isDone) {
            detector.handleData(buf, 0, nread)
        }
        detector.dataEnd()
        var encoding = detector.detectedCharset
        if (encoding != null) {
            mainViewModel.receiveFlow.value = "Detected encoding = $encoding"
        } else {
            mainViewModel.receiveFlow.value = "No encoding detected."
            mainViewModel.receiveFlow.value = "尝试 GB18030"
            encoding = "GB18030"
        }
        detector.reset()
        inputStream?.close()
        encoding?.let { encode ->
            inputStream = context.contentResolver.openInputStream(uri)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, encode))
            var chapters = mutableListOf(Pair("", ""))
            val regex = Regex(""".{0,10}第.{1,10}章.{0,30}""")
            mainViewModel.receiveFlow.value = "开始读取"
            var counter = 0
            var counter2 = 0
            var temp = Pair("开始", "")
            var lines = mutableListOf<String>()
            val tempStr: StringBuilder = StringBuilder()
            //循环读取bufferedReader char 遇到换行就往lines里面加
            while (bufferedReader.ready()) {
                val char = bufferedReader.read().toChar()
                if (char == '\n' || char == '\r') {
                    lines.add(tempStr.toString())
                    tempStr.clear()
                } else {
                    // '\x00' continue
                    if (char == '\u0000') continue
                    /*if (tempStr.length > 10000) {
                        lines.add(tempStr.toString())
                        tempStr.clear()
                    }*/
                    tempStr.append(char)
                }
            }
            lines.add(tempStr.toString())
            val matches = lines.count { regex.matches(it) }
            lines = lines.filter { it.isNotBlank() }.toMutableList()
            if (lines.size<20 || (matches < 10)) {
                val tempLines = mutableListOf<String>()
                lines.chunked(50).forEachIndexed { idx, it ->
                    chapters.add(Pair("第${idx + 1}部分", it.joinToString("\n")))
                }
            } else {
                lines.forEach {
                    counter++
                    temp = if (regex.matches(it)) {
                        counter2++
                        if (temp.first.isNotEmpty()) {
                            var formatTitle = temp.first
                            formatTitle = formatTitle.trim()
                            formatTitle = formatTitle.replace("=", "")
                            temp = temp.copy(first = formatTitle)
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
            }
            mainViewModel.receiveFlow.value = "读取完毕"
            inputStream?.close()
            chapters = chapters.filter { it.second.length > 50 }.toMutableList()
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

        } ?: return emptyList<Chapter>().also {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(mainViewModel.appContext, "不支持的编码格式", Toast.LENGTH_SHORT)
                    .show()
                //触发返回事件
                onBackPressed()
                delay(1000)
                onBackPressed()
            }
        }
    }

}

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit = {},
    onConfirmation: (result: String) -> Unit = {},
    finally: () -> Unit = {},
    dialogTitle: String,
    dialogText: String = "",
    cover: Boolean = false,
    edit: Boolean = false,
    wait: Boolean = false,
    dialogSubText: String? = null,
    confirmText: String = "确认",
    cancelText: String = "取消"
) {
    val bookNameState = remember { mutableStateOf(dialogText) }
    val coverState = coverBitmapFlow.collectAsState()
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_photo))
    //localcontext
    val context = LocalContext.current
    AlertDialog(title = {
        Text(text = dialogTitle)
    }, text = {
        Column(
            modifier = Modifier
                .offset(y = (-10).dp)
        ) {
            dialogSubText?.let {
                Text(text = dialogSubText)
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (edit) {
                OutlinedTextField(
                    value = bookNameState.value,
                    textStyle = TextStyle.Default.copy(color = Color.White),
                    onValueChange = { bookNameState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 4.dp)
                )
            }
            if (cover) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clickable {
                        PermissionX
                            .init(context as FragmentActivity)
                            .permissions(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(
                                    Manifest.permission.READ_MEDIA_VIDEO,
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                )
                                else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            )
                            .request { allGranted, _, deniedList ->
                                if (allGranted) {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    //type picture
                                    intent.type = "image/*"
                                    (context as MainActivity).pickFileLauncher.launch(intent)
                                }
                            }
                    }
                ) {
                    coverState.value?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(0.dp, 4.dp)
                        )
                    } ?: Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                color = FillBtnColor, shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        /*LottieAnimation(
                            composition,
                            modifier = Modifier.size(80.dp),
                            iterations = LottieConstants.IterateForever,
                        )*/
                        Text("点击此处选择封面(可选)")
                    }
                }
            }

        }
    }, onDismissRequest = {
        onDismissRequest()
    }, confirmButton = {
        TextButton(onClick = {
            onConfirmation(bookNameState.value)
            finally()
        }) {
            if (wait) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    trackColor = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(confirmText, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }, dismissButton = {
        TextButton(onClick = {
            onDismissRequest()
            finally()
        }) {
            Text(cancelText)
        }
    })
}

fun bookName(filePath: String): String {
    if (filePath.isEmpty()) {
        return ""
    }
    val splitList = filePath.split("/")
    val bookName = splitList[splitList.size - 1]
    return bookName.split(".")[0]
}