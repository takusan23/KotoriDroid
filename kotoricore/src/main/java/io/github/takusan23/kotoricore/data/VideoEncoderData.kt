package io.github.takusan23.kotoricore.data

import io.github.takusan23.kotoricore.gl.FragmentShaders

/**
 * 動画エンコーダーに必要な情報
 *
 * null時は多分元データの情報が使われます
 *
 * @param codecName コーデックの名前
 * @param height 動画の高さ
 * @param width 動画の幅
 * @param bitRate ビットレート
 * @param frameRate フレームレート
 * @param fragmentShaders 動画にエフェクトをかける場合はフラグメントシェーダーを入れる
 */
data class VideoEncoderData(
    val codecName: String,
    val height: Int? = null,
    val width: Int? = null,
    val bitRate: Int? = null,
    val frameRate: Int? = null,
    val fragmentShaders: FragmentShaders? = null,
)