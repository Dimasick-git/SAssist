package dev.ryazha.sassist.model

/** The app deliberately defaults to Russian; English is an opt-in setting. */
enum class AppLanguage(val storedValue: String) {
    Russian("ru"),
    English("en");

    companion object {
        fun fromStored(value: String?): AppLanguage =
            entries.firstOrNull { it.storedValue == value } ?: Russian
    }
}
