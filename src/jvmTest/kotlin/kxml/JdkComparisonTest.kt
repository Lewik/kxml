package kxml

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.xml.sax.InputSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class JdkComparisonTest {
    @Test
    fun supportedDocumentsAgreeWithJdkParser() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isCoalescing = true
            isIgnoringComments = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val builder = factory.newDocumentBuilder()
        val random = Random(8472)
        repeat(300) {
            val xml = "<?xml version='1.0'?>" + elementSource(random, 0)
            val expected = fromDom(builder.parse(InputSource(StringReader(xml))).documentElement)
            assertEquals(expected, Kxml.parseDocument(xml), xml)
        }
    }

    @Test
    fun arbitraryInputNeverEscapesAsAnInternalParserException() {
        val random = Random(3291)
        val alphabet = "<>/!?-=:'\"&;#x012ab \t\n\r[]\u0000\uD800\uDC00"
        repeat(2_000) {
            val input = buildString {
                repeat(random.nextInt(100)) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            try {
                Kxml.parse(input)
            } catch (_: Kxml.ParseException) {
                // Syntax rejection is expected; index errors and other exceptions fail the test.
            }
        }
    }

    private fun elementSource(random: Random, depth: Int): String = buildString {
        val name = listOf("a", "card-label", "p:item", "имя")[random.nextInt(4)]
        append("<$name")
        if (random.nextBoolean()) append(" data-id = '${random.nextInt(100)} &amp; &#x1F600;\r\n\t &#9;'")
        if (random.nextBoolean()) {
            append("/>")
        } else {
            append('>')
            repeat(random.nextInt(1, 5)) {
                if (depth < 4 && random.nextBoolean()) {
                    append(elementSource(random, depth + 1))
                } else {
                    append(listOf(
                        "hello ", "&lt; &gt; &amp; &quot; &apos; ", "&#x1F600; &#10; &#13;",
                        "\r\n\r\n\t ", "<![CDATA[<raw> &data;\rtext]]>", "<!-- comment -->",
                        "<?instruction data?>", "שלום 😀 ",
                    )[random.nextInt(8)])
                }
            }
            append("</$name>")
        }
    }

    private fun fromDom(node: Node): Kxml.Element {
        val attrs = (0 until node.attributes.length).map { index ->
            val attr = node.attributes.item(index)
            Kxml.Tag.Attribute(attr.nodeName.substringAfter(':'), prefix(attr.nodeName), attr.nodeValue)
        }
        val content = mutableListOf<Pair<Kxml.Element?, String?>>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                content.add(null to text.toString())
                text.clear()
            }
        }
        for (index in 0 until node.childNodes.length) {
            val child = node.childNodes.item(index)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> { flush(); content.add(fromDom(child) to null) }
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(child.nodeValue)
            }
        }
        flush()
        return Kxml.Element(node.nodeName.substringAfter(':'), prefix(node.nodeName), attrs, content)
    }

    private fun prefix(name: String): String? = name.substringBefore(':', "").ifEmpty { null }
}
