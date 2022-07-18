package io.github.takusan23.kotoridroid

import android.content.Context
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.work.*
import io.github.takusan23.kotoricore.KotoriCore
import io.github.takusan23.kotoricore.data.AudioEncoderData
import io.github.takusan23.kotoricore.data.VideoEncoderData
import io.github.takusan23.kotoricore.data.VideoUriData
import io.github.takusan23.kotoricore.gl.FragmentShaders
import io.github.takusan23.kotoridroid.tool.EncoderCodecTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 動画の加工、エンコードをするWorkManager
 */
class VideoProcessWork(private val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val notificationManager = NotificationManagerCompat.from(appContext)

    /** 動画ライブラリ */
    private var kotoriCore: KotoriCore? = null

    override suspend fun doWork(): Result {
        try {
            // 長期間タスクですよ...
            setForeground(createForegroundInfo())
            // 作業開始
            withContext(Dispatchers.Default) {
                startVideoProcess()
            }
            return Result.success()
        } catch (e: Exception) {
            // WorkManagerがキャンセルになると、CancellationException が投げられるのでキャッチする
            e.printStackTrace()
            kotoriCore?.stop()
            return Result.failure()
        }
    }

    /** 動画の加工、エンコードを始める */
    private suspend fun startVideoProcess() {
        val uri = inputData.getString(VIDEO_URI)!!.toUri()
        val encodeCodecType = inputData.getInt(ENCODER_CODEC_TYPE_INDEX, 0)
            .let { index -> EncoderCodecTypes.values()[index] }
        val fileName = inputData.getString(RESULT_FILE_NAME)?.ifEmpty { null } ?: System.currentTimeMillis().toString()
        val fileExtension = when (encodeCodecType) {
            EncoderCodecTypes.VP9_OPUS_WEBM -> "webm"
            else -> "mp4"
        }

        // 適当にエンコーダー設定
        val tempFolder = File(appContext.getExternalFilesDir(null), "temp").apply { mkdir() }
        val videoFile = VideoUriData(
            context = appContext,
            videoUri = uri,
            resultName = "$fileName.$fileExtension",
            tempWorkFolder = tempFolder,
            containerFormat = when (encodeCodecType) {
                EncoderCodecTypes.VP9_OPUS_WEBM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }
        )
        val videoEncoder = VideoEncoderData(
            fragmentShaders = FragmentShaders.DEFAULT,
            codecName = when (encodeCodecType) {
                EncoderCodecTypes.H264_AAC_MP4 -> MediaFormat.MIMETYPE_VIDEO_AVC
                EncoderCodecTypes.H265_AAC_MP4 -> MediaFormat.MIMETYPE_VIDEO_HEVC
                EncoderCodecTypes.VP9_OPUS_WEBM -> MediaFormat.MIMETYPE_VIDEO_VP9
            }
        )
        val audioEncoder = AudioEncoderData(
            codecName = when (encodeCodecType) {
                EncoderCodecTypes.H264_AAC_MP4 -> MediaFormat.MIMETYPE_AUDIO_AAC
                EncoderCodecTypes.H265_AAC_MP4 -> MediaFormat.MIMETYPE_AUDIO_AAC
                EncoderCodecTypes.VP9_OPUS_WEBM -> MediaFormat.MIMETYPE_AUDIO_OPUS
            }
        )
        kotoriCore = KotoriCore(videoFile, videoEncoder, audioEncoder)
        // 開始する
        kotoriCore?.start()
    }

    /** フォアグラウンドサービスで実行させるための情報 */
    private fun createForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW).apply {
            setName("動画の加工、エンコード中通知")
        }.build()
        // 通知ちゃんねるがない場合は作成
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle("動画を加工、エンコード中です")
            setContentText("しばらくお待ち下さい")
            setSmallIcon(R.drawable.ic_outline_memory_24)
            // キャンセル用PendingIntent
            addAction(R.drawable.ic_outline_clear_24, "強制終了", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
        }.build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {

        /**
         * [VideoProcessWork]を開始する
         *
         * @param context [Context]
         * @param videoUri 動画Uri
         * @param resultFileName ファイル名
         * @param codecTypes コーデック
         */
        fun startWork(context: Context, videoUri: Uri, resultFileName: String, codecTypes: EncoderCodecTypes) {
            val videoMergeWork = OneTimeWorkRequestBuilder<VideoProcessWork>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORKER_TAG)
                .setInputData(workDataOf(
                    VIDEO_URI to videoUri.toString(),
                    ENCODER_CODEC_TYPE_INDEX to codecTypes.ordinal,
                    RESULT_FILE_NAME to resultFileName
                ))
                .build()
            WorkManager.getInstance(context).enqueue(videoMergeWork)
        }

        /**
         * 実行中タスクがあるかどうかを返す
         *
         * @param context [Context]
         * @return ない場合はnull
         */
        fun existsRunningTask(context: Context) = callbackFlow {
            val liveData = WorkManager.getInstance(context).getWorkInfosByTagLiveData(WORKER_TAG)
            val callback = Observer<List<WorkInfo>> {
                trySend(it.firstOrNull { it.state == WorkInfo.State.RUNNING })
            }
            liveData.observeForever(callback)
            awaitClose { liveData.removeObserver(callback) }
        }

        /** 通知チャンネルのID */
        const val NOTIFICATION_CHANNEL_ID = "video_merge_task_notification"

        /** WorkManagerに指定しておくタグ */
        const val WORKER_TAG = "io.github.takusan23.kotoridroid.VIDEO_PROCESS_WORK"

        /** 加工したい動画のURI */
        const val VIDEO_URI = "io.github.takusan23.kotoridroid.VIDEO_PROCESS_WORK.video_uri"

        /** コーデック、コンテナフォーマット [EncoderCodecTypes] の位置 */
        const val ENCODER_CODEC_TYPE_INDEX = "io.github.takusan23.kotoridroid.VIDEO_PROCESS_WORK.encoder_codec_type_index"

        /** ファイル名 */
        const val RESULT_FILE_NAME = "io.github.takusan23.kotoridroid.VIDEO_PROCESS_WORK.result_file_name"

        /** 通知ID */
        const val NOTIFICATION_ID = 2525
    }
}