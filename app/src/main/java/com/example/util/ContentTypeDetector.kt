package com.example.util

import com.example.data.model.ContentType
import java.util.regex.Pattern

data class DetectedContent(
    val type: ContentType,
    val colorHex: String? = null
)

object ContentTypeDetector {

    private val URL_PATTERN = Pattern.compile(
        "^((https?|ftp)://|(www\\.))[a-zA-Z0-9+&@#/%?=~_|!:,.;\\-]*[a-zA-Z0-9+&@#/%=~_|]",
        Pattern.CASE_INSENSITIVE
    )

    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )

    private val PHONE_PATTERN = Pattern.compile(
        "^(\\+?\\d{1,4}[\\s-]?)?(\\(?\\d{2,4}\\)?[\\s-]?)?[\\d\\s-]{6,14}\\d$"
    )

    private val HEX_COLOR_PATTERN = Pattern.compile(
        "^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"
    )

    private val CODE_KEYWORDS = listOf(
        "val ", "var ", "fun ", "def ", "function ", "class ", "interface ",
        "import ", "export ", "public ", "private ", "protected ", "return ",
        "const ", "let ", "select ", "from ", "where ", "<html>", "<div",
        "console.log", "System.out", "println", "void ", "#include", "namespace"
    )

    fun detect(rawText: String): DetectedContent {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return DetectedContent(ContentType.TEXT)
        }

        // Check Hex Color
        if (HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            return DetectedContent(ContentType.HEX_COLOR, colorHex = trimmed)
        }

        // Check URL
        if (URL_PATTERN.matcher(trimmed).matches() || 
            (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true))) {
            return DetectedContent(ContentType.URL)
        }

        // Check Email
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            return DetectedContent(ContentType.EMAIL)
        }

        // Check Phone (ensure it has at least 7 digits)
        val digitsCount = trimmed.count { it.isDigit() }
        if (digitsCount in 7..15 && PHONE_PATTERN.matcher(trimmed).matches()) {
            return DetectedContent(ContentType.PHONE)
        }

        // Check JSON
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            if (trimmed.contains(":") || trimmed.contains(",")) {
                return DetectedContent(ContentType.JSON)
            }
        }

        // Check Code
        val lower = trimmed.lowercase()
        val hasCodeKeyword = CODE_KEYWORDS.any { lower.contains(it) }
        val hasCodePunctuation = trimmed.contains(";") || trimmed.contains("->") || trimmed.contains("=>") || trimmed.contains("{\n")
        if (hasCodeKeyword && (trimmed.contains("\n") || hasCodePunctuation)) {
            return DetectedContent(ContentType.CODE)
        }

        return DetectedContent(ContentType.TEXT)
    }
}
