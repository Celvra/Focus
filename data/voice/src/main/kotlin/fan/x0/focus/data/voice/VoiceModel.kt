package fan.x0.focus.data.voice

internal data class VoiceModelFile(
    val name: String,
    val bytes: Long,
)

internal object LocalVoiceModel {
    const val DIR = "zh-14m"
    const val ARCHIVE_NAME = "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
    const val ARCHIVE_BYTES = 74_004_050L
    const val ARCHIVE_URL =
        "https://modelscope.cn/models/zhaochaoqun/sherpa-onnx-asr-models/resolve/master/" +
            "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"

    const val ENCODER = "encoder-epoch-99-avg-1.int8.onnx"
    const val DECODER = "decoder-epoch-99-avg-1.int8.onnx"
    const val JOINER = "joiner-epoch-99-avg-1.int8.onnx"
    const val TOKENS = "tokens.txt"

    val files = listOf(
        VoiceModelFile(ENCODER, 21_621_684L),
        VoiceModelFile(DECODER, 1_888_682L),
        VoiceModelFile(JOINER, 1_795_562L),
        VoiceModelFile(TOKENS, 48_697L),
    )
}
