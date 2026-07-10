package com.example.utils

object ArabicNormalizer {
    /**
     * Normalizes Arabic text by converting all variations of Alef (أ, إ, آ, ٱ)
     * into a plain Alef (ا), compressing multiple spaces, and trimming whitespace.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace("[أإآٱ]".toRegex(), "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
