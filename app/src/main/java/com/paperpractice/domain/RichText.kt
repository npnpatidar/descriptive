package com.paperpractice.domain

/**
 * Lightweight, offline rich text formatting.
 *
 * Supported markup:
 *   **bold**, *italic*, ++underline++, ~~strikethrough~~, ==highlight==
 *   H_2O, CO_2, x^2, x^{n+1}
 *   \frac{a}{b}, \sqrt{x}
 *   \alpha, \Delta, \times, \pm, \le, \ge, ->
 *
 * Pipe tables use a header row, a separator row and body rows:
 *   | Material | Density |
 *   | --- | ---: |
 *   | Copper | 8.96 |
 *
 * The original markup is kept in the JSON so an exported file can be imported
 * again without losing anything. [format] is the plain-text projection used by
 * search results and for backwards-compatible tests.
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

    private val markerOrder = listOf("**", "++", "~~", "==", "*")

    fun parse(input: String): RichDocument {
        if (input.isEmpty()) return RichDocument(emptyList())
        return RichDocument(parseBlocks(input))
    }

    /**
     * Plain-text projection. It keeps the old `(a)/(b)` fraction form so
     * search snippets and existing JSON round-trips remain compact.
     */
    fun format(input: String): String = plain(parse(input))

    fun plain(document: RichDocument): String = document.blocks.joinToString("\n\n") { block ->
        when (block) {
            is RichBlock.Paragraph -> plain(block.inlines)
            is RichBlock.Table -> {
                val lines = mutableListOf<String>()
                lines += block.headers.joinToString(" | ") { plain(it) }
                block.rows.forEach { row ->
                    lines += row.joinToString(" | ") { plain(it) }
                }
                lines.joinToString("\n")
            }
        }
    }

    fun plain(inlines: List<RichInline>): String = buildString {
        inlines.forEach { inline ->
            when (inline) {
                is RichInline.Text -> append(inline.text)
                is RichInline.Fraction -> {
                    append("(")
                    append(plain(inline.numerator))
                    append(")/(")
                    append(plain(inline.denominator))
                    append(")")
                }
            }
        }
    }

    fun plain(block: RichBlock): String = when (block) {
        is RichBlock.Paragraph -> plain(block.inlines)
        is RichBlock.Table -> plain(RichDocument(listOf(block)))
    }

    private fun parseBlocks(input: String): List<RichBlock> {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val blocks = mutableListOf<RichBlock>()
        var index = 0

        while (index < lines.size) {
            if (lines[index].isBlank()) {
                index++
                continue
            }

            if (isTableStart(lines, index)) {
                val headers = parseRow(lines[index])
                val separator = parseRow(lines[index + 1])
                val alignments = headers.indices.map { column ->
                    alignment(separator.getOrNull(column).orEmpty())
                }
                index += 2
                val rows = mutableListOf<List<List<RichInline>>>()
                while (index < lines.size) {
                    val line = lines[index]
                    if (line.isBlank() || !line.contains('|')) break
                    val cells = parseRow(line)
                    if (cells.size < 2) break
                    rows += headers.indices.map { column ->
                        parseInline(cells.getOrNull(column).orEmpty())
                    }
                    index++
                }
                if (rows.isNotEmpty()) {
                    blocks += RichBlock.Table(
                        headers = headers.map(::parseInline),
                        rows = rows,
                        alignments = alignments
                    )
                } else {
                    val start = (index - 2).coerceAtLeast(0)
                    blocks += RichBlock.Paragraph(
                        parseInline(lines.subList(start, index).joinToString("\n"))
                    )
                }
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size) {
                val line = lines[index]
                if (line.isBlank() || isTableStart(lines, index)) break
                paragraph += line
                index++
            }
            blocks += RichBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
        }

        return blocks
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val header = lines[index]
        val separator = lines[index + 1]
        if (!header.contains('|') || !separator.contains('|')) return false
        if (header.isBlank() || separator.isBlank()) return false
        val separatorCells = parseRow(separator)
        if (separatorCells.size < 2) return false
        return separatorCells.all { cell ->
            Regex("""^:?-{3,}:?$""").matches(cell.trim())
        }
    }

    private fun parseRow(line: String): List<String> {
        var value = line.trim()
        if (value.startsWith("|")) value = value.drop(1)
        if (value.endsWith("|") && !value.endsWith("\\|")) value = value.dropLast(1)
        if (!value.contains('|')) return listOf(value.trim())
        return value
            .split(Regex("""(?<!\\)\|"""))
            .map { cell -> cell.replace("\\|", "|").trim() }
    }

    private fun alignment(cell: String): ColumnAlignment {
        val value = cell.trim()
        return when {
            value.startsWith(":") && value.endsWith(":") -> ColumnAlignment.CENTER
            value.endsWith(":") -> ColumnAlignment.RIGHT
            else -> ColumnAlignment.LEFT
        }
    }

    private fun parseInline(
        text: String,
        style: RichInlineStyle = RichInlineStyle()
    ): List<RichInline> {
        val result = mutableListOf<RichInline>()
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isEmpty()) return
            result += RichInline.Text(formatText(buffer.toString()), style)
            buffer.clear()
        }

        var index = 0
        while (index < text.length) {
            val character = text[index]

            if (character == '\\') {
                val fraction = matchCommand(text, index, "frac") ?:
                    matchCommand(text, index, "dfrac")
                if (fraction != null) {
                    val numerator = readGroup(text, fraction)
                    val denominator = numerator?.let { readGroup(text, it.next) }
                    if (numerator != null && denominator != null) {
                        flush()
                        result += RichInline.Fraction(
                            numerator = parseInline(numerator.content, style),
                            denominator = parseInline(denominator.content, style),
                            style = style
                        )
                        index = denominator.next
                        continue
                    }
                }

                val squareRoot = matchCommand(text, index, "sqrt")
                if (squareRoot != null) {
                    val group = readGroup(text, squareRoot)
                    if (group != null) {
                        flush()
                        result += RichInline.Text("√(", style)
                        result += parseInline(group.content, style)
                        result += RichInline.Text(")", style)
                        index = group.next
                        continue
                    }
                }

                if (index + 1 < text.length && text[index + 1] in "\\*_^+=~`|{}[]()") {
                    buffer.append(text[index + 1])
                    index += 2
                    continue
                }

                val commandEnd = readCommandEnd(text, index)
                buffer.append(text, index, commandEnd)
                index = commandEnd
                continue
            }

            val marker = markerOrder.firstOrNull { text.startsWith(it, index) }
            if (marker != null) {
                val afterOpening = index + marker.length
                val hasContent = afterOpening < text.length &&
                    !text[afterOpening].isWhitespace()
                val close = if (hasContent) {
                    findClosingMarker(text, afterOpening, marker)
                } else {
                    -1
                }
                if (close > afterOpening && !text[close - 1].isWhitespace()) {
                    flush()
                    result += parseInline(
                        text.substring(afterOpening, close),
                        style.toggle(marker)
                    )
                    index = close + marker.length
                    continue
                }
            }

            buffer.append(character)
            index++
        }
        flush()
        return result
    }

    private fun matchCommand(text: String, index: Int, command: String): Int? {
        val token = "\\$command"
        if (!text.startsWith(token, index)) return null
        val next = index + token.length
        if (next < text.length && text[next].isLetter()) return null
        return next
    }

    private fun readGroup(text: String, start: Int): Group? {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        if (index >= text.length || text[index] != '{') return null
        val contentStart = index + 1
        var depth = 1
        index = contentStart
        while (index < text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return Group(text.substring(contentStart, index), index + 1)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun readCommandEnd(text: String, start: Int): Int {
        var index = start + 1
        if (index >= text.length) return text.length
        if (!text[index].isLetter()) return index + 1
        while (index < text.length && text[index].isLetter()) index++
        return index
    }

    private fun findClosingMarker(text: String, start: Int, marker: String): Int {
        var index = start
        while (index <= text.length - marker.length) {
            if (text.startsWith(marker, index)) {
                if (marker == "**" && text.startsWith("***", index)) {
                    // Let the last two stars close bold and the first star
                    // close a nested italic run, as in **bold *italic***.
                    index++
                    continue
                }
                if (marker != "*" || !text.startsWith("**", index)) {
                    return index
                }
            }
            index++
        }
        return -1
    }

    private fun formatText(raw: String): String {
        var text = raw
        text = Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""")
            .replace(text) { match -> "(${match.groupValues[1]})/(${match.groupValues[2]})" }
        text = Regex("""\\sqrt\{([^{}]*)\}""")
            .replace(text) { match -> "√(${match.groupValues[1]})" }
        text = Regex("""\\(?:text|mathrm|ce|pu)\{([^{}]*)\}""")
            .replace(text) { match -> match.groupValues[1] }

        // Match complete command names. A plain replace would turn \leq into
        // "≤q" because \le is a prefix.
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
                        val character = source[cursor]
                        if (character.isLetterOrDigit()) {
                            val mapped = mapping[character] ?: break
                            converted.append(mapped)
                            cursor++
                            continue
                        }
                        if (marker == '^' && (character == '+' || character == '-')) {
                            val mapped = mapping[character] ?: break
                            val next = source.getOrNull(cursor + 1)
                            val negativeExponent = character == '-' &&
                                converted.isEmpty() &&
                                next?.isDigit() == true
                            if (negativeExponent) {
                                // 10^-6 -> 10⁻⁶
                                converted.append(mapped)
                                cursor++
                                continue
                            }
                            // A trailing +/- is an ion charge (H^+, Cl^-,
                            // Fe^3+); a sign followed by another operand is
                            // ordinary arithmetic and stays unchanged.
                            if (next == null || !next.isLetterOrDigit()) {
                                converted.append(mapped)
                                cursor++
                            }
                        }
                        break
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

    private data class Group(
        val content: String,
        val next: Int
    )
}
