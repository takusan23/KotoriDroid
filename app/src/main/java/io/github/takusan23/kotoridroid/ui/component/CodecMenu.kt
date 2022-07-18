package io.github.takusan23.kotoridroid.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.takusan23.kotoridroid.R
import io.github.takusan23.kotoridroid.tool.EncoderCodecTypes

/**
 * 動画コーデック選択のやつ
 */
@ExperimentalMaterial3Api
@Composable
fun CodecMenu(
    modifier: Modifier = Modifier,
    value: EncoderCodecTypes,
    onValueChange: (EncoderCodecTypes) -> Unit,
) {
    val isOpen = remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedButton(
            modifier = modifier.width(300.dp),
            onClick = { isOpen.value = true }
        ) {
            Text(text = value.name)
            Spacer(modifier = Modifier.weight(1f))
            Icon(painter = painterResource(id = R.drawable.ic_outline_expand_more_24), contentDescription = null)
        }
        DropdownMenu(
            expanded = isOpen.value,
            onDismissRequest = { isOpen.value = false }
        ) {
            EncoderCodecTypes.values().forEach { codec ->
                DropdownMenuItem(
                    text = { Text(text = codec.name) },
                    onClick = {
                        onValueChange(codec)
                        isOpen.value = false
                    }
                )
            }
        }
    }

}