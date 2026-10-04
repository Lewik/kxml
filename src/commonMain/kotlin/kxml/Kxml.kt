package kxml

/** A bounded, dependency-free XML subset parser. It never resolves DTDs or external entities. */
class Kxml {
    data class Element(
        val name: String,
        /** Lexical prefix, not a resolved namespace URI. */
        val namespace: String?,
        val attrs: List<Tag.Attribute>,
        val content: List<Pair<Element?, String?>>,
    ) {
        /** Direct text children, excluding text inside descendant elements. */
        fun getInnerText(): String = content.mapNotNull { it.second }.joinToString("")
    }

    data class Tag(
        val name: String,
        val namespace: String?,
        val type: TagType,
        val attrs: List<Attribute>,
    ) {
        enum class TagType { NOT_SPECIFIED, START, END, SINGLE, HEADER, ROOT }

        data class Attribute(val name: String, val namespace: String?, val value: String)
    }

    data class Limits(
        /** UTF-16 code units, as measured by String.length. */
        val maxInputLength: Int = 1_048_576,
        val maxDepth: Int = 128,
        /** Elements plus nonempty, coalesced text nodes. */
        val maxNodes: Int = 100_000,
        val maxAttributesPerElement: Int = 256,
    ) {
        init {
            require(maxInputLength > 0 && maxDepth > 0 && maxNodes > 0 && maxAttributesPerElement >= 0)
        }
    }

    class ParseException(
        detail: String,
        /** Zero-based UTF-16 offset in the original input. */
        val offset: Int,
        /** One-based line and UTF-16 column; CRLF counts as one line break. */
        val line: Int,
        val column: Int,
    ) : IllegalArgumentException("$detail at $line:$column (offset $offset)")

    companion object {
        /** Parses an XML fragment: zero or more elements and text nodes, in source order. */
        fun parse(text: String, limits: Limits = Limits()): List<Pair<Element?, String?>> =
            Parser(text, limits, document = false).parse()

        /** Requires exactly one root element and only whitespace/comments/PIs outside it. */
        fun parseDocument(text: String, limits: Limits = Limits()): Element =
            Parser(text, limits, document = true).parse().firstNotNullOf { it.first }
    }
}

private class Parser(private val source: String, private val limits: Kxml.Limits, private val document: Boolean) {
    private class Frame(val name: String, val attrs: List<Kxml.Tag.Attribute>) {
        val content = mutableListOf<Pair<Kxml.Element?, String?>>()
        val text = StringBuilder()
    }

    private var position = 0
    private var nodes = 0
    private var roots = 0
    private val frames = mutableListOf(Frame("", emptyList()))

    fun parse(): List<Pair<Kxml.Element?, String?>> {
        if (source.length > limits.maxInputLength) fail("Input length limit exceeded", limits.maxInputLength)
        validateCharacters()
        if (source.startsWith('\uFEFF')) position++
        while (position < source.length) {
            when {
                starts("<!--") -> readComment()
                starts("<![CDATA[") -> readCdata()
                starts("<?") -> readInstruction()
                starts("</") -> closeElement()
                starts("<!") -> fail("DTD and markup declarations are not supported")
                source[position] == '<' -> openElement()
                else -> readText()
            }
        }
        if (frames.size != 1) fail("Missing closing tag for '${frames.last().name.take(80)}'")
        flushText()
        if (document && roots != 1) fail("Expected exactly one root element")
        return frames.single().content.toList()
    }

    private fun openElement() {
        val start = position++
        if (frames.size > limits.maxDepth) fail("Nesting depth limit exceeded", start)
        if (frames.size == 1 && ++roots > 1 && document) fail("Multiple root elements", start)
        flushText()
        countNode()
        val name = readName()
        val (attrs, empty) = readAttributes(declaration = false)
        val frame = Frame(name, attrs)
        if (empty) frames.last().content.add(element(frame) to null) else frames.add(frame)
    }

    private fun closeElement() {
        val start = position
        position += 2
        val name = readName()
        skipSpace()
        expect(">")
        if (frames.size == 1) fail("Unexpected closing tag '${name.take(80)}'", start)
        val frame = frames.last()
        if (name != frame.name) {
            fail("Expected closing tag '${frame.name.take(80)}', found '${name.take(80)}'", start)
        }
        flushText()
        frames.removeAt(frames.lastIndex)
        frames.last().content.add(element(frame) to null)
    }

    private fun element(frame: Frame): Kxml.Element = Kxml.Element(
        name = frame.name.substringAfter(':'),
        namespace = prefix(frame.name),
        attrs = frame.attrs,
        content = frame.content.toList(),
    )

    private fun readAttributes(declaration: Boolean): Pair<List<Kxml.Tag.Attribute>, Boolean> {
        val attrs = mutableListOf<Kxml.Tag.Attribute>()
        val names = mutableSetOf<String>()
        while (true) {
            val separated = skipSpace()
            if (take(if (declaration) "?>" else ">")) return attrs to false
            if (!declaration && take("/>")) return attrs to true
            if (!separated) fail("Expected whitespace before attribute or end of tag")
            val start = position
            if (attrs.size >= limits.maxAttributesPerElement) fail("Attribute count limit exceeded")
            val name = readName()
            if (!names.add(name)) fail("Duplicate attribute '${name.take(80)}'", start)
            skipSpace()
            expect("=")
            skipSpace()
            val value = readAttributeValue(declaration)
            attrs.add(Kxml.Tag.Attribute(name.substringAfter(':'), prefix(name), value))
        }
    }

    private fun readAttributeValue(declaration: Boolean): String {
        val quote = source.getOrNull(position)
        if (quote != '\'' && quote != '"') fail("Attribute value must be quoted")
        position++
        val value = StringBuilder()
        while (position < source.length && source[position] != quote) {
            when (source[position]) {
                '<' -> fail("Literal '<' is not allowed in attribute values")
                '&' -> {
                    if (declaration) fail("References are not allowed in XML declarations")
                    value.append(readReference())
                }
                else -> appendLiteral(value, attribute = true)
            }
        }
        if (position == source.length) fail("Unterminated attribute value")
        position++
        return value.toString()
    }

    private fun readText() {
        val text = frames.last().text
        while (position < source.length && source[position] != '<') {
            if (document && frames.size == 1 && !isSpace(source[position])) {
                fail("Only whitespace is allowed outside the root element")
            }
            if (starts("]]>")) fail("']]>' is only allowed as a CDATA terminator")
            if (source[position] == '&') text.append(readReference()) else appendLiteral(text)
        }
    }

    private fun readCdata() {
        if (document && frames.size == 1) fail("CDATA must be inside the root element")
        position += 9
        val end = source.indexOf("]]>", position)
        if (end < 0) fail("Unterminated CDATA section")
        while (position < end) appendLiteral(frames.last().text)
        position += 3
    }

    private fun readComment() {
        position += 4
        val end = source.indexOf("--", position)
        if (end < 0) fail("Unterminated comment")
        if (!source.startsWith("-->", end)) fail("'--' is not allowed inside a comment", end)
        position = end + 3
    }

    private fun readInstruction() {
        val start = position
        position += 2
        val target = readName()
        if (target.equals("xml", ignoreCase = true)) {
            val beginning = if (source.startsWith('\uFEFF')) 1 else 0
            if (target != "xml" || start != beginning) fail("XML declaration must be first and lowercase", start)
            val (attrs, _) = readAttributes(declaration = true)
            val names = attrs.map { it.name }
            val allowedOrders = listOf(
                listOf("version"), listOf("version", "encoding"),
                listOf("version", "standalone"), listOf("version", "encoding", "standalone"),
            )
            if (names !in allowedOrders || attrs.any { it.namespace != null } || attrs.first().value != "1.0") {
                fail("Expected an XML 1.0 declaration", start)
            }
            attrs.firstOrNull { it.name == "encoding" }?.let {
                if (!it.value.matches(Regex("[A-Za-z][A-Za-z0-9._-]*"))) fail("Invalid encoding name", start)
            }
            attrs.firstOrNull { it.name == "standalone" }?.let {
                if (it.value != "yes" && it.value != "no") fail("Invalid standalone value", start)
            }
        } else {
            if (take("?>")) return
            if (!skipSpace()) fail("Expected whitespace after processing instruction target")
            val end = source.indexOf("?>", position)
            if (end < 0) fail("Unterminated processing instruction")
            position = end + 2
        }
    }

    private fun readReference(): String {
        val start = position++
        val end = source.indexOf(';', position)
        if (end < 0) fail("Unterminated entity or character reference", start)
        val ref = source.substring(position, end)
        position = end + 1
        return when (ref) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "apos" -> "'"
            "quot" -> "\""
            else -> {
                if (!ref.startsWith('#')) fail("Unknown entity '${ref.take(48)}'", start)
                val hex = ref.startsWith("#x")
                val digits = ref.substring(if (hex) 2 else 1)
                if (digits.isEmpty() || digits.any { it !in '0'..'9' && !(hex && (it in 'a'..'f' || it in 'A'..'F')) }) {
                    fail("Invalid numeric character reference", start)
                }
                val code = digits.toIntOrNull(if (hex) 16 else 10)
                    ?: fail("Character reference is out of range", start)
                if (!isXmlCharacter(code)) fail("Invalid XML character reference", start)
                if (code < 0x10000) code.toChar().toString() else {
                    val adjusted = code - 0x10000
                    "${(0xD800 + (adjusted shr 10)).toChar()}${(0xDC00 + (adjusted and 0x3FF)).toChar()}"
                }
            }
        }
    }

    private fun readName(): String {
        val start = position
        readNamePart()
        if (take(":")) readNamePart()
        return source.substring(start, position)
    }

    private fun readNamePart() {
        if (position == source.length || !isNameStart(codePoint(position))) fail("Expected an XML name")
        position += codePointSize(position)
        while (position < source.length && isNamePart(codePoint(position))) position += codePointSize(position)
    }

    private fun appendLiteral(target: StringBuilder, attribute: Boolean = false) {
        val char = source[position++]
        if (char == '\r') {
            if (source.getOrNull(position) == '\n') position++
            target.append(if (attribute) ' ' else '\n')
        } else {
            target.append(if (attribute && (char == '\n' || char == '\t')) ' ' else char)
        }
    }

    private fun flushText() {
        val frame = frames.last()
        if (frame.text.isNotEmpty()) {
            countNode()
            frame.content.add(null to frame.text.toString())
            frame.text.clear()
        }
    }

    private fun countNode() {
        if (++nodes > limits.maxNodes) fail("Node count limit exceeded")
    }

    private fun validateCharacters() {
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char.isHighSurrogate() && source.getOrNull(index + 1)?.isLowSurrogate() != true) {
                fail("Unpaired UTF-16 surrogate", index)
            }
            if (!isXmlCharacter(codePoint(index))) fail("Invalid XML character", index)
            index += codePointSize(index)
        }
    }

    private fun codePoint(index: Int): Int {
        val char = source[index]
        return if (char.isHighSurrogate()) {
            0x10000 + ((char.code - 0xD800) shl 10) + (source[index + 1].code - 0xDC00)
        } else char.code
    }

    private fun codePointSize(index: Int): Int = if (source[index].isHighSurrogate()) 2 else 1
    private fun prefix(name: String): String? = name.substringBefore(':', "").ifEmpty { null }
    private fun starts(value: String): Boolean = source.startsWith(value, position)
    private fun take(value: String): Boolean = starts(value).also { if (it) position += value.length }
    private fun expect(value: String) { if (!take(value)) fail("Expected '$value'") }
    private fun skipSpace(): Boolean {
        val start = position
        while (position < source.length && isSpace(source[position])) position++
        return position != start
    }

    private fun fail(detail: String, at: Int = position): Nothing {
        var line = 1
        var column = 1
        var index = 0
        while (index < at) {
            when (source[index++]) {
                '\r' -> {
                    if (index < at && source[index] == '\n') index++
                    line++
                    column = 1
                }
                '\n' -> { line++; column = 1 }
                else -> column++
            }
        }
        throw Kxml.ParseException(detail, at, line, column)
    }
}

private fun isSpace(char: Char): Boolean = char == ' ' || char == '\t' || char == '\r' || char == '\n'

private fun isXmlCharacter(code: Int): Boolean = code == 0x9 || code == 0xA || code == 0xD ||
    code in 0x20..0xD7FF || code in 0xE000..0xFFFD || code in 0x10000..0x10FFFF

// XML 1.0 fifth-edition NameStartChar / NameChar, without ':' (handled as a QName separator).
private fun isNameStart(code: Int): Boolean = code == 0x5F || code in 0x41..0x5A || code in 0x61..0x7A ||
    code in 0xC0..0xD6 || code in 0xD8..0xF6 || code in 0xF8..0x2FF || code in 0x370..0x37D ||
    code in 0x37F..0x1FFF || code in 0x200C..0x200D || code in 0x2070..0x218F || code in 0x2C00..0x2FEF ||
    code in 0x3001..0xD7FF || code in 0xF900..0xFDCF || code in 0xFDF0..0xFFFD || code in 0x10000..0xEFFFF

private fun isNamePart(code: Int): Boolean = isNameStart(code) || code == 0x2D || code == 0x2E ||
    code in 0x30..0x39 || code == 0xB7 || code in 0x300..0x36F || code in 0x203F..0x2040
