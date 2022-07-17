package io.github.takusan23.kotoridroid.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.takusan23.kotoridroid.VideoProcessWork

/**
 * 最初に表示する画面
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val selectUri = remember { mutableStateOf<Uri?>(null) }
    val isExistsRunningTask = remember { VideoProcessWork.existsRunningTask(context) }.collectAsState(initial = null)

    val videoPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent(), onResult = { uri ->
        selectUri.value = uri
    })

    Column {
        Text(text = "OpenGLで動画にエフェクトをつけるマン")
        Text(text = selectUri.value?.toString() ?: "動画が未選択です")
        Text(text = if (isExistsRunningTask.value != null) {
            "実行中タスクがあります"
        } else {
            "実行中タスクはありません"
        })

        Button(onClick = {
            videoPicker.launch("video/*")
        }) { Text(text = "動画を選ぶ") }

        Button(onClick = {
            VideoProcessWork.startWork(context, selectUri.value!!)
        }) { Text(text = "処理を始める") }
    }
}