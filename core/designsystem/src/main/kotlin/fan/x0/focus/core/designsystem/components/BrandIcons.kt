package fan.x0.focus.core.designsystem.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import fan.x0.focus.core.designsystem.R

data class ProviderIconOption(
    val key: String,
    val label: String,
    @DrawableRes val icon: Int,
    val aliases: List<String>,
)

object BrandIcons {

    val providerIcons: List<ProviderIconOption> = listOf(
        ProviderIconOption("openrouter", "OpenRouter", R.drawable.brand_openrouter, listOf("openrouter")),
        ProviderIconOption("openai", "OpenAI", R.drawable.brand_openai, listOf("openai", "chatgpt")),
        ProviderIconOption("anthropic", "Anthropic", R.drawable.brand_anthropic, listOf("anthropic", "claude")),
        ProviderIconOption("deepseek", "DeepSeek", R.drawable.brand_deepseek, listOf("deepseek")),
        ProviderIconOption("google", "Google", R.drawable.brand_google, listOf("google", "gemini", "gemma")),
        ProviderIconOption("azure", "Azure", R.drawable.brand_azure, listOf("azure")),
        ProviderIconOption("bedrock", "Bedrock", R.drawable.brand_bedrock, listOf("bedrock", "amazon")),
        ProviderIconOption("vertex", "Vertex AI", R.drawable.brand_vertexai, listOf("vertex", "vertexai")),
        ProviderIconOption("ollama", "Ollama", R.drawable.brand_ollama, listOf("ollama")),
        ProviderIconOption("groq", "Groq", R.drawable.brand_groq, listOf("groq")),
        ProviderIconOption("together", "Together", R.drawable.brand_together, listOf("together")),
        ProviderIconOption("fireworks", "Fireworks", R.drawable.brand_fireworks, listOf("fireworks")),
        ProviderIconOption("perplexity", "Perplexity", R.drawable.brand_perplexity, listOf("perplexity")),
        ProviderIconOption("siliconcloud", "SiliconCloud", R.drawable.brand_siliconcloud, listOf("siliconflow", "siliconcloud", "silicon")),
        ProviderIconOption("nvidia", "NVIDIA", R.drawable.brand_nvidia, listOf("nvidia")),
        ProviderIconOption("cohere", "Cohere", R.drawable.brand_cohere, listOf("cohere")),
        ProviderIconOption("minimax", "MiniMax", R.drawable.brand_minimax, listOf("minimax")),
        ProviderIconOption("kimi", "Kimi", R.drawable.brand_kimi, listOf("moonshot", "kimi")),
        ProviderIconOption("zhipu", "Zhipu", R.drawable.brand_zhipu, listOf("zhipu", "bigmodel", "chatglm", "glm")),
        ProviderIconOption("qwen", "Qwen", R.drawable.brand_qwen, listOf("qwen", "dashscope", "alibaba")),
        ProviderIconOption("mistral", "Mistral", R.drawable.brand_mistral, listOf("mistral")),
        ProviderIconOption("xai", "xAI", R.drawable.brand_xai, listOf("xai", "grok")),
        ProviderIconOption("hunyuan", "Hunyuan", R.drawable.brand_hunyuan, listOf("hunyuan")),
        ProviderIconOption("doubao", "Doubao", R.drawable.brand_doubao, listOf("doubao", "volcengine", "bytedance")),
        ProviderIconOption("tencent", "Tencent", R.drawable.brand_tencent, listOf("tencent")),
        ProviderIconOption("baidu", "Baidu", R.drawable.brand_baidu, listOf("baidu", "ernie")),
        ProviderIconOption("stepfun", "StepFun", R.drawable.brand_stepfun, listOf("stepfun")),
        ProviderIconOption("baichuan", "Baichuan", R.drawable.brand_baichuan, listOf("baichuan")),
        ProviderIconOption("yi", "Yi", R.drawable.brand_yi, listOf("lingyiwanwu", "yi")),
        ProviderIconOption("meta", "Meta", R.drawable.brand_meta, listOf("meta", "llama")),
        ProviderIconOption("github", "GitHub", R.drawable.brand_github, listOf("github")),
    )

    @DrawableRes
    fun forProvider(id: String, name: String): Int? {
        val tokens = "$id $name".lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return providerIcons.firstOrNull { option ->
            option.aliases.any { key -> tokens.any { it == key || it.startsWith(key) } }
        }?.icon
    }

    @DrawableRes
    fun forKey(key: String): Int? =
        providerIcons.firstOrNull { it.key == key.lowercase() }?.icon

    fun labelFor(key: String): String? =
        providerIcons.firstOrNull { it.key == key.lowercase() }?.label

    fun isCustomImage(icon: String): Boolean = icon.startsWith("data:image/")

    @DrawableRes
    fun forModel(model: String): Int? {
        val text = model.lowercase().substringAfterLast('/')
        return modelBrands.firstOrNull { (keys, _) -> keys.any { text.startsWithKey(it) } }?.second
    }

    private fun String.startsWithKey(key: String): Boolean =
        this == key || startsWith("$key-") || startsWith("$key.") || startsWith("${key}_") || startsWith("$key:")

    private val modelBrands: List<Pair<List<String>, Int>> = listOf(
        listOf("openrouter") to R.drawable.brand_openrouter,
        listOf("gpt", "chatgpt", "o1", "o3", "o4", "davinci") to R.drawable.brand_openai,
        listOf("claude", "anthropic") to R.drawable.brand_anthropic,
        listOf("deepseek") to R.drawable.brand_deepseek,
        listOf("gemini", "gemma", "palm") to R.drawable.brand_gemini,
        listOf("qwen", "qwq", "qvq") to R.drawable.brand_qwen,
        listOf("glm", "chatglm") to R.drawable.brand_chatglm,
        listOf("kimi", "moonshot") to R.drawable.brand_kimi,
        listOf("mistral", "mixtral", "codestral", "ministral", "magistral", "devstral") to R.drawable.brand_mistral,
        listOf("grok") to R.drawable.brand_grok,
        listOf("llama", "meta") to R.drawable.brand_meta,
        listOf("yi", "lingyiwanwu") to R.drawable.brand_yi,
        listOf("baichuan") to R.drawable.brand_baichuan,
        listOf("doubao", "seed") to R.drawable.brand_doubao,
        listOf("hunyuan") to R.drawable.brand_hunyuan,
        listOf("minimax", "abab") to R.drawable.brand_minimax,
        listOf("step") to R.drawable.brand_stepfun,
        listOf("ernie") to R.drawable.brand_baidu,
        listOf("command") to R.drawable.brand_cohere,
        listOf("sonar", "perplexity") to R.drawable.brand_perplexity,
        listOf("nemotron", "nvidia") to R.drawable.brand_nvidia,
    )
}

@Composable
fun BrandIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun rememberProviderImage(icon: String): ImageBitmap? = remember(icon) {
    if (!BrandIcons.isCustomImage(icon)) return@remember null
    val payload = icon.substringAfter(',', "")
    if (payload.isBlank()) return@remember null
    val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
        ?: return@remember null
    runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
