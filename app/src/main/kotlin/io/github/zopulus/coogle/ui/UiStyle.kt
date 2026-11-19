package io.github.zopulus.coogle.ui

enum class UiStyle(val title: String) {
    COUI("COUI"),
    MATERIAL("Material 3");

    companion object {
        val default = COUI

        @JvmStatic
        fun fromPreference(value: String?): UiStyle = entries.firstOrNull { it.name == value } ?: default
    }
}
