package co.nilin.opex.config.app.data

data class WebConfig(
    val defaultTheme: String = "DARK",
    val language: String = "en",
    val logoUrl: String = "logo/url",
    val title: String = "MasterMoon Exchange",
    val description: String = "MasterMoon Cryptocurrency Exchange",
    val supportedLanguages: List<String> = listOf("en"),
    val supportEmail: String = "support@mastermoon.cc",
    val baseCurrency: String = "USDT",
    val dateType: String = "Gregorian"
)

data class UserConfig(
    val theme: String? = null,
    val language: String? = null,
    val favoritePairs: List<String> = emptyList()
)

data class UserConfigRequest(
    val theme: String? = null,
    val language: String? = null,
    val favoritePairs: List<String>? = null
)
