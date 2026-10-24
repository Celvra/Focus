package fan.x0.focus.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import fan.x0.focus.core.designsystem.R

object BrandIcons {

    @DrawableRes
    fun forProvider(id: String, name: String): Int? {
        val tokens = "$id $name".lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return providerBrands.firstOrNull { (keys, _) ->
            keys.any { key -> tokens.any { it == key || it.startsWith(key) } }
        }?.second
    }

    @DrawableRes
    fun forModel(model: String): Int? {
        val text = model.lowercase().substringAfterLast('/')
        return modelBrands.firstOrNull { (keys, _) -> keys.any { text.startsWithKey(it) } }?.second
    }

    private fun String.startsWithKey(key: String): Boolean =
        this == key || startsWith("$key-") || startsWith("$key.") || startsWith("${key}_") || startsWith("$key:")

    private val providerBrands: List<Pair<List<String>, Int>> = listOf(
        listOf("openrouter") to R.drawable.brand_openrouter,
        listOf("openai", "chatgpt") to R.drawable.brand_openai,
        listOf("anthropic", "claude") to R.drawable.brand_anthropic,
        listOf("deepseek") to R.drawable.brand_deepseek,
        listOf("google", "gemini", "gemma") to R.drawable.brand_google,
        listOf("azure") to R.drawable.brand_azure,
        listOf("bedrock") to R.drawable.brand_bedrock,
        listOf("vertex") to R.drawable.brand_vertexai,
        listOf("ollama") to R.drawable.brand_ollama,
        listOf("groq") to R.drawable.brand_groq,
        listOf("together") to R.drawable.brand_together,
        listOf("fireworks") to R.drawable.brand_fireworks,
        listOf("perplexity") to R.drawable.brand_perplexity,
        listOf("siliconflow", "siliconcloud", "silicon") to R.drawable.brand_siliconcloud,
        listOf("nvidia") to R.drawable.brand_nvidia,
        listOf("cohere") to R.drawable.brand_cohere,
        listOf("minimax") to R.drawable.brand_minimax,
        listOf("moonshot", "kimi") to R.drawable.brand_kimi,
        listOf("zhipu", "bigmodel", "chatglm", "glm") to R.drawable.brand_zhipu,
        listOf("qwen", "dashscope", "alibaba") to R.drawable.brand_qwen,
        listOf("mistral") to R.drawable.brand_mistral,
        listOf("xai", "grok") to R.drawable.brand_xai,
        listOf("hunyuan") to R.drawable.brand_hunyuan,
        listOf("doubao", "volcengine", "bytedance") to R.drawable.brand_doubao,
        listOf("tencent") to R.drawable.brand_tencent,
        listOf("baidu", "ernie") to R.drawable.brand_baidu,
        listOf("stepfun") to R.drawable.brand_stepfun,
        listOf("baichuan") to R.drawable.brand_baichuan,
        listOf("lingyiwanwu", "yi") to R.drawable.brand_yi,
        listOf("meta", "llama") to R.drawable.brand_meta,
        listOf("github") to R.drawable.brand_github,
    )

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
