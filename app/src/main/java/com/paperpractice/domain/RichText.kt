package com.paperpractice.domain

/**
 * Lightweight, offline formula formatting. It intentionally stores the original
 * author text and only converts it for display/export, so JSON round-trips keep
 * exactly what the user typed.
 *
 * Supported examples:
 *   H_2O, CO_2, C_6H_12O_6
 *   x^2, x^{n+1}
 *   H_2 + O_2 -> H_2O
 *   \frac{a}{b}, \sqrt{x}
 *   \alpha, \Delta, \times, \pm, \le, \ge
 */
object RichText {

    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ'
    )

    private val commands = mapOf(
        "alpha" to "α",
        "beta" to "β",
        "gamma" to "γ",
        "delta" to "δ",
        "epsilon" to "ε",
        "zeta" to "ζ",
        "eta" to "η",
        "theta" to "θ",
        "iota" to "ι",
        "kappa" to "κ",
        "lambda" to "λ",
        "mu" to "μ",
        "nu" to "ν",
        "xi" to "ξ",
        "pi" to "π",
        "rho" to "ρ",
        "sigma" to "σ",
        "tau" to "τ",
        "upsilon" to "υ",
        "phi" to "φ",
        "chi" to "χ",
        "psi" to "ψ",
        "omega" to "ω",
        "Gamma" to "Γ",
        "Delta" to "Δ",
        "Theta" to "Θ",
        "Lambda" to "Λ",
        "Xi" to "Ξ",
        "Pi" to "Π",
        "Sigma" to "Σ",
        "Phi" to "Φ",
        "Psi" to "Ψ",
        "Omega" to "Ω",
        "times" to "×",
        "cdot" to "·",
        "div" to "÷",
        "pm" to "±",
        "mp" to "∓",
        "le" to "≤",
        "leq" to "≤",
        "ge" to "≥",
        "geq" to "≥",
        "neq" to "≠",
        "approx" to "≈",
        "equiv" to "≡",
        "infty" to "∞",
        "degree" to "°",
        "sum" to "∑",
        "prod" to "∏",
        "int" to "∫",
        "rightleftharpoons" to "⇌",
        "leftrightarrow" to "↔",
        "rightarrow" to "→",
        "to" to "→",
        "leftarrow" to "←",
        "uparrow" to "↑",
        "downarrow" to "↓"
    )

    fun format(input: String): String {
        if (input.isEmpty()) return input

        var text = input
        text = Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""")
            .replace(text) { match -> "(${match.groupValues[1]})/(${match.groupValues[2]})" }
        text = Regex("""\\sqrt\{([^{}]*)\}""")
            .replace(text) { match -> "√(${match.groupValues[1]})" }
        text = Regex("""\\(?:text|mathrm|ce|pu)\{([^{}]*)\}""")
            .replace(text) { match -> match.groupValues[1] }

        // Match complete command names. A plain replace would turn \leq into
        // "≤q" because \le is a prefix, and would corrupt \rightarrow while
        // stripping \right.
        val commandNames = commands.keys
            .sortedByDescending { it.length }
            .joinToString("|")
        val commandPattern = Regex("""\\($commandNames)\b""")
        text = commandPattern.replace(text) { match ->
            commands[match.groupValues[1]] ?: match.value
        }
        text = text.replace(Regex("""\\(?:left|right)\b"""), "")

        text = replaceScripts(text, '_', subscripts)
        text = replaceScripts(text, '^', superscripts)

        return text
            .replace("<=>", "⇌")
            .replace("<->", "↔")
            .replace("-->", "→")
            .replace("->", "→")
            .replace("<-", "←")
            .replace("=>", "⇒")
    }

    private fun replaceScripts(
        source: String,
        marker: Char,
        mapping: Map<Char, Char>
    ): String {
        val result = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            val current = source[index]
            if (current == marker && index + 1 < source.length) {
                if (source[index + 1] == '{') {
                    val end = source.indexOf('}', index + 2)
                    if (end > index + 2) {
                        val content = source.substring(index + 2, end)
                        val converted = content.map { mapping[it] }
                        if (converted.all { it != null }) {
                            converted.forEach { result.append(it) }
                            index = end + 1
                            continue
                        }
                    }
                } else {
                    // Unbraced scripts often contain several characters, for
                    // example C_6H_12O_6. Consume the whole mapped run so
                    // "12" becomes a two-character subscript.
                    var cursor = index + 1
                    val converted = StringBuilder()
                    while (cursor < source.length) {
                        val mapped = mapping[source[cursor]] ?: break
                        converted.append(mapped)
                        cursor++
                    }
                    if (converted.isNotEmpty()) {
                        result.append(converted)
                        index = cursor
                        continue
                    }
                }
            }
            result.append(current)
            index++
        }
        return result.toString()
    }
}
