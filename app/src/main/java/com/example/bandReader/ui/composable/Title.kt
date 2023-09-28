package com.example.bandReader.ui.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Title(str:String, subStr:String? = null, @SuppressLint("ModifierParameter") modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = str,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        subStr?.let {
            Text(
                text = it,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}