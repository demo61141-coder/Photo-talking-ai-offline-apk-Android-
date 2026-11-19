package com.example.i18n

enum class AppLanguage(val code: String, val shortLabel: String, val displayName: String) {
    EN("en", "EN", "English"),
    BN("bn", "বাংলা", "বাংলা (Bengali)");

    fun toggled(): AppLanguage = if (this == EN) BN else EN
}
