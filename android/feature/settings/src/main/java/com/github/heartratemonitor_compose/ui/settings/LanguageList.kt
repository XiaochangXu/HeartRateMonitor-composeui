package com.github.heartratemonitor_compose.ui.settings

/**
 * 语言设置页可选语言列表。
 *
 * - [LanguageOption.autoFollow]：自动跟随系统语言（值为 null）。
 * - 其余为具体语言，[tag] 为 BCP 47 语言 Tag（用于持久化与 LocaleListCompat），
 *   [emoji] 为国旗 emoji，[displayNameKey] 无意义——展示名直接用 [nativeName] /
 *   [subtitle]（母语写法 + 英文副标题），不随当前 app locale 变化。
 *
 * 卡片顺序遵循需求：自动跟随 → 中文 → 中国香港 → 中国台湾 → 英语 → 日语 → 韩语 →
 * 德语 → 法语 → 俄语 → 西班牙语 → 葡萄牙语 → 越南语 → 泰语 → 意大利语 → 波兰语 →
 * 荷兰语 → 土耳其语 → 印尼语 → 菲律宾语 → 马来语 → 印地语 → 孟加拉语 → 阿拉伯语 → 尼泊尔语。
 */
internal data class LanguageOption(
    val tag: String?,
    val emoji: String,
    val nativeName: String,
    val subtitle: String
)

internal val AUTO_FOLLOW_OPTION = LanguageOption(
    tag = null,
    emoji = "🌐",
    nativeName = "Auto Follow",
    subtitle = "Use system language"
)

internal val LANGUAGE_OPTIONS: List<LanguageOption> = listOf(
    LanguageOption("zh-CN", "🇨🇳", "中文", "你好，欢迎来中国"),
    LanguageOption("zh-HK", "🇭🇰", "繁體中文（香港）", "你好，歡迎嚟香港"),
    LanguageOption("zh-TW", "🇹🇼", "繁體中文（台灣）", "你好，歡迎來台灣"),
    LanguageOption("en", "🇬🇧", "English", "Hello, welcome to the UK"),
    LanguageOption("ja", "🇯🇵", "日本語", "こんにちは、日本へようこそ"),
    LanguageOption("ko", "🇰🇷", "한국어", "안녕하세요, 대한민국에 오신 것을 환영합니다"),
    LanguageOption("de", "🇩🇪", "Deutsch", "Hallo, willkommen in Deutschland"),
    LanguageOption("fr", "🇫🇷", "Français", "Bonjour, bienvenue en France"),
    LanguageOption("ru", "🇷🇺", "Русский", "Привет, добро пожаловать в Россию"),
    LanguageOption("es", "🇪🇸", "Español", "Hola, bienvenido a España"),
    LanguageOption("pt", "🇵🇹", "Português", "Olá, bem-vindo a Portugal"),
    LanguageOption("vi", "🇻🇳", "Tiếng Việt", "Xin chào, chào mừng đến Việt Nam"),
    LanguageOption("th", "🇹🇭", "ไทย", "สวัสดี ยินดีต้อนรับสู่ประเทศไทย"),
    LanguageOption("it", "🇮🇹", "Italiano", "Ciao, benvenuto in Italia"),
    LanguageOption("pl", "🇵🇱", "Polski", "Cześć, witamy w Polsce"),
    LanguageOption("nl", "🇳🇱", "Nederlands", "Hallo, welkom in Nederland"),
    LanguageOption("tr", "🇹🇷", "Türkçe", "Merhaba, Türkiye'ye hoş geldiniz"),
    LanguageOption("id", "🇮🇩", "Indonesia", "Halo, selamat datang di Indonesia"),
    LanguageOption("fil", "🇵🇭", "Filipino", "Kumusta, maligayang pagdating sa Pilipinas"),
    LanguageOption("ms", "🇲🇾", "Melayu", "Helo, selamat datang ke Malaysia"),
    LanguageOption("hi", "🇮🇳", "हिन्दी", "नमस्ते, भारत में आपका स्वागत है"),
    LanguageOption("bn", "🇧🇩", "বাংলা", "নমস্কার, বাংলাদেশে স্বাগতম"),
    LanguageOption("ar", "🇸🇦", "العربية", "مرحباً، أهلاً بك في المملكة العربية السعودية"),
    LanguageOption("ne", "🇳🇵", "नेपाली", "नमस्ते, नेपालमा स्वागत छ")
)
