package com.example.bandReader

import android.R
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.bandReader.ui.theme.BandReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


@AndroidEntryPoint
class IntentActivity : ComponentActivity() {
    val mainViewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.receiveFlow.value = "intent.action: ${ intent.action}"

        // 处理来自其他应用的打开请求
        if (intent.action == Intent.ACTION_VIEW) {
            // 获取 txt 文件的路径
            val uri = intent.data
            if (uri != null) {
                // 使用 ContentResolver 打开输入流
                try {
                    contentResolver.openInputStream(uri).use { inputStream ->
                        // 读取输入流中的文本内容
                        mainViewModel.bufferedReaderFlow.value =
                            BufferedReader(InputStreamReader(inputStream))
                        /*val sb = StringBuilder()
                        var line: String?
                        while (mainViewModel.bufferedReaderFlow.value!!.readLine().also { line = it } != null) {
                            sb.append(line).append("\n")
                        }
                        // 显示或处理文本内容
                        mainViewModel.receiveFlow.value = "已经解析文本 长度 ${sb.length}"*/
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    mainViewModel.receiveFlow.value = "解析文本失败"

                }
            }
            mainViewModel.shareUri.value = uri
            //重定向到MainActivity.kt 并将uri和path传递
            val intent = Intent(this, MainActivity::class.java)
            mainViewModel.receiveFlow.value = "IntenntActivity: ${uri.toString()}"
            intent.putExtra("share", true)
            intent.putExtra("uri", uri.toString())
            uri?.let {
                intent.putExtra("path", uri.path)
            }
            startActivity(intent)
            finish()
        }

        setContent {

        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BandReaderTheme {
        Greeting("Android")
    }
}