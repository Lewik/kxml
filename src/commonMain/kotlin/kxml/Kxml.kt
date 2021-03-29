package kxml

import kxml.Kxml.Mode.*
import kxml.Kxml.Tag.TagType.*

class Kxml {

    data class Element(
        val name: String,
        val namespace: String?,
        val attrs: List<Tag.Attribute>,
        val content: List<Pair<Element?, String?>>,
    ) {
        fun getInnerText() = content.mapNotNull { it.second }.joinToString("")
    }

    data class Tag(
        val name: String,
        val namespace: String?,
        val type: TagType,
        val attrs: List<Attribute>,
    ) {
        enum class TagType {
            NOT_SPECIFIED,
            START,
            END,
            SINGLE,
            HEADER,
            ROOT
        }

        data class Attribute(
            val name: String,
            val namespace: String?,
            val value: String,
        )
    }

    enum class Mode {
        NONE,

        LEFT_ANGLE_BRACKET_SYMBOL,
        EQUALS_SYMBOL,
        SLASH_SYMBOL,

        TAG_NAME,
        TAG_NAMESPACE,
        TAG,
        ATTR_NAME,
        ATTR_NAMESPACE,
        ATTR_VALUE,
    }

    companion object {
        fun parse(text: String): List<Pair<Element?, String?>> {
            val iterator = text.trim().toList().listIterator()
            val iterator2 = parse1(iterator).iterator()

            return getContent(
                iterator2 = iterator2
            )
        }

        private fun getContent(
            iterator2: Iterator<Pair<String?, Tag?>>,
        ): List<Pair<Element?, String?>> {
            val content = mutableListOf<Pair<Element?, String?>>()

            while (iterator2.hasNext()) {
                val (innerText2, tag) = iterator2.next()
                when (tag?.type) {
                    NOT_SPECIFIED -> TODO()
                    START -> {
                        content.add(Element(
                            name = tag.name,
                            namespace = tag.namespace,
                            attrs = tag.attrs,
                            content = getContent(
                                iterator2 = iterator2
                            )
                        )
                                to null)
                    }
                    END -> return content
                    SINGLE,
                    HEADER,
                    -> {
                        content.add(Element(
                            name = tag.name,
                            namespace = tag.namespace,
                            attrs = tag.attrs,
                            content = emptyList()
                        ) to null)
                    }
                    ROOT -> TODO()
                    null -> {
                        content.add(null to innerText2)
                    }
                }
            }

            return content
        }


        private fun parse1(iterator: ListIterator<Char>): Sequence<Pair<String?, Tag?>> = sequence {

            var mode = NONE
            var tagType = NOT_SPECIFIED

            var tagFirstPart = ""
            var tagSecondPart = ""
            var attributeFirstPart = ""
            var attributeSecondPart = ""
            var attributeValue = ""

            var readyAttributes = mutableListOf<Tag.Attribute>()

            var body: String? = null

            var debugCharNumber = 0
            var debugRowNumber = 1

            while (iterator.hasNext()) {
                debugCharNumber++
                val char = iterator.next()
                val previousMode = mode
                val previousTagType = tagType
                when (char) {
                    '<' -> {
                        val check = when (mode) {
                            NONE -> {
                                yield(body to null)
                                body = null
                                mode = LEFT_ANGLE_BRACKET_SYMBOL
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_VALUE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                        }
                    }
                    '>' -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME,
                            TAG_NAMESPACE,
                            TAG,
                            -> {
                                if (tagType == NOT_SPECIFIED) {
                                    throw IllegalStateException("tagType == $tagType, mode: $mode, char: $char, at ${iterator.previousIndex()}")
                                }

                                yield(null to Tag(
                                    type = tagType,
                                    name = if (tagSecondPart == "") tagFirstPart else tagSecondPart,
                                    namespace = if (tagSecondPart == "") null else tagFirstPart,
                                    attrs = readyAttributes
                                ))

                                mode = NONE
                                tagType = NOT_SPECIFIED

                                tagFirstPart = ""
                                tagSecondPart = ""
                                attributeFirstPart = ""
                                attributeSecondPart = ""
                                attributeValue = ""


                                readyAttributes = mutableListOf()
                            }
                            ATTR_NAME,
                            ATTR_NAMESPACE,
                            -> {
                                readyAttributes.add(Tag.Attribute(
                                    name = if (attributeSecondPart == "") attributeFirstPart else attributeSecondPart,
                                    namespace = if (attributeSecondPart == "") null else attributeFirstPart,
                                    value = attributeValue.trim('"')
                                ))
                                attributeFirstPart = ""
                                attributeSecondPart = ""
                                attributeValue = ""
                                mode = TAG
                            }
                            ATTR_VALUE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                        }
                    }
                    ':' -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> {
                                mode = TAG_NAMESPACE
                            }
                            TAG_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAME -> {
                                mode = ATTR_NAMESPACE
                            }
                            ATTR_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_VALUE -> {
                                attributeValue += char
                            }
                        }
                    }
                    '\r',
                    '\n',
                    '\t',
                    ' ',
                    -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME,
                            TAG_NAMESPACE,
                            -> {
                                mode = TAG
                            }
                            TAG -> {
                                Unit
                            }
                            ATTR_NAME,
                            ATTR_NAMESPACE,
                            -> {
                                readyAttributes.add(Tag.Attribute(
                                    name = if (attributeSecondPart == "") attributeFirstPart else attributeSecondPart,
                                    namespace = if (attributeSecondPart == "") null else attributeFirstPart,
                                    value = attributeValue.trim('"')
                                ))
                                attributeFirstPart = ""
                                attributeSecondPart = ""
                                attributeValue = ""
                                mode = TAG
                            }
                            ATTR_VALUE -> {
                                attributeValue += char
                            }
                        }
                        if (char == '\n') {
                            debugCharNumber = 0
                            debugRowNumber++
                        }
                    }
//                '\'',
                    '"',
                    -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            EQUALS_SYMBOL -> {
                                attributeValue += char
                                mode = ATTR_VALUE
                            }
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_VALUE -> {
                                readyAttributes.add(Tag.Attribute(
                                    name = if (attributeSecondPart == "") attributeFirstPart else attributeSecondPart,
                                    namespace = if (attributeSecondPart == "") null else attributeFirstPart,
                                    value = attributeValue.trim('"')
                                ))
                                attributeFirstPart = ""
                                attributeSecondPart = ""
                                attributeValue = ""
                                mode = TAG
                            }
                        }
                    }
                    '=' -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAME,
                            ATTR_NAMESPACE,
                            -> {
                                mode = EQUALS_SYMBOL
                            }
                            ATTR_VALUE -> {
                                attributeValue += char
                            }
                        }
                    }
                    '?' -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> {
                                tagType = HEADER
                            }
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG -> {
                                Unit
                            }
                            ATTR_NAME,
                            ATTR_NAMESPACE,
                            ATTR_VALUE,
                            -> {
                                readyAttributes.add(Tag.Attribute(
                                    name = if (attributeSecondPart == "") attributeFirstPart else attributeSecondPart,
                                    namespace = if (attributeSecondPart == "") null else attributeFirstPart,
                                    value = attributeValue.trim('"')
                                ))
                                attributeFirstPart = ""
                                attributeSecondPart = ""
                                attributeValue = ""
                                mode = TAG
                            }
                        }
                    }
                    '/' -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> {
                                tagType = END
                            }
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG -> {
                                tagType = SINGLE
                            }
                            ATTR_NAME -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_NAMESPACE -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            ATTR_VALUE -> {
                                attributeValue += char
                            }
                        }
                    }
                    else -> {
                        val check = when (mode) {
                            NONE -> {
                                if (body == null) {
                                    body = ""
                                }
                                body += char
                            }
                            LEFT_ANGLE_BRACKET_SYMBOL -> {
                                tagFirstPart += char
                                if (tagType == NOT_SPECIFIED) {
                                    tagType = START
                                }
                                mode = TAG_NAME
                            }
                            EQUALS_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            SLASH_SYMBOL -> TODO("mode: $mode, char: $char, at $debugRowNumber:$debugCharNumber")
                            TAG_NAME -> {
                                tagFirstPart += char
                            }
                            TAG_NAMESPACE -> {
                                tagSecondPart += char
                            }
                            TAG -> {
                                attributeFirstPart += char
                                mode = ATTR_NAME
                            }
                            ATTR_NAME -> {
                                attributeFirstPart += char
                            }
                            ATTR_NAMESPACE -> {
                                attributeSecondPart += char
                            }
                            ATTR_VALUE -> {
                                attributeValue += char
                            }
                        }
                    }
                }

                //println("\"$char\" $previousMode -> $mode         $previousTagType -> $tagType")
            }
            if (mode != NONE) {
                throw IllegalStateException("Exit from file with mode $mode")
            }
        }
    }
}
