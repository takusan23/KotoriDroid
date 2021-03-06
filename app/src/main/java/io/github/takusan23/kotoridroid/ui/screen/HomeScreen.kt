package io.github.takusan23.kotoridroid.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.takusan23.kotoricore.gl.FragmentShaderTypes
import io.github.takusan23.kotoridroid.VideoProcessWork
import io.github.takusan23.kotoridroid.tool.DisplayConverter
import io.github.takusan23.kotoridroid.tool.EncoderCodecTypes
import io.github.takusan23.kotoridroid.ui.component.CodecMenu
import io.github.takusan23.kotoridroid.ui.component.FilterMenu

/**
 * 最初に表示する画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val selectUri = remember { mutableStateOf<Uri?>(null) }
    val isExistsRunningTask = remember { VideoProcessWork.existsRunningTask(context) }.collectAsState(initial = null)
    val selectCodec = remember { mutableStateOf(EncoderCodecTypes.H264_AAC_MP4) }
    val selectFilter = remember { mutableStateOf(FragmentShaderTypes.DEFAULT) }
    val fileName = remember { mutableStateOf("") }
    val videoBitrate = remember { mutableStateOf(VideoProcessWork.DEFAULT_VIDEO_BITRATE.toString()) }

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
        CodecMenu(
            value = selectCodec.value,
            onValueChange = { selectCodec.value = it }
        )
        FilterMenu(
            value = selectFilter.value,
            onValueChange = { selectFilter.value = it }
        )
        OutlinedTextField(
            value = fileName.value,
            onValueChange = { fileName.value = it },
            label = { Text(text = "エンコード後のファイル名") },
            maxLines = 1
        )
        OutlinedTextField(
            value = videoBitrate.value,
            onValueChange = { videoBitrate.value = it },
            label = { Text(text = "動画のビットレート (省略可能)") },
            maxLines = 1
        )
        Text(text = DisplayConverter.convert(videoBitrate.value.toIntOrNull() ?: 0))

        Button(onClick = {
            videoPicker.launch("video/*")
        }) { Text(text = "動画を選ぶ") }

        Button(onClick = {
            VideoProcessWork.startWork(
                context = context,
                videoUri = selectUri.value!!,
                resultFileName = fileName.value,
                codecTypes = selectCodec.value,
                filter = selectFilter.value,
                videoBitrate = videoBitrate.value.toIntOrNull() ?: VideoProcessWork.DEFAULT_VIDEO_BITRATE
            )
        }) { Text(text = "処理を始める") }
    }
}