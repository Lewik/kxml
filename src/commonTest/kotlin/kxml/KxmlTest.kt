package kxml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KxmlTest {
    @Test
    fun fragmentsPreserveTextOrderAndWhitespace() {
        val nodes = Kxml.parse(" before <a/> between <b>body</b> after ")
        assertEquals(5, nodes.size)
        assertEquals(listOf(" before ", " between ", " after "), nodes.mapNotNull { it.second })
        assertEquals(listOf("a", "b"), nodes.mapNotNull { it.first?.name })
        assertEquals("body", nodes[3].first!!.getInnerText())
        assertTrue(nodes.all { (element, text) -> (element == null) != (text == null) })
        assertEquals(emptyList(), Kxml.parse(""))
        assertEquals(listOf(null to "  \n"), Kxml.parse("  \n"))
        assertEquals(listOf(null to "trailing text"), Kxml.parse("trailing text"))
    }

    @Test
    fun emptyAndNestedElements() {
        val root = Kxml.parseDocument("<root><one/><two /><three></three><nest><leaf/></nest></root>")
        assertEquals(listOf("one", "two", "three", "nest"), root.content.map { it.first!!.name })
        assertEquals("leaf", root.content.last().first!!.content.single().first!!.name)
        assertTrue(root.content.take(3).all { it.first!!.content.isEmpty() })
    }

    @Test
    fun directTextExcludesDescendantText() {
        val root = Kxml.parseDocument("<root>before<b>nested</b>after</root>")
        assertEquals("beforeafter", root.getInnerText())
        assertEquals("nested", root.content[1].first!!.getInnerText())
    }

    @Test
    fun quotesWhitespaceKebabCaseAndBindingsAreOrdinaryMarkup() {
        val root = Kxml.parseDocument("""<user-card data-id = '42' title="a > b" empty='' value='{field-name.subField[1]}'/>""")
        assertEquals("user-card", root.name)
        assertEquals(listOf("data-id", "title", "empty", "value"), root.attrs.map { it.name })
        assertEquals(listOf("42", "a > b", "", "{field-name.subField[1]}"), root.attrs.map { it.value })
        assertNull(root.namespace)
    }

    @Test
    fun prefixesAreLexicalNotResolvedUris() {
        val root = Kxml.parseDocument("<ui:card xmlns:ui='urn:ui' ui:id='x' id='y'><ui:label/></ui:card>")
        assertEquals("card", root.name)
        assertEquals("ui", root.namespace)
        assertEquals(listOf("xmlns", "ui", null), root.attrs.map { it.namespace })
        assertEquals("ui", root.content.single().first!!.namespace)
        assertEquals("x", Kxml.parseDocument("<unbound:x/>").name)
    }

    @Test
    fun unicodeNamesAndSupplementaryCharacters() {
        val root = Kxml.parseDocument("<корень><שלום/><名 値='😀'/><😀/></корень>")
        assertEquals("корень", root.name)
        assertEquals(listOf("שלום", "名", "😀"), root.content.map { it.first!!.name })
        assertEquals("😀", root.content[1].first!!.attrs.single().value)
    }

    @Test
    fun predefinedAndNumericReferencesDecodeExactlyOnce() {
        val root = Kxml.parseDocument("<a v='&quot;&apos;&lt;&gt;&amp;'>&lt;&gt;&amp;&apos;&quot;&#65;&#x41;&#128512;&#x1F600;&amp;lt;</a>")
        assertEquals("\"'<>&", root.attrs.single().value)
        assertEquals("<>&'\"AA😀😀&lt;", root.getInnerText())
        assertEquals("\t\n\r", Kxml.parseDocument("<a>&#9;&#10;&#13;</a>").getInnerText())
        assertEquals(" ]]>", Kxml.parseDocument("<a> ]]&gt;</a>").getInnerText())
    }

    @Test
    fun commentsCdataAndInstructionsDoNotCreateFakeElements() {
        val root = Kxml.parseDocument("<?xml version='1.0'?><!--top--><?build ok?><a>one<!-- <x> & --><![CDATA[<x>&raw;]]><?pi ignored?>two</a><!--end-->")
        assertEquals(listOf(null to "one<x>&raw;two"), root.content)
        assertEquals("&not-an-entity;", Kxml.parseDocument("<a><![CDATA[&not-an-entity;]]></a>").getInnerText())
        assertEquals(emptyList(), Kxml.parse("<!--ignored--><?pi?>"))
        assertEquals(listOf(null to "raw"), Kxml.parse("<![CDATA[raw]]>"))
    }

    @Test
    fun xmlLineEndAndAttributeWhitespaceNormalization() {
        val root = Kxml.parseDocument("<a v='x\t y\r\nz\rq\n&#9;&#10;&#13;'>one\r\ntwo\rthree\nfour<![CDATA[\rfive\r\nsix]]></a>")
        assertEquals("x  y z q \t\n\r", root.attrs.single().value)
        assertEquals("one\ntwo\nthree\nfour\nfive\nsix", root.getInnerText())
    }

    @Test
    fun declarationsAndBom() {
        for (declaration in listOf(
            "<?xml version='1.0'?>",
            "<?xml version=\"1.0\" encoding='UTF-8'?>",
            "<?xml version='1.0' standalone='yes'?>",
            "<?xml version='1.0' encoding='UTF-16' standalone='no'?>",
        )) {
            assertEquals("a", Kxml.parseDocument("\uFEFF$declaration<a/>").name)
        }
        assertEquals("a", Kxml.parseDocument("\uFEFF<a/>").name)
    }

    @Test
    fun singleRootDocumentIsStricterThanFragments() {
        invalidDocuments("", " \n", "<!--only-->", "<a/><b/>", "x<a/>", "<a/>x", "&#32;<a/>", "<![CDATA[ ]]><a/>")
        assertEquals("a", Kxml.parseDocument(" \n<!--comment--><a/><?done?>\t").name)
        assertEquals(2, Kxml.parse("<a/><b/>").size)
    }

    @Test
    fun mismatchedMissingAndUnexpectedClosingTagsFail() {
        invalidFragments("<a></b>", "<a><b></a></b>", "</a>", "<a>", "<a><b/>", "<a></a></a>", "<a></A>", "<x:a></y:a>")
    }

    @Test
    fun malformedNamesAndTagsFail() {
        invalidFragments("<>", "<1a/>", "<-a/>", "< a/>", "<a:b:c/>", "<a:/>", "<:a/>", "<a! />", "<a/ >", "<a></a x>", "<a></ a>", "<a\u00A0/>")
    }

    @Test
    fun malformedAttributesFail() {
        invalidFragments(
            "<a b/>", "<a b=x/>", "<a b='x'c='y'/>", "<a b='x' b='y'/>",
            "<a p:b='x' p:b='y'/>", "<a b='<'/>", "<a b='unfinished>",
            "<a b = />", "<a = 'x'/>", "<a 1b='x'/>", "<a b:c:d='x'/>",
        )
    }

    @Test
    fun malformedCommentsCdataAndInstructionsFail() {
        invalidFragments("<!--", "<!-- bad -- comment -->", "<!--bad--->", "<![CDATA[", "<a>]]></a>", "<?pi", "<?pi!?>", "<??>")
    }

    @Test
    fun invalidDeclarationsFail() {
        invalidFragments(
            "<?xml?><a/>", "<?XML version='1.0'?><a/>", " <?xml version='1.0'?><a/>",
            "<a><?xml version='1.0'?></a>", "<?xml version='1.1'?><a/>",
            "<?xml encoding='UTF-8' version='1.0'?><a/>", "<?xml version='1.0' extra='x'?><a/>",
            "<?xml version='1.0' encoding='8bit'?><a/>", "<?xml version='1.0' standalone='maybe'?><a/>",
            "<?xml version='&#49;.0'?><a/>", "<?xml p:version='1.0'?><a/>",
        )
    }

    @Test
    fun noDoctypeOrCustomEntitiesEvenInternalOnes() {
        invalidFragments(
            "<!DOCTYPE a><a/>", "<!DOCTYPE a SYSTEM 'file:///etc/passwd'><a/>",
            "<!DOCTYPE a [<!ENTITY x 'expanded'>]><a>&x;</a>", "<!ENTITY x 'value'>",
            "<a>&custom;</a>", "<a>&nbsp;</a>",
        )
    }

    @Test
    fun invalidReferencesAndForbiddenCharactersFail() {
        val refs = listOf("&", "&amp", "&AMP;", "&;", "&#;", "&#x;", "&#X41;", "&#+65;", "&#-1;", "&#x+41;", "&#1;", "&#0;", "&#xD800;", "&#xFFFF;", "&#x110000;", "&#9999999999999999999999;")
        for (ref in refs) invalidFragments("<a>$ref</a>", "<a v='$ref'/>")
        for (char in listOf('\u0000', '\u0001', '\u000B', '\uD800', '\uDC00', '\uFFFE', '\uFFFF')) {
            invalidFragments("<a>$char</a>", "<a v='$char'/>", "<!--$char-->", "<?pi $char?>", "<![CDATA[$char]]>")
        }
    }

    @Test
    fun errorsPointIntoOriginalInputIncludingCrLf() {
        val input = "<root>\r\n  <child></wrong></root>"
        val error = assertFailsWith<Kxml.ParseException> { Kxml.parse(input) }
        assertEquals(input.indexOf("</wrong>"), error.offset)
        assertEquals(2, error.line)
        assertEquals(10, error.column)
        assertTrue(error.message!!.contains("child"))
        val eof = assertFailsWith<Kxml.ParseException> { Kxml.parse("<a>\n") }
        assertEquals(4, eof.offset)
        assertEquals(2, eof.line)
        assertEquals(1, eof.column)
    }

    @Test
    fun limitsHaveExactBoundaries() {
        assertEquals("a", Kxml.parseDocument("<a/>", Kxml.Limits(maxInputLength = 4)).name)
        assertFailsWith<Kxml.ParseException> { Kxml.parse("<a/>", Kxml.Limits(maxInputLength = 3)) }
        Kxml.parse("<a><b/></a>", Kxml.Limits(maxDepth = 2))
        assertFailsWith<Kxml.ParseException> { Kxml.parse("<a><b/></a>", Kxml.Limits(maxDepth = 1)) }
        Kxml.parse("<a>x<b/>y</a>", Kxml.Limits(maxNodes = 4))
        assertFailsWith<Kxml.ParseException> { Kxml.parse("<a>x<b/>y</a>", Kxml.Limits(maxNodes = 3)) }
        Kxml.parse("<a v='1'/>", Kxml.Limits(maxAttributesPerElement = 1))
        assertFailsWith<Kxml.ParseException> { Kxml.parse("<a v='1' w='2'/>", Kxml.Limits(maxAttributesPerElement = 1)) }
        Kxml.parse("<a/>", Kxml.Limits(maxAttributesPerElement = 0))
        assertFailsWith<IllegalArgumentException> { Kxml.Limits(maxDepth = 0) }
        assertFailsWith<IllegalArgumentException> { Kxml.Limits(maxNodes = -1) }
    }

    @Test
    fun deepInputIsBoundedWithoutUsingTheCallStack() {
        val depth = 2_000
        val source = "<a>".repeat(depth) + "x" + "</a>".repeat(depth)
        assertFailsWith<Kxml.ParseException> { Kxml.parse(source) }
        var root = Kxml.parseDocument(source, Kxml.Limits(maxDepth = depth))
        repeat(depth - 1) { root = root.content.single().first!! }
        assertEquals("x", root.getInnerText())
    }

    @Test
    fun truncatedInputAlwaysHasAParseErrorNotAnIndexError() {
        val source = "<root data-id='value'>text &amp;<![CDATA[raw]]><!--ok--><child/></root>"
        for (length in 0 until source.length) {
            assertFailsWith<Kxml.ParseException>("Prefix length $length") { Kxml.parseDocument(source.take(length)) }
        }
        assertEquals("root", Kxml.parseDocument(source).name)
    }

    private fun invalidFragments(vararg inputs: String) {
        for (input in inputs) assertFailsWith<Kxml.ParseException>(input) { Kxml.parse(input) }
    }

    private fun invalidDocuments(vararg inputs: String) {
        for (input in inputs) assertFailsWith<Kxml.ParseException>(input) { Kxml.parseDocument(input) }
    }
}
