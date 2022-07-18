package io.github.takusan23.kotoricore.data

import java.io.File

/**
 * File でも Uri でも対応できるようにしたもの
 *
 * @param tempWorkFolder 一時保存先で利用できる保存先を
 */
interface VideoFileInterface {

    /** 動画の元データ */
    val videoFile: File

    /** 一時保存先 */
    val tempWorkFolder: File

    /** エンコードした音声の保存先 */
    val encodedAudioFile: File
        get() = File(tempWorkFolder, TEMP_AUDIO_FILE).apply { createNewFile() }

    /** エンコードした動画の保存先 */
    val encodedVideoFile: File
        get() = File(tempWorkFolder, TEMP_VIDEO_FILE).apply { createNewFile() }

    /** フォーマット [android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4] など  */
    val format: Int

    /** エンコードされた動画の置き場 */
    val outputFile: File

    /** エンコーダー開始前に呼ばれる */
    suspend fun prepare() {
        // do nothing
    }

    /** 終了時に呼ばれる */
    suspend fun destroy() {
        // do nothing
    }

    companion object {
        /** 一時映像ファイル保存先 */
        const val TEMP_VIDEO_FILE = "temp_video_file.mp4"

        /** 一時音声ファイル保存先、一旦すべてのファイルをデコードして一つのファイルにする */
        const val TEMP_RAW_AUDIO_FILE = "temp_raw_audio_file.aac"

        /** 一時音声ファイル保存先 */
        const val TEMP_AUDIO_FILE = "temp_audio_file.aac"
    }
}