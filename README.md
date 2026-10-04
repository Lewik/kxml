# Kxml

A small, dependency-free XML subset parser written in common Kotlin. It builds an
in-memory tree; it does not render HTML, evaluate expressions, validate application
schemas, fetch resources, or resolve namespace URIs.

## Use

```kotlin
import kxml.Kxml

val root = Kxml.parseDocument("""
    <user-card data-id="42">
        <label>Hello &amp; welcome</label>
    </user-card>
""".trimIndent())

val label = root.content.mapNotNull { it.first }.single()
println(label.getInnerText()) // Hello & welcome
```

- `parseDocument(text)` requires one root element. Only XML whitespace, comments,
  and processing instructions may surround it.
- `parse(text)` accepts a fragment: zero or more elements and text nodes. Leading,
  trailing, and mixed-content whitespace is preserved, not trimmed.
- Both accept an optional `Kxml.Limits` value.
- Content is an ordered `List<Pair<Element?, String?>>`. Each entry contains
  exactly one element or nonempty text string. Adjacent text is coalesced, including
  across ignored comments and processing instructions. `getInnerText()` returns
  **direct** text children, not descendant text.
- Attributes are `Kxml.Tag.Attribute` values. For `ui:label`, `name` is `label`
  and `namespace` is the lexical prefix `ui`, **not** a namespace URI. Namespace
  declarations are ordinary attributes; unbound prefixes are accepted.

## Supported syntax

- Matching, case-sensitive start/end tags and self-closing tags (`<item/>`).
- Unicode names, including kebab-case; at most one colon separating name/prefix.
- Single- and double-quoted attributes, whitespace around `=`, and duplicate
  qualified-name detection. Attribute values must always be quoted.
- Mixed text, comments, CDATA, and processing instructions. Comments and PIs are
  validated syntactically and discarded; CDATA becomes ordinary text.
- The five XML entities (`amp`, `lt`, `gt`, `apos`, `quot`) and decimal/hexadecimal
  character references, including supplementary Unicode characters.
- An optional initial BOM and XML 1.0 declaration. Input is already a Kotlin
  `String`; an encoding declaration is checked but does not decode bytes.
- XML line-end normalization (CRLF/CR to LF). Literal attribute tabs/newlines
  become spaces; whitespace introduced by character references is preserved.

Kxml implements this explicitly limited subset of
[XML 1.0](https://www.w3.org/TR/xml/), not a validating XML processor or an HTML5
parser. It does not repair malformed markup or implement HTML void elements,
boolean attributes, HTML named entities, namespace resolution, DTDs, custom
entities, or external entity loading. DTD/markup declarations are rejected.

Application-specific rules (allowed tags, attributes, bindings, components,
JSON schemas, and rendering) belong outside the parser. Braces such as
`{field-name.items[1]}` remain ordinary text.

## Errors and limits

Malformed input throws `Kxml.ParseException` (an `IllegalArgumentException`), with
`offset` (zero-based UTF-16 index), `line`, and `column` (one-based, CRLF-aware).
Positions refer to the original, unnormalized string.

Defaults:

| Limit | Value |
| --- | ---: |
| Input length (`String.length`) | 1,048,576 |
| Element nesting depth | 128 |
| Elements + coalesced text nodes | 100,000 |
| Attributes per element/declaration | 256 |

Limits are configurable per parse. Parsing uses an explicit stack, so raising the
depth limit does not introduce recursive parser calls. Input must still fit in
memory. Invalid XML characters, malformed UTF-16, unknown references, mismatched
tags, and truncated input are rejected. No filesystem/network access or entity
expansion is performed.

## Build and targets

Kotlin 2.4.10, Gradle 9.7.0, JDK 21 toolchain; JVM bytecode targets Java 17.
Targets: JVM (also usable from Android), JavaScript, Wasm/JS, iOS arm64 and arm64
simulator, macOS arm64. Browser and Node.js environments are configured for JS
and Wasm. The library has no runtime dependencies beyond Kotlin's standard library.

```sh
./gradlew jvmTest jsNodeTest wasmJsNodeTest
# On an Apple Silicon Mac with the native toolchain:
./gradlew macosArm64Test compileKotlinIosArm64 compileKotlinIosSimulatorArm64
```

The shared parser suite lives in `commonTest`. JVM tests additionally compare
supported documents against the JDK XML parser. `publishToMavenLocal` can be used
for local integration; no repository publication is performed by ordinary tests.
