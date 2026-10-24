package fan.x0.focus.data.assistant

enum class ReasoningLevel(val wire: String?, val budgetTokens: Int) {
    Off("none", 0),
    Auto(null, -1),
    Low("low", 1_000),
    Medium("medium", 2_000),
    High("high", 8_000),
    XHigh("xhigh", 16_000),
    Max("max", 32_000),
    ;

    val isEnabled: Boolean get() = this != Off

    companion object {
        fun fromName(value: String?): ReasoningLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Auto
    }
}
