package fan.x0.focus.data.assistant

// Port for the in-app language switch; the app module owns the Android implementation.
interface LocaleController {
    fun current(): String?
    fun setLanguage(tag: String?)
}
