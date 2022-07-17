package io.github.takusan23.kotoridroid.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * 最初に表示する画面
 */
@Composable
fun HomeScreen() {
    val selectUri = remember { mutableStateOf<Uri?>(null) }

    val videoPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent(), onResult = { uri ->
        selectUri.value = uri
    })

    Column {
        Text(text = "OpenGLで動画にエフェクトをつけるマン")
        if (selectUri.value != null) {
            Text(text = selectUri.value.toString())
        }

        Button(onClick = {
            videoPicker.launch("video/*")
        }) { Text(text = "動画を選ぶ") }

        Button(onClick = {

        }) { Text(text = "処理を始める") }
    }
}