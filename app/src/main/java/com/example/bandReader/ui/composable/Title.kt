package com.example.bandReader.ui.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bandReader.printState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Title(
    str: String,
    subStr: String? = null,
    subOffset: Dp = 0.dp,
    click: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Column(
        modifier.combinedClickable(
            onClick = click,
            onLongClick = { printState.value = !printState.value }
        )
    ) {
        Text(
            text = str,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            lineHeight = 26.sp,
            modifier = Modifier.basicMarquee()
        )
        subStr?.let {
            Spacer(modifier = Modifier.padding(1.dp))
            Text(
                text = it,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp).padding(start = subOffset)
            )
        }
    }
}