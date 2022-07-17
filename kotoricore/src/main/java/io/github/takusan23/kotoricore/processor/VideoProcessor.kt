package io.github.takusan23.kotoricore.processor

import android.media.*
import io.github.takusan23.kotoricore.gl.CodecInputSurface
import io.github.takusan23.kotoricore.tool.MediaExtractorTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OpenGLを利用して動画へフィルターをかける
 *
 * 動画の解像度とかを変えられます...
 *
 * @param videoFile フィルターをかけたい動画ファイル
 * @param resultFile エンコードしたファイルの保存先
 * @param bitRate ビットレート。何故か取れなかった
 * @param frameRate フレームレート。何故か取れなかった
 * @param videoCodec エンコード後の動画コーデック [MediaFormat.MIMETYPE_VIDEO_AVC] など
 * @param videoHeight 動画の高さを変える場合は変えられます。16の倍数であることが必須です
 * @param videoWidth 動画の幅を変える場合は変えられます。16の倍数であることが必須です
 * */
class VideoProcessor(
    private val videoFile: File,
    private val resultFile: File,
    private val bitRate: Int? = null,
    private val frameRate: Int? = null,
    private val videoCodec: String? = null,
    private val videoWidth: Int? = null,
    private val videoHeight: Int? = null,
) {
    /** データを取り出すやつ */
    private var currentMediaExtractor: MediaExtractor? = null

    /** エンコード用 [MediaCodec] */
    private var encodeMediaCodec: MediaCodec? = null

    /** デコード用 [MediaCodec] */
    private var decodeMediaCodec: MediaCodec? = null

    /** コンテナフォーマットへ格納するやつ */
    private val mediaMuxer by lazy { MediaMuxer(resultFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }

    /** OpenGL */
    private var codecInputSurface: CodecInputSurface? = null

    /** 処理を始める、終わるまで一時停止します */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun start() = withContext(Dispatchers.Default) {

        // 動画の情報を読み出す
        val (mediaExtractor, index, format) = MediaExtractorTool.extractMedia(videoFile.path, MediaExtractorTool.ExtractMimeType.EXTRACT_MIME_VIDEO) ?: return@withContext
        currentMediaExtractor = mediaExtractor
        // 音声のトラックを選択
        mediaExtractor.selectTrack(index)
        mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        // 解析結果から各パラメータを取り出す
        val decodeMimeType = format.getString(MediaFormat.KEY_MIME)!!
        val encoderMimeType = videoCodec ?: decodeMimeType
        val width = videoWidth ?: format.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoHeight ?: format.getInteger(MediaFormat.KEY_HEIGHT)
        val bitRate = bitRate ?: 1_000_000
        val frameRate = frameRate ?: 30

        var videoTrackIndex = UNDEFINED_TRACK_INDEX

        // エンコード用（生データ -> H.264）MediaCodec
        encodeMediaCodec = MediaCodec.createEncoderByType(encoderMimeType).apply {
            // エンコーダーにセットするMediaFormat
            // コーデックが指定されていればそっちを使う
            val videoMediaFormat = MediaFormat.createVideoFormat(encoderMimeType, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_BUFFER_SIZE)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        // エンコーダーのSurfaceを取得して、OpenGLで加工します
        codecInputSurface = CodecInputSurface(encodeMediaCodec!!.createInputSurface())
        codecInputSurface?.makeCurrent()
        encodeMediaCodec!!.start()

        // デコード用（H.264 -> 生データ）MediaCodec
        codecInputSurface?.createRender()
        decodeMediaCodec = MediaCodec.createDecoderByType(decodeMimeType).apply {
            configure(format, codecInputSurface!!.surface, null, 0)
        }
        decodeMediaCodec?.start()

        // nonNull
        val decodeMediaCodec = decodeMediaCodec!!
        val encodeMediaCodec = encodeMediaCodec!!

        // メタデータ格納用
        val bufferInfo = MediaCodec.BufferInfo()

        var outputDone = false
        var inputDone = false

        while (!outputDone) {
            if (!inputDone) {

                val inputBufferId = decodeMediaCodec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferId >= 0) {
                    val inputBuffer = decodeMediaCodec.getInputBuffer(inputBufferId)!!
                    val size = mediaExtractor.readSampleData(inputBuffer, 0)
                    if (size > 0) {
                        // デコーダーへ流す
                        // 今までの動画の分の再生位置を足しておく
                        decodeMediaCodec.queueInputBuffer(inputBufferId, 0, size, mediaExtractor.sampleTime, 0)
                        mediaExtractor.advance()
                    } else {
                        // 終了
                        decodeMediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        // 開放
                        mediaExtractor.release()
                        // 終了
                        inputDone = true
                    }
                }
            }
            var decoderOutputAvailable = true
            while (decoderOutputAvailable) {
                // Surface経由でデータを貰って保存する
                val encoderStatus = encodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (encoderStatus >= 0) {
                    val encodedData = encodeMediaCodec.getOutputBuffer(encoderStatus)!!
                    if (bufferInfo.size > 1) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        } else if (videoTrackIndex == UNDEFINED_TRACK_INDEX) {
                            // MediaMuxerへ映像トラックを追加するのはこのタイミングで行う
                            // このタイミングでやると固有のパラメーターがセットされたMediaFormatが手に入る(csd-0 とか)
                            // 映像がぶっ壊れている場合（緑で塗りつぶされてるとか）は多分このあたりが怪しい
                            val newFormat = encodeMediaCodec.outputFormat
                            videoTrackIndex = mediaMuxer.addTrack(newFormat)
                            mediaMuxer.start()
                        }
                    }
                    outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encodeMediaCodec.releaseOutputBuffer(encoderStatus, false)
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue
                }
                // Surfaceへレンダリングする。そしてOpenGLでゴニョゴニョする
                val outputBufferId = decodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    decoderOutputAvailable = false
                } else if (outputBufferId >= 0) {
                    val doRender = bufferInfo.size != 0
                    decodeMediaCodec.releaseOutputBuffer(outputBufferId, doRender)
                    if (doRender) {
                        var errorWait = false
                        try {
                            codecInputSurface?.awaitNewImage()
                        } catch (e: Exception) {
                            errorWait = true
                        }
                        if (!errorWait) {
                            codecInputSurface?.drawImage()
                            codecInputSurface?.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                            codecInputSurface?.swapBuffers()
                        }
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderOutputAvailable = false
                        encodeMediaCodec.signalEndOfInputStream()
                    }
                }
            }
        }

        // デコーダー終了
        decodeMediaCodec.stop()
        decodeMediaCodec.release()
        // OpenGL開放
        codecInputSurface?.release()
        // エンコーダー終了
        encodeMediaCodec.stop()
        encodeMediaCodec.release()
        // MediaMuxerも終了
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    /** 強制終了時に呼ぶ */
    fun stop() {
        decodeMediaCodec?.stop()
        decodeMediaCodec?.release()
        codecInputSurface?.release()
        encodeMediaCodec?.stop()
        encodeMediaCodec?.release()
        currentMediaExtractor?.release()
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    companion object {
        /** タイムアウト */
        private const val TIMEOUT_US = 10000L

        /** MediaCodecでもらえるInputBufferのサイズ */
        private const val INPUT_BUFFER_SIZE = 655360

        /** トラック番号が空の場合 */
        private const val UNDEFINED_TRACK_INDEX = -1
    }

}