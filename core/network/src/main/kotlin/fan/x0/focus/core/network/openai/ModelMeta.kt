package fan.x0.focus.core.network.openai

data class ModelMeta(
    val contextWindow: Int? = null,
    val inputPrice: Double? = null,
    val outputPrice: Double? = null,
    val supportsImage: Boolean? = null,
    val supportsTools: Boolean? = null,
    val supportsReasoning: Boolean? = null,
    val description: String? = null,
    val knowledgeCutoff: String? = null,
    val releaseDate: String? = null,
) {
    val isEmpty: Boolean
        get() = this == ModelMeta()

    fun fillMissingFrom(other: ModelMeta): ModelMeta = ModelMeta(
        contextWindow = contextWindow ?: other.contextWindow,
        inputPrice = inputPrice ?: other.inputPrice,
        outputPrice = outputPrice ?: other.outputPrice,
        supportsImage = supportsImage ?: other.supportsImage,
        supportsTools = supportsTools ?: other.supportsTools,
        supportsReasoning = supportsReasoning ?: other.supportsReasoning,
        description = description ?: other.description,
        knowledgeCutoff = knowledgeCutoff ?: other.knowledgeCutoff,
        releaseDate = releaseDate ?: other.releaseDate,
    )
}

data class ModelEntry(
    val id: String,
    val meta: ModelMeta = ModelMeta(),
)

internal fun epochYearMonth(epochSeconds: Long): String {
    var days = epochSeconds / 86_400
    if (days < 0) days = 0
    var year = 1970
    while (days >= daysInYear(year)) {
        days -= daysInYear(year)
        year++
    }
    var month = 1
    while (days >= daysInMonth(year, month)) {
        days -= daysInMonth(year, month)
        month++
    }
    return "$year-${month.toString().padStart(2, '0')}"
}

private fun daysInYear(year: Int): Int = if (isLeapYear(year)) 366 else 365

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)