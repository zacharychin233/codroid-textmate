import org.codroid.textmate.*
import org.codroid.textmate.utils.strArrCmp
import org.codroid.textmate.utils.strcmp
import kotlin.experimental.or
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ThemesTest {
    @Test
    fun `Theme matching gives higher priority to deeper matches`() {
        val theme = Theme.createFromRawTheme(
            source = RawTheme(
                settings = arrayOf(
                    RawThemeSetting(settings = Setting(foreground = "#100000", background = "#200000")),
                    RawThemeSetting(
                        scope = "punctuation.definition.string.begin.html", settings = Setting(foreground = "#300000")
                    ),
                    RawThemeSetting(
                        scope = "meta.tag punctuation.definition.string", settings = Setting(foreground = "#400000")
                    )
                )
            )
        )
        val actual = theme.match(ScopeStack.from("punctuation.definition.string.begin.html"))
        val actualForeground = actual?.foregroundId!!
        assertEquals("#300000", theme.getColorMap()[actualForeground])
    }

    @Test
    fun `Theme matching gives higher priority to parent matches 1`() {
        val theme = Theme.createFromRawTheme(
            source = RawTheme(
                settings = arrayOf(
                    RawThemeSetting(settings = Setting(foreground = "#100000", background = "#200000")),
                    RawThemeSetting(
                        scope = "c a", settings = Setting(foreground = "#300000")
                    ),
                    RawThemeSetting(
                        scope = "d a.b", settings = Setting(foreground = "#400000")
                    ),
                    RawThemeSetting(
                        scope = "a", settings = Setting(foreground = "#500000")
                    )
                )
            )
        )
        val map = theme.getColorMap()
        val id = theme.match(ScopeStack.from("d", "a.b"))?.foregroundId!!
        assertEquals("#400000", map[id])
    }

    @Test
    fun `Theme matching gives higher priority to parent matches 2`() {
        val theme = Theme.createFromRawTheme(
            source = RawTheme(
                settings = arrayOf(
                    RawThemeSetting(settings = Setting(foreground = "#100000", background = "#200000")),
                    RawThemeSetting(
                        scope = "meta.tag entity", settings = Setting(foreground = "#300000")
                    ),
                    RawThemeSetting(
                        scope = "meta.selector.css entity.name.tag", settings = Setting(foreground = "#400000")
                    ),
                    RawThemeSetting(
                        scope = "entity", settings = Setting(foreground = "#500000")
                    )
                )
            )
        )
        val result = theme.match(
            ScopeStack.from(
                "text.html.cshtml",
                "meta.tag.structure.any.html",
                "entity.name.tag.structure.any.html",
            )
        )
        val colorMap = theme.getColorMap()
        val id = result?.foregroundId
        assertEquals("#300000", colorMap[id])
    }

    @Test
    fun `Theme matching can match`() {
        val theme = Theme.createFromRawTheme(
            source = RawTheme(
                settings = arrayOf(
                    RawThemeSetting(settings = Setting(foreground = "#F8F8F2", background = "#272822")),
                    RawThemeSetting(
                        scope = "source, something", settings = Setting(background = "#100000")
                    ),
                    RawThemeSetting(
                        scopes = arrayOf("bar", "baz"), settings = Setting(background = "#200000")
                    ),
                    RawThemeSetting(
                        scope = "source.css selector bar", settings = Setting(fontStyle = "bold")
                    ),
                    RawThemeSetting(
                        scope = "constant", settings = Setting(fontStyle = "italic", foreground = "#300000")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric", settings = Setting(foreground = "#400000")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.hex", settings = Setting(fontStyle = "bold")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.oct", settings = Setting(fontStyle = "bold italic underline")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.dec", settings = Setting(fontStyle = "", foreground = "#500000")
                    ),
                    RawThemeSetting(
                        scope = "storage.object.bar", settings = Setting(fontStyle = "", foreground = "#600000")
                    ),
                )
            )
        )

        val map = theme.getColorMap()
        fun match(vararg paths: ScopeName): Setting? {
            val result = theme.match(ScopeStack.from(*paths)) ?: return null
            val obj = Setting(fontStyle = fontStyleToString(result.fontStyle))
            if (result.foregroundId != 0u) {
                obj.foreground = map[result.foregroundId]
            }
            if (result.backgroundId != 0u) {
                obj.background = map[result.backgroundId]
            }
            return obj
        }

        fun test(summary: String, actual: Setting?, expected: Setting) {
            print(summary.padEnd(16))
            assertEquals(expected, actual)
            println("√")
        }

        test("simpleMatch1", match("source"), Setting(background = "#100000", fontStyle = "not set"))
        test("simpleMatch2", match("source.ts"), Setting(background = "#100000", fontStyle = "not set"))
        test("simpleMatch3", match("source.tss"), Setting(background = "#100000", fontStyle = "not set"))
        test("simpleMatch4", match("something"), Setting(background = "#100000", fontStyle = "not set"))
        test("simpleMatch5", match("something.ts"), Setting(background = "#100000", fontStyle = "not set"))
        test("simpleMatch6", match("something.tss"), Setting(background = "#100000", fontStyle = "not set"))
        test("simpleMatch7", match("baz"), Setting(background = "#200000", fontStyle = "not set"))
        test("simpleMatch8", match("baz.ts"), Setting(background = "#200000", fontStyle = "not set"))
        test("simpleMatch9", match("baz.tss"), Setting(background = "#200000", fontStyle = "not set"))
        test("simpleMatch10", match("constant"), Setting(foreground = "#300000", fontStyle = "italic"))
        test("simpleMatch11", match("constant.string"), Setting(foreground = "#300000", fontStyle = "italic"))
        test("simpleMatch12", match("constant.hex"), Setting(foreground = "#300000", fontStyle = "italic"))
        test("simpleMatch13", match("constant.numeric"), Setting(foreground = "#400000", fontStyle = "italic"))
        test("simpleMatch14", match("constant.numeric.baz"), Setting(foreground = "#400000", fontStyle = "italic"))
        test("simpleMatch15", match("constant.numeric.hex"), Setting(foreground = "#400000", fontStyle = "bold"))
        test("simpleMatch16", match("constant.numeric.hex.baz"), Setting(foreground = "#400000", fontStyle = "bold"))
        test(
            "simpleMatch17",
            match("constant.numeric.oct"),
            Setting(foreground = "#400000", fontStyle = "italic bold underline")
        )
        test(
            "simpleMatch18",
            match("constant.numeric.oct.baz"),
            Setting(foreground = "#400000", fontStyle = "italic bold underline")
        )
        test("simpleMatch19", match("constant.numeric.dec"), Setting(foreground = "#500000", fontStyle = "none"))
        test("simpleMatch20", match("constant.numeric.dec.baz"), Setting(foreground = "#500000", fontStyle = "none"))
        test("simpleMatch21", match("storage.object.bar"), Setting(foreground = "#600000", fontStyle = "none"))
        test("simpleMatch22", match("storage.object.bar.baz"), Setting(foreground = "#600000", fontStyle = "none"))
        test("simpleMatch23", match("storage.object.bart"), Setting(fontStyle = "not set"))
        test("simpleMatch24", match("storage.object"), Setting(fontStyle = "not set"))
        test("simpleMatch25", match("storage"), Setting(fontStyle = "not set"))

        test("defaultMatch1", match(""), Setting(fontStyle = "not set"))
        test("defaultMatch2", match("bazz"), Setting(fontStyle = "not set"))
        test("defaultMatch3", match("asdfg"), Setting(fontStyle = "not set"))

        test("multiMatch1", match("bar"), Setting(background = "#200000", fontStyle = "not set"))
        test(
            "multiMatch2", match("source.css", "selector", "bar"), Setting(background = "#200000", fontStyle = "bold")
        )
    }

    @Test
    fun `Theme matching Microsoft vscode#23460`() {
        val theme = Theme.createFromRawTheme(
            source = RawTheme(
                settings = arrayOf(
                    RawThemeSetting(settings = Setting(foreground = "#aec2e0", background = "#14191f")),
                    RawThemeSetting(
                        name = "JSON String",
                        scope = "meta.structure.dictionary.json string.quoted.double.json",
                        settings = Setting(foreground = "#FF410D")
                    ),
                    RawThemeSetting(
                        scope = "meta.structure.dictionary.json string.quoted.double.json",
                        settings = Setting(foreground = "#ffffff")
                    ),
                    RawThemeSetting(
                        scope = "meta.structure.dictionary.value.json string.quoted.double.json",
                        settings = Setting(foreground = "#FF410D")
                    )
                )
            )
        )
        val path = ScopeStack.from(
            "source.json",
            "meta.structure.dictionary.json",
            "meta.structure.dictionary.value.json",
            "string.quoted.double.json"
        )
        val result = theme.match(path)
        val id = result?.foregroundId
        assertEquals("#FF410D", theme.getColorMap()[id])
    }

    @Test
    fun `Theme parsing can parse`() {
        val actual = parseTheme(
            RawTheme(
                settings = arrayOf(
                    RawThemeSetting(
                        settings = Setting(foreground = "#F8F8F2", background = "#272822")
                    ),
                    RawThemeSetting(
                        scope = "source, something", settings = Setting(background = "#100000")
                    ),
                    RawThemeSetting(
                        scopes = arrayOf("bar", "baz"), settings = Setting(background = "#010000")
                    ),
                    RawThemeSetting(
                        scope = "source.css selector bar", settings = Setting(fontStyle = "bold")
                    ),
                    RawThemeSetting(
                        scope = "constant", settings = Setting(fontStyle = "italic", foreground = "#ff0000")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric", settings = Setting(foreground = "#00ff00")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.hex", settings = Setting(fontStyle = "bold")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.oct", settings = Setting(fontStyle = "bold italic underline")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.bin", settings = Setting(fontStyle = "bold strikethrough")
                    ),
                    RawThemeSetting(
                        scope = "constant.numeric.dec", settings = Setting(fontStyle = "", foreground = "#0000ff")
                    ),
                    RawThemeSetting(
                        scope = "foo", settings = Setting(fontStyle = "", foreground = "#CFA")
                    ),
                )
            )
        )
        val expected = mutableListOf(
            ParsedThemeRule("", null, 0, FontStyleConsts.NotSet, "#F8F8F2", "#272822"),
            ParsedThemeRule("source", null, 1, FontStyleConsts.NotSet, null, "#100000"),
            ParsedThemeRule("something", null, 1, FontStyleConsts.NotSet, null, "#100000"),
            ParsedThemeRule("bar", null, 2, FontStyleConsts.NotSet, null, "#010000"),
            ParsedThemeRule("baz", null, 2, FontStyleConsts.NotSet, null, "#010000"),
            ParsedThemeRule("bar", arrayListOf("selector", "source.css"), 3, FontStyleConsts.Bold, null, null),
            ParsedThemeRule("constant", null, 4, FontStyleConsts.Italic, "#ff0000", null),
            ParsedThemeRule("constant.numeric", null, 5, FontStyleConsts.NotSet, "#00ff00", null),
            ParsedThemeRule("constant.numeric.hex", null, 6, FontStyleConsts.Bold, null, null),
            ParsedThemeRule(
                "constant.numeric.oct",
                null,
                7,
                FontStyleConsts.Bold or FontStyleConsts.Italic or FontStyleConsts.Underline,
                null,
                null
            ),
            ParsedThemeRule(
                "constant.numeric.bin", null, 8, FontStyleConsts.Bold or FontStyleConsts.Strikethrough, null, null
            ),
            ParsedThemeRule("constant.numeric.dec", null, 9, FontStyleConsts.None, "#0000ff", null),
            ParsedThemeRule("foo", null, 10, FontStyleConsts.None, "#CFA", null),
        )
        assertContentEquals(expected, actual)
    }

    @Test
    fun `Theme resolving strcmp works`() {
        val actual = arrayOf("bar", "z", "zu", "a", "ab", "")
        actual.sortWith(::strcmp)
        val expected = arrayOf("", "a", "ab", "bar", "z", "zu")
        assertContentEquals(expected, actual)
    }

    @Test
    fun `Theme resolving strArrCmp works`() {
        fun test(msg: String, a: Array<String>?, b: Array<String>?, expected: Int) {
            print(msg.padEnd(7))
            assertEquals(expected, strArrCmp(a, b))
            println("√")
        }
        test("001", null, null, 0);
        test("002", null, arrayOf(), -1);
        test("003", null, arrayOf("a"), -1);
        test("004", arrayOf(), null, 1);
        test("005", arrayOf("a"), null, 1);
        test("006", arrayOf(), arrayOf(), 0);
        test("007", arrayOf(), arrayOf("a"), -1);
        test("008", arrayOf("a"), arrayOf(), 1);
        test("009", arrayOf("a"), arrayOf("a"), 0);
        test("010", arrayOf("a", "b"), arrayOf("a"), 1);
        test("011", arrayOf("a"), arrayOf("a", "b"), -1);
        test("012", arrayOf("a", "b"), arrayOf("a", "b"), 0);
        test("013", arrayOf("a", "b"), arrayOf("a", "c"), -1);
        test("014", arrayOf("a", "c"), arrayOf("a", "b"), 1);
    }

    @Test
    fun `Theme resolving always has defaults`() {
        val actual = Theme.createFromParsedTheme(mutableListOf())
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#000000")
        val b = colorMap.getId("#ffffff")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet,
                    notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving respects incoming defaults 1`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    null, null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#000000")
        val b = colorMap.getId("#ffffff")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving respects incoming defaults 2`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.None,
                    null, null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#000000")
        val b = colorMap.getId("#ffffff")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving respects incoming defaults 3`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.Bold,
                    null, null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#000000")
        val b = colorMap.getId("#ffffff")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.Bold, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving respects incoming defaults 4`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    "#ff0000", null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#ff0000")
        val b = colorMap.getId("#ffffff")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving respects incoming defaults 5`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    null, "#ff0000"
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#000000")
        val b = colorMap.getId("#ff0000")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving can merge incoming defaults`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    null, "#ff0000"
                ),
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    "#00ff00", null
                ),
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.Bold,
                    null, null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#00ff00")
        val b = colorMap.getId("#ff0000")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.Bold, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving defaults are inherited`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    "#F8F8F2", "#272822"
                ),
                ParsedThemeRule(
                    "var", null, -1, FontStyleConsts.NotSet,
                    "#ff0000", null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#F8F8F2")
        val b = colorMap.getId("#272822")
        val c = colorMap.getId("#ff0000")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                ), arrayListOf(), TrieChildrenMap().apply {
                    this["var"] = ThemeTrieElement(
                        ThemeTrieElementRule(1, null, FontStyleConsts.NotSet, c, notSet)
                    )
                }
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving same rules get merged`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    "#F8F8F2", "#272822"
                ),
                ParsedThemeRule(
                    "var", null, 1, FontStyleConsts.Bold,
                    null, null
                ),
                ParsedThemeRule(
                    "var", null, 0, FontStyleConsts.NotSet,
                    "#ff0000", null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#F8F8F2")
        val b = colorMap.getId("#272822")
        val c = colorMap.getId("#ff0000")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                ), arrayListOf(), TrieChildrenMap().apply {
                    this["var"] = ThemeTrieElement(
                        ThemeTrieElementRule(1, null, FontStyleConsts.Bold, c, notSet)
                    )
                }
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving rules are inherited 1`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    "#F8F8F2", "#272822"
                ),
                ParsedThemeRule(
                    "var", null, -1, FontStyleConsts.Bold,
                    "#ff0000", null
                ),
                ParsedThemeRule(
                    "var.identifier", null, -1, FontStyleConsts.NotSet,
                    "#00ff00", null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#F8F8F2")
        val b = colorMap.getId("#272822")
        val c = colorMap.getId("#ff0000")
        val d = colorMap.getId("#00ff00")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                ), arrayListOf(), TrieChildrenMap().apply {
                    this["var"] = ThemeTrieElement(
                        ThemeTrieElementRule(1, null, FontStyleConsts.Bold, c, notSet),
                        arrayListOf(),
                        TrieChildrenMap().apply {
                            this["identifier"] = ThemeTrieElement(
                                ThemeTrieElementRule(2, null, FontStyleConsts.Bold, d, notSet)
                            )
                        }
                    )
                }
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving rules are inherited 2`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule("", null, -1, FontStyleConsts.NotSet, "#F8F8F2", "#272822"),
                ParsedThemeRule("var", null, -1, FontStyleConsts.Bold, "#ff0000", null),
                ParsedThemeRule("var.identifier", null, -1, FontStyleConsts.NotSet, "#00ff00", null),
                ParsedThemeRule("constant", null, 4, FontStyleConsts.Italic, "#100000", null),
                ParsedThemeRule("constant.numeric", null, 5, FontStyleConsts.NotSet, "#200000", null),
                ParsedThemeRule("constant.numeric.hex", null, 6, FontStyleConsts.Bold, null, null),
                ParsedThemeRule(
                    "constant.numeric.oct",
                    null,
                    7,
                    FontStyleConsts.Bold or FontStyleConsts.Italic or FontStyleConsts.Underline,
                    null,
                    null
                ),
                ParsedThemeRule("constant.numeric.dec", null, 8, FontStyleConsts.None, "#300000", null)
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#F8F8F2")
        val b = colorMap.getId("#272822")
        val c = colorMap.getId("#100000")
        val d = colorMap.getId("#200000")
        val e = colorMap.getId("#300000")
        val f = colorMap.getId("#ff0000")
        val g = colorMap.getId("#00ff00")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                ), arrayListOf(), TrieChildrenMap().apply {
                    this["var"] = ThemeTrieElement(
                        ThemeTrieElementRule(1, null, FontStyleConsts.Bold, f, notSet),
                        arrayListOf(),
                        TrieChildrenMap().apply {
                            this["identifier"] = ThemeTrieElement(
                                ThemeTrieElementRule(2, null, FontStyleConsts.Bold, g, notSet)
                            )
                        }
                    )
                    this["constant"] = ThemeTrieElement(
                        ThemeTrieElementRule(1, null, FontStyleConsts.Italic, c, notSet),
                        children = TrieChildrenMap().apply {
                            this["numeric"] = ThemeTrieElement(
                                ThemeTrieElementRule(2, null, FontStyleConsts.Italic, d, notSet),
                                children = TrieChildrenMap().apply {
                                    this["hex"] = ThemeTrieElement(
                                        ThemeTrieElementRule(
                                            3,
                                            null,
                                            FontStyleConsts.Bold,
                                            d,
                                            notSet
                                        )
                                    )
                                    this["oct"] = ThemeTrieElement(
                                        ThemeTrieElementRule(
                                            3,
                                            null,
                                            FontStyleConsts.Bold or FontStyleConsts.Italic or FontStyleConsts.Underline,
                                            d,
                                            notSet
                                        )
                                    )
                                    this["dec"] =
                                        ThemeTrieElement(ThemeTrieElementRule(3, null, FontStyleConsts.None, e, notSet))
                                })
                        })
                }
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving rules with parent scopes`() {
        val actual = Theme.createFromParsedTheme(
            mutableListOf(
                ParsedThemeRule(
                    "", null, -1, FontStyleConsts.NotSet,
                    "#F8F8F2", "#272822"
                ),
                ParsedThemeRule(
                    "var", null, -1, FontStyleConsts.Bold,
                    "#100000", null
                ),
                ParsedThemeRule(
                    "var.identifier", null, -1, FontStyleConsts.NotSet,
                    "#200000", null
                ),
                ParsedThemeRule(
                    "var", listOf("source.css"), 1, FontStyleConsts.Italic,
                    "#300000", null
                ),
                ParsedThemeRule(
                    "var", listOf("source.css"), 2, FontStyleConsts.Underline,
                    null, null
                )
            )
        )
        val colorMap = ColorMap()
        val notSet = 0u
        val a = colorMap.getId("#F8F8F2")
        val b = colorMap.getId("#272822")
        val c = colorMap.getId("#100000")
        val d = colorMap.getId("#300000")
        val e = colorMap.getId("#200000")
        val expected = Theme(
            colorMap,
            StyleAttributes(FontStyleConsts.None, a, b),
            ThemeTrieElement(
                ThemeTrieElementRule(
                    0, null, FontStyleConsts.NotSet, notSet, notSet
                ), arrayListOf(), TrieChildrenMap().apply {
                    this["var"] = ThemeTrieElement(
                        ThemeTrieElementRule(1, null, FontStyleConsts.Bold, c, notSet),
                        mutableListOf(
                            ThemeTrieElementRule(
                                1,
                                listOf("source.css"),
                                FontStyleConsts.Underline,
                                d,
                                notSet
                            )
                        ),
                        TrieChildrenMap().apply {
                            this["identifier"] = ThemeTrieElement(
                                ThemeTrieElementRule(2, null, FontStyleConsts.Bold, e, notSet),
                                mutableListOf(
                                    ThemeTrieElementRule(
                                        1,
                                        listOf("source.css"),
                                        FontStyleConsts.Underline,
                                        d,
                                        notSet
                                    )
                                )
                            )
                        }
                    )
                }
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `Theme resolving issue #38, ignores rules with invalid colors`() {
        val actual = parseTheme(
            RawTheme(
                settings = arrayOf(
                    RawThemeSetting(
                        settings = Setting(
                            background = "#222222",
                            foreground = "#cccccc"
                        )
                    ), RawThemeSetting(
                        name = "Variable",
                        scope = "variable",
                        settings = Setting(
                            fontStyle = ""
                        )
                    ), RawThemeSetting(
                        name = "Function argument",
                        scope = "variable.parameter",
                        settings = Setting(
                            fontStyle = "italic",
                            foreground = ""
                        )
                    ), RawThemeSetting(
                        name = "Library variable",
                        scope = "support.other.variable",
                        settings = Setting(
                            fontStyle = ""
                        )
                    ), RawThemeSetting(
                        name = "Function argument",
                        scope = "variable.other",
                        settings = Setting(
                            foreground = "",
                            fontStyle = "normal"
                        )
                    ), RawThemeSetting(
                        name = "Coffeescript Function argument",
                        scope = "variable.parameter.function.coffee",
                        settings = Setting(
                            foreground = "#F9D423",
                            fontStyle = "italic"
                        )
                    )
                )
            )
        )
        val expected = mutableListOf(
            ParsedThemeRule("", null, 0, FontStyleConsts.NotSet, "#cccccc", "#222222"),
            ParsedThemeRule("variable", null, 1, FontStyleConsts.None, null, null),
            ParsedThemeRule("variable.parameter", null, 2, FontStyleConsts.Italic, null, null),
            ParsedThemeRule("support.other.variable", null, 3, FontStyleConsts.None, null, null),
            ParsedThemeRule("variable.other", null, 4, FontStyleConsts.None, null, null),
            ParsedThemeRule("variable.parameter.function.coffee", null, 5, FontStyleConsts.Italic, "#F9D423", null),
        )

        assertContentEquals(expected, actual)
    }

    @Test
    fun `Theme resolving issue #35, Trailing comma in a tmTheme scope selector`() {
        val actual = parseTheme(
            RawTheme(
                settings = arrayOf(
                    RawThemeSetting(
                        settings = Setting(
                            background = "#25292C",
                            foreground = "#EFEFEF"
                        )
                    ), RawThemeSetting(
                        name = "CSS at-rule keyword control",
                        scope = arrayOf(
                            "meta.at-rule.return.scss,",
                            "meta.at-rule.return.scss punctuation.definition,",
                            "meta.at-rule.else.scss,",
                            "meta.at-rule.else.scss punctuation.definition,",
                            "meta.at-rule.if.scss,",
                            "meta.at-rule.if.scss punctuation.definition,"
                        ).joinToString("\n"),
                        settings = Setting(
                            foreground = "#CC7832"
                        )
                    )
                )
            )
        )
        val expected = mutableListOf(
            ParsedThemeRule("", null, 0, FontStyleConsts.NotSet, "#EFEFEF", "#25292C"),
            ParsedThemeRule("meta.at-rule.return.scss", null, 1, FontStyleConsts.NotSet, "#CC7832", null),
            ParsedThemeRule(
                "punctuation.definition",
                listOf("meta.at-rule.return.scss"),
                1,
                FontStyleConsts.NotSet,
                "#CC7832",
                null
            ),
            ParsedThemeRule("meta.at-rule.else.scss", null, 1, FontStyleConsts.NotSet, "#CC7832", null),
            ParsedThemeRule(
                "punctuation.definition",
                listOf("meta.at-rule.else.scss"),
                1,
                FontStyleConsts.NotSet,
                "#CC7832",
                null
            ),
            ParsedThemeRule("meta.at-rule.if.scss", null, 1, FontStyleConsts.NotSet, "#CC7832", null),
            ParsedThemeRule(
                "punctuation.definition",
                listOf("meta.at-rule.if.scss"),
                1,
                FontStyleConsts.NotSet,
                "#CC7832",
                null
            ),
        )

        assertContentEquals(expected, actual)
    }


}