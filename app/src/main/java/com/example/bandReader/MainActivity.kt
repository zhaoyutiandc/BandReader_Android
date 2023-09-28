package com.example.bandReader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.example.bandReader.ui.theme.BtnColor
import com.example.bandReader.ui.theme.ItemColor
import com.example.bandReader.util.FileUtils
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

val openDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)
val filePath: MutableStateFlow<String> = MutableStateFlow("")
val showFlow: MutableStateFlow<String> = MutableStateFlow("")
val logFlow: MutableStateFlow<Any> = MutableStateFlow("")
val logDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val mainViewModel by viewModels<MainViewModel>()
    private lateinit var navController: NavController
    private val uri: MutableStateFlow<Uri> = MutableStateFlow(Uri.EMPTY)
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var countDown = 0L

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            //clear list
            uri.value = Uri.EMPTY
            if (result.data?.data != null) {
                result.data?.data?.let {
                    uri.value = it
                    val path =
                        FileUtils.getRealPath(this@MainActivity, it) ?: "无路径"
                    filePath.value = path
                    if (!filePath.value.endsWith(".txt")) {
                        Toast.makeText(this@MainActivity, "不支持的文件格式", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        openDialog.value = true
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initBlue()

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
                    val uriState = uri.collectAsState(initial = Uri.EMPTY)
                    val receiveState = mainViewModel.receiveFlow.collectAsState()
                    Column {
                        if (false) {
                            Text(text = receiveState.value, modifier = Modifier.height(300.dp))
                        }
                        NavHost(
                            navController = navController as NavHostController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                Home(mainViewModel.books, toDetail = { book ->
                                    mainViewModel.currentBookFlow.value = book
                                    navController.navigate("detail")
                                    mainViewModel.reqBookInfo()
                                    mainViewModel.getChapters(book.id)
                                }, pickFile = {
                                    PermissionX.init(this@MainActivity as FragmentActivity)
                                        .permissions(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                                listOf(
                                                    Manifest.permission.READ_MEDIA_VIDEO,
                                                    Manifest.permission.READ_MEDIA_AUDIO,
                                                    Manifest.permission.READ_MEDIA_IMAGES,
                                                )
                                            else
                                                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        )
                                        .request { allGranted, _, deniedList ->
                                            if (allGranted) {
                                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                intent.type = "*/*"
                                                pickFileLauncher.launch(intent)
                                            }
                                        }

                                }, importBook = { input ->
                                    input?.let {
                                        if (input.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                "请输入书名!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@let
                                        }
                                        mainViewModel.importBook(
                                            bookName(input.trim()),
                                            uriState.value
                                        )
                                    }
                                })
                            }
                            composable("detail") {
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
            mainViewModel.messageApi?.removeListener(mainViewModel.curNode!!.id)
                ?.addOnSuccessListener { mainViewModel.receiveFlow.value =  "listener 卸载" }
                ?.addOnFailureListener {
                    mainViewModel.receiveFlow.value = "removeListener fail ${it.stackTraceToString()}"
                }
        }
        super.onDestroy()
    }

    private fun initBlue() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        PermissionX.init(this@MainActivity as FragmentActivity)
            .permissions(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    Manifest.permission.BLUETOOTH_CONNECT
                else
                    Manifest.permission.BLUETOOTH
            )
            .request { allGranted, _, deniedList ->
                if (allGranted) {
                    // 注册蓝牙状态改变的广播接收器
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            // 获取蓝牙状态
                            val state =
                                intent.getIntExtra(
                                    BluetoothAdapter.EXTRA_STATE,
                                    BluetoothAdapter.STATE_OFF
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
                                        this@MainActivity,
                                        "蓝牙正在开启",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (mainViewModel.grantedErrFlow.value) {
                                        restart()
                                    } else {
                                        lifecycleScope.launch {
                                            delay(1000)
                                            val res = mainViewModel.getConnectedDevice()
                                            mainViewModel.receiveFlow.value = "activity判断手环状态 $res"
                                        }
                                    }
                                }

                                BluetoothAdapter.STATE_TURNING_OFF -> {
                                    // 蓝牙正在关闭
                                    // ...
                                    Toast.makeText(
                                        this@MainActivity,
                                        "蓝牙正在关闭",
                                        Toast.LENGTH_SHORT
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
        Handler(Looper.getMainLooper()).postDelayed({
            val launchIntent =
                packageManager.getLaunchIntentForPackage(application.packageName)
            launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        }, 1000)
    }

    override fun onBackPressed() {
//        super.onBackPressed()
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
        Scaffold(
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (grantedErrState.value) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = Color.DarkGray,
                                shape = RoundedCornerShape(32.dp)
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
                            text = "权限异常请手动关闭和开启蓝牙",
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                val bandConnectedState = mainViewModel.bandConnected.collectAsState(initial = false)
                val bandAppInstalledState = mainViewModel.bandAppInstalledFlow.collectAsState(initial = false)
                val hasGrantedState = mainViewModel.hasGrantedFlow.collectAsState(initial = false)
                Row(verticalAlignment = Alignment.Top) {
                    Title(
                        "书架1",
                        subStr = when{
                            (bandConnectedState.value && bandAppInstalledState.value) -> "手环APP已连接"
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
                Spacer(modifier = Modifier.padding(16.dp))
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
                    var curBook by remember { mutableStateOf<Book?>(null) }

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
                            .padding(8.dp)
                    ) {
                        if (delDialogState) {
                            AlertDialogExample(
                                dialogTitle = "确认删除吗",
                                dialogSubText = "手表端请自行删除",
                                onConfirmation = {
                                    mainViewModel.delBook(curBook!!)
                                    delDialogState = false
                                },
                                onDismissRequest = { delDialogState = false }
                            )

                        }

                        bookState.value.forEach { book ->
                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .combinedClickable(
                                    onClick = { toDetail(book) },
                                    onLongClick = {
                                        delDialogState = true
                                        curBook = book
                                    }
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = ItemColor,
                                ),
                            ) {
                                Row(
                                    //垂直居中
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .padding(12.dp, 16.dp)
                                            .weight(1f),
                                        text = book.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 4.em,
                                        maxLines = 1,
                                        //超出省略号
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.padding(16.dp))
                                    Text("章节数: ${book.chapters}")
                                    Spacer(modifier = Modifier.padding(12.dp))
                                }
                            }
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
        if (openDialogState.value) {
            AlertDialogExample(
                dialogTitle = "确认书名",
                dialogText = bookName(filePathState.value),
                onConfirmation = importBook,
                onDismissRequest = {
                    openDialog.value = false
                }
            )
        }
        if (logState.value) {
            AlertDialogExample(
                dialogTitle = "log",
                dialogText = logFlowState.value.toString(),
                onConfirmation = {
//                    application.clearErrLog()
                    logDialog.value = false
                },
                onDismissRequest = {
                    logDialog.value = false
                }
            )
        }
    }

    @Composable
    fun DetailScreen(
        chapters: Flow<List<Chapter>>,
    ) {
        val chaptersState = chapters.collectAsState(initial = null)
        val syncState = mainViewModel.syncStatus.collectAsState(initial = SyncStatus.SyncDef)
        val currentBookState = mainViewModel.currentBookFlow.collectAsState()
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
                        targetValue = rotationAngle + 360f,
                        animationSpec = infiniteRepeatable(
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
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
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
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.padding(16.dp))
            ElevatedCard(
                //flex1
                //width 100%
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                //border card
            ) {
                //scroll state
                rememberScrollState()
                //list book
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)

                ) {
                    items(chaptersState.value?.size ?: 0) { index ->
                        val chapter = chaptersState.value?.get(index)
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, 8.dp)
                            .clickable {},
                            colors = CardDefaults.cardColors(
                                containerColor = ItemColor,
                            ),
                        ) {
                            Row(
                                //垂直居中
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(16.dp, 8.dp)
                                        .weight(1f),
                                    text = chapter?.name ?: "无名称",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    //超出省略号
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.padding(16.dp))
                                Text(if (chapter?.sync == true) "已同步" else "未同步")
                                Spacer(modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit = {},
    onConfirmation: (result: String?) -> Unit = {},
    dialogTitle: String,
    dialogText: String? = null,
    dialogSubText: String? = null,
    confirmText: String = "确认",
    cancelText: String = "取消"
) {
    val bookNameState = remember { mutableStateOf(dialogText) }
    AlertDialog(
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Column {
                dialogText?.let {
                    OutlinedTextField(
                        value = bookNameState.value!!,
                        onValueChange = { bookNameState.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                dialogSubText?.let {
                    Text(text = dialogSubText)
                }
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation(bookNameState.value)
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(cancelText)
            }
        }
    )
}

fun bookName(filePath: String): String {
    if (filePath.isEmpty()) {
        return ""
    }
    val splitList = filePath.split("/")
    val bookName = splitList[splitList.size - 1]
    return bookName.split(".")[0]
}