package io.github.takusan23.kotoricore

import io.github.takusan23.kotoricore.data.AudioEncoderData
import io.github.takusan23.kotoricore.data.VideoEncoderData
import io.github.takusan23.kotoricore.data.VideoFileInterface
import io.github.takusan23.kotoricore.processor.AudioProcessor
import io.github.takusan23.kotoricore.processor.VideoProcessor
import io.github.takusan23.kotoricore.tool.MediaMuxerTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * OpenGLで映像を加工する
 *
 * @param videoFileData 動画ファイルについて
 * @param videoEncoderData 映像エンコーダーについて
 * @param audioEncoderData 音声エンコーダーについて
 */
class KotoriCore(
    private val videoFileData: VideoFileInterface,
    videoEncoderData: VideoEncoderData,
    audioEncoderData: AudioEncoderData,
) {

    /** 音声エンコーダー */
    private val audioProcessor by lazy {
        AudioProcessor(
            videoFile = videoFileData.videoFile,
            resultFile = videoFileData.encodedAudioFile,
            audioCodec = audioEncoderData.codecName,
            bitRate = audioEncoderData.bitRate
        )
    }

    /** フィルターをかけられる 映像エンコーダー */
    private val videoProcessor by lazy {
        VideoProcessor(
            videoFile = videoFileData.videoFile,
            resultFile = videoFileData.encodedVideoFile,
            bitRate = videoEncoderData.bitRate,
            frameRate = videoEncoderData.frameRate,
            videoCodec = videoEncoderData.codecName,
            videoWidth = videoEncoderData.width,
            videoHeight = videoEncoderData.height
        )
    }

    /** 処理を始める */
    suspend fun start() = withContext(Dispatchers.Default) {
        videoFileData.prepare()
        val videoTask = async { videoProcessor.start() }
        val audioTask = async { audioProcessor.start() }
        // 終わるまで待つ
        videoTask.await()
        audioTask.await()
        // 音声と映像をコンテナフォーマットへ
        MediaMuxerTool.mixed(
            resultFile = videoFileData.outputFile,
            containerFormat = videoFileData.format,
            mergeFileList = listOf(videoFileData.encodedAudioFile, videoFileData.encodedVideoFile)
        )
        // 一時ファイルを消して完成
        videoFileData.destroy()
    }

    suspend fun stop() {
        audioProcessor.stop()
        videoProcessor.stop()
        videoFileData.destroy()
    }

}