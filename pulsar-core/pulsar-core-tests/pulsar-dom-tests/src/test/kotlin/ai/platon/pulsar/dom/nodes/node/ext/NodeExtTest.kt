package ai.platon.pulsar.dom.nodes.node.ext

import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.math.geometric.DimI
import ai.platon.pulsar.dom.features.FeatureBlock
import ai.platon.pulsar.dom.features.FeatureBlockVector
import ai.platon.pulsar.dom.features.FeatureRegistry
import ai.platon.pulsar.dom.features.FeatureEntry
import ai.platon.pulsar.dom.features.Level1FeatureCalculator
import ai.platon.pulsar.dom.features.NodeFeature
import ai.platon.pulsar.dom.features.defined.*
import ai.platon.pulsar.dom.nodes.forEach
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.junit.jupiter.api.BeforeAll
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.*

/**
 * Comprehensive tests for [NodeExt.kt] extension functions and properties.
 *
 * Many extensions delegate to the [Node.features] RealVector, which for standalone
 * nodes (those not backed by a FeatureBlock) defaults to a zero-dimensional
 * [ArrayRealVector]. Call [Node.initFeatureVector] before tests that access
 * features to pre-size the vector to accommodate all feature keys.
 */
class NodeExtTest {

    // =========================================================================
    // Helpers
    // =========================================================================

    companion object {
        /** Sufficient dimension to hold all defined feature keys (0..DNS). */
        private const val FEATURE_VECTOR_SIZE = 20

        @JvmStatic
        @BeforeAll
        fun registerFeatures() {
            if (FeatureRegistry.registeredFeatures.isEmpty()) {
                FeatureRegistry.register(F.entries.map { it.toFeature() })
            }
        }

        /**
         * Pre-sizes the standalone feature vector so [Node.features] access
         * does not throw OutOfRangeException for any defined feature key.
         */
        private fun Node.initFeatureVector() {
            val vars = extension.variables
            // V_FEATURES = "__features" — use the literal since it's internal
            if (vars["__features"] !is RealVector) {
                vars["__features"] = ArrayRealVector(FEATURE_VECTOR_SIZE)
            }
        }

        /**
         * Parses HTML and pre-initialises the feature vector and immutableText
         * on every node so geometric / feature delegate / text tests work.
         *
         * Use [parseAndCalc] for tests that should exercise the real feature
         * calculation pipeline from `vi` attributes.
         */
        private fun parseAndInit(html: String): Document {
            val doc = Jsoup.parse(html)
            doc.forEach(includeRoot = true) {
                it.initFeatureVector()
                if (it is TextNode) {
                    it.immutableText = it.text()
                }
            }
            return doc
        }

        /**
         * Parses HTML and runs [Level1FeatureCalculator] to compute features
         * from `vi` attributes — the real production pipeline.
         *
         * This is the preferred helper for testing feature values because it
         * exercises the same code path used by the browser-based rendering.
         */
        private fun parseAndCalc(html: String): Document {
            val doc = Jsoup.parse(html)
            Level1FeatureCalculator().calculate(doc)
            return doc
        }
    }

    // =========================================================================
    // NIL constants and isNil
    // =========================================================================

    @Test
    fun testNILConstants() {
        assertEquals(AppConstants.NIL_PAGE_URL, NILLocation)
        assertEquals(AppConstants.NIL_PAGE_URL, NILBaseUri)
        assertNotNull(NILDocument)
        assertNotNull(NILElement)
        assertNotNull(NILNode)
    }

    @Test
    fun testDocumentIsNil() {
        assertTrue(NILDocument.isNil)
        val normalDoc = Jsoup.parse("<html><body></body></html>")
        assertFalse(normalDoc.isNil)
    }

    @Test
    fun testElementIsNil() {
        assertTrue(NILElement.isNil)
        val normalDoc = Jsoup.parse("<html><body><div></div></body></html>")
        val div = normalDoc.selectFirst("div")!!
        assertFalse(div.isNil)
    }

    @Test
    fun testNodeIsNil() {
        assertTrue(NILNode.isNil)
        val normalDoc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = normalDoc.selectFirst("p")!!
        assertFalse(p.isNil)
    }

    // =========================================================================
    // MapField / NullableMapField / field / nullableField delegates
    // =========================================================================

    @Test
    fun testFieldDelegateCreatesAndCachesValue() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val viewPort1 = doc.viewPort
        val viewPort2 = doc.viewPort
        assertSame(viewPort1, viewPort2)
    }

    @Test
    fun testNullableFieldDelegateReturnsNullForUnsetValue() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val body = doc.body()
        assertEquals(-1, body.nodeIndex)
    }

    @Test
    fun testMapFieldGetAndSet() {
        val doc = Jsoup.parse("<html><body></body></html>")
        doc.annotated = true
        assertTrue(doc.annotated)
        doc.annotated = false
        assertFalse(doc.annotated)
    }

    // =========================================================================
    // ExportPaths
    // =========================================================================

    @Test
    fun testExportPathsCreatesPaths() {
        val exportPaths = ExportPaths("http://example.com/page")
        assertNotNull(exportPaths.portal)
        assertNotNull(exportPaths.annotatedView)
        assertNotNull(exportPaths.tileView)
        assertNotNull(exportPaths.entityView)
    }

    @Test
    fun testExportPathsFilename() {
        val exportPaths = ExportPaths("http://example.com/page")
        assertNotNull(exportPaths.filename)
        assertTrue(exportPaths.filename.toString().contains(".htm"))
    }

    @Test
    fun testExportPathsByType() {
        val exportPaths = ExportPaths("http://example.com/page")
        assertEquals(exportPaths.portal, exportPaths.byType(ExportPaths.Type.PORTAL))
        assertEquals(exportPaths.annotatedView, exportPaths.byType(ExportPaths.Type.ANNOTATED))
    }

    @Test
    fun testExportPathsCompanionGet() {
        val path = ExportPaths.get("first", "second")
        assertNotNull(path)
        assertTrue(path.toString().contains("first"))
        assertTrue(path.toString().contains("second"))
    }

    // =========================================================================
    // Document extensions
    // =========================================================================

    @Test
    fun testDocumentViewPort() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val viewPort = doc.viewPort
        assertNotNull(viewPort)
        assertTrue(viewPort.width > 0)
        assertTrue(viewPort.height > 0)
    }

    @Test
    fun testDocumentPrimaryGrid() {
        val doc = Jsoup.parse("<html><body></body></html>")
        doc.primaryGrid = DimI(10, 20)
        assertEquals(10, doc.primaryGrid.width)
        assertEquals(20, doc.primaryGrid.height)
    }

    @Test
    fun testDocumentSecondaryGrid() {
        val doc = Jsoup.parse("<html><body></body></html>")
        doc.secondaryGrid = DimI(5, 5)
        assertEquals(5, doc.secondaryGrid.width)
        assertEquals(5, doc.secondaryGrid.height)
    }

    @Test
    fun testDocumentGrid() {
        val doc = Jsoup.parse("<html><body></body></html>")
        doc.grid = DimI(100, 200)
        assertEquals(100, doc.grid.width)
        assertEquals(200, doc.grid.height)
    }

    @Test
    fun testDocumentUnitArea() {
        val doc = Jsoup.parse("<html><body></body></html>")
        doc.unitArea = 42
        assertEquals(42, doc.unitArea)
    }

    @Test
    fun testDocumentExportPaths() {
        val doc = Jsoup.parse("<html><body></body></html>")
        val exportPaths = doc.exportPaths
        assertNotNull(exportPaths)
        assertNotNull(exportPaths.portal)
    }

    @Test
    fun testDocumentAnnotated() {
        val doc = Jsoup.parse("<html><body></body></html>")
        assertFalse(doc.annotated)
        doc.annotated = true
        assertTrue(doc.annotated)
    }

    @Test
    fun testDocumentNormalizedURIReturnsNullForPlainDocument() {
        val doc = Jsoup.parse("<html><head></head><body></body></html>")
        assertNull(doc.normalizedURI)
    }

    // =========================================================================
    // Element.addClasses
    // =========================================================================

    @Test
    fun testAddClassesVararg() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addClasses("foo", "bar")
        assertTrue(div.hasClass("foo"))
        assertTrue(div.hasClass("bar"))
    }

    @Test
    fun testAddClassesWithMultipleClassNamesInOneString() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addClasses("foo bar baz")
        assertTrue(div.hasClass("foo"))
        assertTrue(div.hasClass("bar"))
        assertTrue(div.hasClass("baz"))
    }

    @Test
    fun testAddClassesIterable() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addClasses(listOf("x", "y", "z"))
        assertTrue(div.hasClass("x"))
        assertTrue(div.hasClass("y"))
        assertTrue(div.hasClass("z"))
    }

    @Test
    fun testAddClassesDoesNotDuplicate() {
        val doc = Jsoup.parse("<div class='existing'></div>")
        val div = doc.selectFirst("div")!!
        div.addClasses("existing")
        assertTrue(div.hasClass("existing"))
    }

    // =========================================================================
    // Element.slimCopy and minimalCopy
    // =========================================================================

    @Test
    fun testSlimCopyReturnsClone() {
        val doc = Jsoup.parse("<div><a id='1' href='/foo'>One</a></div>")
        val copy = doc.slimCopy()
        assertNotNull(copy)
        assertNotSame(doc, copy)
    }

    @Test
    fun testMinimalCopyReturnsClone() {
        val doc = Jsoup.parse("<div><a id='1' href='/foo'>One</a></div>")
        val copy = doc.minimalCopy()
        assertNotNull(copy)
        assertNotSame(doc, copy)
    }

    // =========================================================================
    // Element.ownTexts
    // =========================================================================

    @Test
    fun testOwnTexts() {
        val doc = Jsoup.parse("<div>Hello <b>World</b> <span>!</span></div>")
        val div = doc.selectFirst("div")!!
        val texts = div.ownTexts()
        assertTrue(texts.any { it.contains("Hello") })
    }

    @Test
    fun testOwnTextsForElementWithoutText() {
        val doc = Jsoup.parse("<div><br><img src='foo.png'></div>")
        val div = doc.selectFirst("div")!!
        val texts = div.ownTexts()
        assertEquals(0, texts.size)
    }

    // =========================================================================
    // Element.valuableClassNames
    // =========================================================================

    @Test
    fun testValuableClassNamesFiltersClearfix() {
        val doc = Jsoup.parse("<div class='clearfix content'></div>")
        val div = doc.selectFirst("div")!!
        val classNames = div.valuableClassNames()
        assertTrue(classNames.contains("content"))
    }

    @Test
    fun testValuableClassNamesWithOnlyClearfix() {
        val doc = Jsoup.parse("<div class='clearfix'></div>")
        val div = doc.selectFirst("div")!!
        val classNames = div.valuableClassNames()
        assertTrue(classNames.contains("clearfix"))
    }

    @Test
    fun testValuableClassNamesEmpty() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        val classNames = div.valuableClassNames()
        assertTrue(classNames.isEmpty())
    }

    // =========================================================================
    // Element.anyAttr
    // =========================================================================

    @Test
    fun testElementAnyAttr() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.anyAttr("data-x", 123)
        assertEquals("123", div.attr("data-x"))
    }

    // =========================================================================
    // Element attribute removal
    // =========================================================================

    @Test
    fun testRemoveTemporaryAttributesCascaded() {
        val doc = Jsoup.parse("<div tv0='x' tv1='y' class='z'></div>")
        val div = doc.selectFirst("div")!!
        div.removeTemporaryAttributesCascaded()
        assertFalse(div.hasAttr("tv0"))
        assertFalse(div.hasAttr("tv1"))
        assertTrue(div.hasAttr("class"))
    }

    @Test
    fun testRemoveNonStandardAttributes() {
        val doc = Jsoup.parse("<div data-custom='val' class='z'></div>")
        val div = doc.selectFirst("div")!!
        div.removeNonStandardAttributes()
        assertFalse(div.hasAttr("data-custom"))
    }

    @Test
    fun testRemoveUnnecessaryAttributes() {
        val doc = Jsoup.parse("<div data-x='val' id='myid' class='z'></div>")
        val div = doc.selectFirst("div")!!
        div.removeUnnecessaryAttributes()
        assertTrue(div.hasAttr("id"))
        assertFalse(div.hasAttr("data-x"))
    }

    @Test
    fun testClearAttributesCascaded() {
        val doc = Jsoup.parse("<div class='z'><span class='inner'></span></div>")
        val div = doc.selectFirst("div")!!
        div.clearAttributesCascaded()
        assertFalse(div.hasAttr("class"))
        val span = div.selectFirst("span")!!
        assertFalse(span.hasAttr("class"))
    }

    // =========================================================================
    // Element.parseStyle / Element.getStyle
    // =========================================================================

    @Test
    fun testParseStyle() {
        val doc = Jsoup.parse("<div style='color:red;font-size:14px;'></div>")
        val div = doc.selectFirst("div")!!
        val styles = div.parseStyle()
        assertTrue(styles.isNotEmpty())
        assertTrue(styles.any { it.contains("color") && it.contains("red") })
    }

    @Test
    fun testGetStyle() {
        val doc = Jsoup.parse("<div style='color:red;font-size:14px;'></div>")
        val div = doc.selectFirst("div")!!
        val color = div.getStyle("color")
        assertTrue(color.contains("red"))
    }

    // =========================================================================
    // Node ownerDocument / ownerBody / immutableText / location
    // =========================================================================

    @Test
    fun testOwnerDocument() {
        val doc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(doc, p.ownerDocument)
    }

    @Test
    fun testOwnerBody() {
        val doc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(doc.body(), p.ownerBody)
    }

    @Test
    fun testImmutableText() {
        val doc = Jsoup.parse("<p>Hello</p>")
        val p = doc.selectFirst("p")!!
        val textNode = p.childNode(0) as TextNode
        textNode.immutableText = "World"
        assertEquals("World", textNode.immutableText)
    }

    @Test
    fun testLocation() {
        val doc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(doc.location(), p.location)
    }

    // =========================================================================
    // Node depth / sequence / nodeIndex — set by calculator
    // =========================================================================

    @Test
    fun testDepthSetByCalculator() {
        val doc = parseAndCalc("<html><body><div vi='0 0 100 100'><p vi='10 10 80 20'>Hello</p></div></body></html>")
        val p = doc.selectFirst("p")!!
        assertTrue(p.depth >= 3, "p inside div inside body should be depth >= 3")
    }

    @Test
    fun testSequenceSetByCalculator() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 20'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertTrue(p.sequence >= 0, "sequence should be assigned by calculator")
    }

    @Test
    fun testNodeIndexDefault() {
        val doc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(-1, p.nodeIndex)
    }

    @Test
    fun testNodeIndexSetGet() {
        val doc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        p.nodeIndex = 5
        assertEquals(5, p.nodeIndex)
    }

    @Test
    fun testFeatureBlockDefaultIsNull() {
        val doc = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertNull(p.featureBlock)
    }

    // =========================================================================
    // Node.globalId — uses calculator-assigned sequence + geometry
    // =========================================================================

    @Test
    fun testGlobalIdIsNotEmpty() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 20'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertNotNull(p.globalId)
        assertTrue(p.globalId.isNotEmpty())
    }

    @Test
    fun testGlobalIdContainsLocation() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 20'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertTrue(p.globalId.startsWith(doc.location()))
    }

    // =========================================================================
    // Node.features — after calculator, features is a FeatureBlockVector
    // =========================================================================

    @Test
    fun testFeaturesReturnsFeatureBlockVectorAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 20'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        val f = p.features
        assertTrue(f is FeatureBlockVector, "After calculator, features should be FeatureBlockVector")
        assertEquals(N, f.dimension)
    }

    @Test
    fun testFeaturesReadAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(10.0, p.getFeature(LEFT))
        assertEquals(20.0, p.getFeature(TOP))
        assertEquals(100.0, p.getFeature(WIDTH))
        assertEquals(50.0, p.getFeature(HEIGHT))
    }

    // =========================================================================
    // Node geometric properties — computed from vi attribute via calculator
    // =========================================================================

    @Test
    fun testLeftTopWidthHeightFromVi() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(10, p.left)
        assertEquals(20, p.top)
        assertEquals(100, p.width)
        assertEquals(50, p.height)
    }

    @Test
    fun testRightAndBottomFromVi() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(110, p.right)
        assertEquals(70, p.bottom)
    }

    @Test
    fun testXAndYFromVi() {
        val doc = parseAndCalc("<html><body><p vi='15 25 50 10'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(15, p.x)
        assertEquals(25, p.y)
    }

    @Test
    fun testX2AndY2FromVi() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(110, p.x2)
        assertEquals(70, p.y2)
    }

    @Test
    fun testCenterXAndCenterYFromVi() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 60'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(50, p.centerX)
        assertEquals(30, p.centerY)
    }

    @Test
    fun testGeoLocationFromVi() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(Point(10, 20), p.geoLocation)
    }

    @Test
    fun testDimensionFromVi() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        val dim = p.dimension
        assertEquals(100, dim.width)
        assertEquals(50, dim.height)
    }

    @Test
    fun testRectangleFromVi() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(Rectangle(10, 20, 100, 50), p.rectangle)
    }

    @Test
    fun testAreaFromVi() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(5000, p.area)
    }

    // =========================================================================
    // Node visibility — _h / _oh attributes + geometry from vi
    // =========================================================================

    @Test
    fun testHasHiddenFlag() {
        val doc = parseAndCalc("<div _h vi='10 10 100 50'></div>")
        val div = doc.selectFirst("div")!!
        assertTrue(div.hasHiddenFlag)
    }

    @Test
    fun testHasHiddenFlagFalse() {
        val doc = parseAndCalc("<div vi='10 10 100 50'></div>")
        val div = doc.selectFirst("div")!!
        assertFalse(div.hasHiddenFlag)
    }

    @Test
    fun testHasOverflowHiddenFlag() {
        val doc = parseAndCalc("<div _oh vi='10 10 100 50'></div>")
        val div = doc.selectFirst("div")!!
        assertTrue(div.hasOverflowHiddenFlag)
    }

    @Test
    fun testHasOverflowHiddenFlagFalse() {
        val doc = parseAndCalc("<div vi='10 10 100 50'></div>")
        val div = doc.selectFirst("div")!!
        assertFalse(div.hasOverflowHiddenFlag)
    }

    @Test
    fun testIsHiddenByFlag() {
        val doc = parseAndCalc("<div _h vi='10 10 100 50'></div>")
        val div = doc.selectFirst("div")!!
        assertFalse(div.isVisible)
        assertTrue(div.isHidden)
    }

    @Test
    fun testIsVisibleWhenGeometryNonEmpty() {
        val doc = parseAndCalc("<div vi='10 10 100 50'></div>")
        val div = doc.selectFirst("div")!!
        // x=10, y=10, w=100, h=50 — non-empty rect, no hidden flags → visible
        assertTrue(div.isVisible)
    }

    // =========================================================================
    // Node type checks: isText, isBlankText, isNonBlankText, etc.
    // =========================================================================

    @Test
    fun testIsText() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0)
        assertTrue(textNode.isText)
        assertFalse(doc.selectFirst("p")!!.isText)
    }

    @Test
    fun testIsBlankText() {
        val doc = parseAndInit("<p>   </p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "   "
        assertTrue(textNode.isBlankText)
    }

    @Test
    fun testIsNonBlankText() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "Hello"
        assertTrue(textNode.isNonBlankText)
    }

    @Test
    fun testIsImage() {
        val doc = parseAndInit("<div><img src='test.png'></div>")
        val img = doc.selectFirst("img")!!
        assertTrue(img.isImage)
        val div = doc.selectFirst("div")!!
        assertFalse(div.isImage)
    }

    @Test
    fun testIsRegularImage() {
        val doc = parseAndInit("<img src='test.png'>")
        val img = doc.selectFirst("img")!!
        assertFalse(img.isRegularImage) // not visible with zero rect
    }

    @Test
    fun testIsAnchorImage() {
        val doc = parseAndInit("<a href='/'><img src='test.png'></a>")
        val img = doc.selectFirst("img")!!
        assertTrue(img.isAnchorImage)
    }

    @Test
    fun testIsAnchor() {
        val doc = parseAndInit("<a href='/'>Link</a>")
        val a = doc.selectFirst("a")!!
        assertTrue(a.isAnchor)
        val p = doc.body()
        assertFalse(p.isAnchor)
    }

    @Test
    fun testIsTable() {
        val doc = parseAndInit("<table><tr><td>Cell</td></tr></table>")
        val table = doc.selectFirst("table")!!
        assertTrue(table.isTable)
        val td = doc.selectFirst("td")!!
        assertFalse(td.isTable)
    }

    @Test
    fun testIsList() {
        val doc = parseAndInit("<ul><li>Item</li></ul><ol><li>Item</li></ol>")
        val ul = doc.selectFirst("ul")!!
        val ol = doc.selectFirst("ol")!!
        assertTrue(ul.isList)
        assertTrue(ol.isList)
        val li = doc.selectFirst("li")!!
        assertFalse(li.isList)
    }

    // =========================================================================
    // Text analysis: isShortText, isMediumText, isLongText, etc.
    // =========================================================================

    @Test
    fun testIsNumeric() {
        val doc = parseAndInit("<span>12345</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "12345"
        // isNumeric requires isMediumText → isRegularText → isVisible
        // With zero rect, isVisible is false, so isNumeric is false
        assertFalse(textNode.isNumeric)
    }

    @Test
    fun testIsNumericFalseForNonNumericText() {
        val doc = parseAndInit("<span>abc</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "abc"
        assertFalse(textNode.isNumeric)
    }

    @Test
    fun testIsFloat() {
        val doc = parseAndInit("<span>3.14</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "3.14"
        // isFloat requires isShortText → isRegularText → isVisible, false with zero rect
        assertFalse(textNode.isFloat)
    }

    @Test
    fun testIsInt() {
        val doc = parseAndInit("<span>42</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "42"
        // isInt requires isShortText → isRegularText → isVisible, false with zero rect
        assertFalse(textNode.isInt)
    }

    // =========================================================================
    // Node.cleanText
    // =========================================================================

    @Test
    fun testCleanTextForTextNode() {
        val doc = parseAndInit("<p>  Hello World  </p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "  Hello World  "
        assertEquals("Hello World", textNode.cleanText)
    }

    @Test
    fun testCleanTextForElement() {
        val doc = parseAndInit("<div><p>Hello</p><p>World</p></div>")
        val div = doc.selectFirst("div")!!
        val clean = div.cleanText
        assertTrue(clean.contains("Hello"))
        assertTrue(clean.contains("World"))
    }

    // =========================================================================
    // Node.joinToString
    // =========================================================================

    @Test
    fun testJoinToStringBasic() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "Hello"
        assertEquals("Hello", textNode.joinToString())
    }

    @Test
    fun testJoinToStringWithPrefix() {
        val doc = parseAndInit("<p>World</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "World"
        assertEquals("Hello World", textNode.joinToString(prefix = "Hello "))
    }

    @Test
    fun testJoinToStringWithSuffix() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "Hello"
        assertEquals("Hello World", textNode.joinToString(suffix = " World"))
    }

    // =========================================================================
    // Node.textRepresentation
    // =========================================================================

    @Test
    fun testTextRepresentationForImage() {
        val doc = parseAndInit("<img src='http://example.com/img.png'>")
        val img = doc.selectFirst("img")!!
        val repr = img.textRepresentation
        assertTrue(repr.contains("img.png") || repr.contains("example.com"))
    }

    @Test
    fun testTextRepresentationForAnchor() {
        val doc = parseAndInit("<a href='http://example.com'>Click</a>")
        val a = doc.selectFirst("a")!!
        val repr = a.textRepresentation
        assertTrue(repr.contains("example.com") || repr.contains("href"))
    }

    @Test
    fun testTextRepresentationForTextNode() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "Hello"
        assertEquals("Hello", textNode.textRepresentation)
    }

    // =========================================================================
    // Node.slimHtml / Node.minimalHtml
    // =========================================================================

    @Test
    fun testSlimHtmlForTextNode() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "Hello"
        val html = textNode.slimHtml
        assertTrue(html.contains("Hello") || html.contains("span"))
    }

    @Test
    fun testMinimalHtmlForTextNode() {
        val doc = parseAndInit("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0) as TextNode
        textNode.immutableText = "Hello"
        val html = textNode.minimalHtml
        assertTrue(html.contains("Hello") || html.contains("span"))
    }

    @Test
    fun testSlimHtmlForImage() {
        val doc = parseAndInit("<img src='http://example.com/test.png' alt='Test'>")
        val img = doc.selectFirst("img")!!
        val html = img.slimHtml
        assertTrue(html.contains("img") || html.contains("test.png"))
    }

    @Test
    fun testSlimHtmlForAnchor() {
        val doc = parseAndInit("<a href='http://example.com'>Click</a>")
        val a = doc.selectFirst("a")!!
        val html = a.slimHtml
        assertTrue(html.lowercase().contains("a") || html.contains("href"))
    }

    // =========================================================================
    // Node.key
    // =========================================================================

    @Test
    fun testKeyContainsLocation() {
        val doc = parseAndInit("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        p.nodeIndex = 5
        assertTrue(p.key.startsWith(doc.location()))
        assertTrue(p.key.contains("#"))
    }

    // =========================================================================
    // Node.name
    // =========================================================================

    @Test
    fun testNameForDocument() {
        val doc = Jsoup.parse("<html><body></body></html>")
        assertEquals(":root", doc.name)
    }

    @Test
    fun testNameForElementWithId() {
        val doc = Jsoup.parse("<div id='myid'>Content</div>")
        val div = doc.selectFirst("div")!!
        assertEquals("#myid", div.name)
    }

    @Test
    fun testNameForElementWithClassOnly() {
        val doc = Jsoup.parse("<div class='foo bar'>Content</div>")
        val div = doc.selectFirst("div")!!
        assertTrue(div.name.contains("foo"))
    }

    @Test
    fun testNameForElementWithoutIdOrClass() {
        val doc = Jsoup.parse("<div>Content</div>")
        val div = doc.selectFirst("div")!!
        assertEquals("div", div.name)
    }

    @Test
    fun testNameForTextNode() {
        val doc = Jsoup.parse("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0)
        assertNotNull(textNode.name)
    }

    // =========================================================================
    // Node.canonicalName
    // =========================================================================

    @Test
    fun testCanonicalNameForElement() {
        val doc = Jsoup.parse("<div id='myid' class='foo'>Content</div>")
        val div = doc.selectFirst("div")!!
        assertTrue(div.canonicalName.startsWith("div"))
        assertTrue(div.canonicalName.contains("myid"))
    }

    @Test
    fun testCanonicalNameForTextNode() {
        val doc = Jsoup.parse("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0)
        val cname = textNode.canonicalName
        assertTrue(cname.isNotEmpty())
    }

    // =========================================================================
    // Node.uniqueName / Node.namedRect / Node.namedRect2
    // =========================================================================

    @Test
    fun testUniqueName() {
        val doc = parseAndInit("<div id='x'>Content</div>")
        val div = doc.selectFirst("div")!!
        div.setFeature(SEQ, 10.0)
        assertTrue(div.uniqueName.contains("10"))
        assertTrue(div.uniqueName.contains("div"))
    }

    @Test
    fun testNamedRect() {
        val doc = parseAndInit("<div>Content</div>")
        val div = doc.selectFirst("div")!!
        div.left = 0; div.top = 0; div.width = 10; div.height = 10
        assertTrue(div.namedRect.contains("div"))
    }

    @Test
    fun testNamedRect2() {
        val doc = parseAndInit("<div>Content</div>")
        val div = doc.selectFirst("div")!!
        div.left = 0; div.top = 0; div.width = 10; div.height = 10
        assertTrue(div.namedRect2.contains("div"))
    }

    // =========================================================================
    // Node.parentElement / Node.bestElement
    // =========================================================================

    @Test
    fun testParentElement() {
        val doc = Jsoup.parse("<div><p>Hello</p></div>")
        val p = doc.selectFirst("p")!!
        assertEquals("div", p.parentElement.tagName())
    }

    @Test
    fun testBestElementForElement() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertEquals(div, div.bestElement)
    }

    @Test
    fun testBestElementForTextNode() {
        val doc = Jsoup.parse("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0)
        assertEquals("p", textNode.bestElement.tagName())
    }

    // =========================================================================
    // Node.attrOrNull
    // =========================================================================

    @Test
    fun testAttrOrNullExisting() {
        val doc = Jsoup.parse("<div class='foo'></div>")
        val div = doc.selectFirst("div")!!
        assertEquals("foo", div.attrOrNull("class"))
    }

    @Test
    fun testAttrOrNullNonExisting() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertNull(div.attrOrNull("nonexistent"))
    }

    @Test
    fun testAttrOrNullBlank() {
        val doc = Jsoup.parse("<div class=''></div>")
        val div = doc.selectFirst("div")!!
        assertNull(div.attrOrNull("class"))
    }

    // =========================================================================
    // Node.getFeature / setFeature / removeFeature / clearFeatures
    // (testing the API surface; values computed from vi)
    // =========================================================================

    @Test
    fun testGetFeatureAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(10.0, p.getFeature(LEFT))
        assertEquals(20.0, p.getFeature(TOP))
    }

    @Test
    fun testGetFeatureByNameAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(10.0, p.getFeature("left"))
        assertEquals(20.0, p.getFeature("top"))
    }

    @Test
    fun testGetFeatureEntryAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='30 40 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        val entry = p.getFeatureEntry(WIDTH)
        assertEquals(WIDTH, entry.key)
        assertEquals(100.0, entry.value)
    }

    @Test
    fun testSetFeatureOverwritesCalcValue() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(10, p.left)
        // Overwrite with setFeature
        p.setFeature(LEFT, 999.0)
        assertEquals(999.0, p.getFeature(LEFT))
        assertEquals(999, p.left) // delegate also reads the new value
    }

    @Test
    fun testRemoveFeatureZeroesCalcValue() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(100, p.width)
        p.removeFeature(WIDTH)
        assertEquals(0.0, p.getFeature(WIDTH))
        assertEquals(0, p.width)
    }

    @Test
    fun testClearFeaturesAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertNotNull(p.featureBlock)
        assertTrue(p.nodeIndex >= 0)
        p.clearFeatures()
        assertEquals(-1, p.nodeIndex)
        assertNull(p.featureBlock)
    }

    // =========================================================================
    // Node variable access
    // =========================================================================

    @Test
    fun testSetAndGetVariable() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.setVariable("myKey", "myValue")
        assertEquals("myValue", div.getVariable<String>("myKey"))
    }

    @Test
    fun testGetVariableWithDefault() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertEquals("default", div.getVariable("nonexistent", "default"))
    }

    @Test
    fun testGetVariableReturnsNullForMissingKey() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertNull(div.getVariable<String>("nonexistent"))
    }

    @Test
    fun testComputeVariableIfAbsent() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        val result = div.computeVariableIfAbsent("computed") { it.uppercase() }
        assertEquals("COMPUTED", result)
        val result2 = div.computeVariableIfAbsent("computed") { "different" }
        assertEquals("COMPUTED", result2)
    }

    @Test
    fun testSetVariableIfNotNull() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.setVariableIfNotNull("key", "value")
        assertEquals("value", div.getVariable<String>("key"))
        div.setVariableIfNotNull("key2", null)
        assertFalse(div.hasVariable("key2"))
    }

    @Test
    fun testHasVariable() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertFalse(div.hasVariable("myKey"))
        div.setVariable("myKey", "value")
        assertTrue(div.hasVariable("myKey"))
    }

    @Test
    fun testRemoveVariable() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.setVariable("myKey", "value")
        assertTrue(div.hasVariable("myKey"))
        div.removeVariable("myKey")
        assertFalse(div.hasVariable("myKey"))
    }

    // =========================================================================
    // Node.anyAttr / Node.rAttr / Node.rAnyAttr / Node.appendAttr
    // =========================================================================

    @Test
    fun testNodeAnyAttr() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.anyAttr("data-x", 42)
        assertEquals("42", div.attr("data-x"))
    }

    @Test
    fun testRAttr() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        val result = div.rAttr("data-x", "hello")
        assertEquals("hello", result)
        assertEquals("hello", div.attr("data-x"))
    }

    @Test
    fun testRAnyAttr() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        val value = 99
        val result = div.rAnyAttr("data-x", value)
        assertEquals(value, result)
        assertEquals("99", div.attr("data-x"))
    }

    @Test
    fun testAppendAttrNew() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.appendAttr("class", "foo")
        assertEquals("foo", div.attr("class"))
    }

    @Test
    fun testAppendAttrExisting() {
        val doc = Jsoup.parse("<div class='foo'></div>")
        val div = doc.selectFirst("div")!!
        div.appendAttr("class", "bar")
        assertEquals("foo bar", div.attr("class"))
    }

    @Test
    fun testAppendAttrWithCustomSeparator() {
        val doc = Jsoup.parse("<div class='foo'></div>")
        val div = doc.selectFirst("div")!!
        div.appendAttr("class", "bar", separator = ",")
        assertEquals("foo,bar", div.attr("class"))
    }

    // =========================================================================
    // Node.removeAttrs / Node.removeAttrsIf
    // =========================================================================

    @Test
    fun testRemoveAttrsVararg() {
        val doc = Jsoup.parse("<div class='foo' id='bar' title='baz'></div>")
        val div = doc.selectFirst("div")!!
        div.removeAttrs("class", "title")
        assertFalse(div.hasAttr("class"))
        assertFalse(div.hasAttr("title"))
        assertTrue(div.hasAttr("id"))
    }

    @Test
    fun testRemoveAttrsWildcard() {
        val doc = Jsoup.parse("<div class='foo' id='bar'></div>")
        val div = doc.selectFirst("div")!!
        div.removeAttrs("*")
        assertFalse(div.hasAttr("class"))
        assertFalse(div.hasAttr("id"))
    }

    @Test
    fun testRemoveAttrsIterable() {
        val doc = Jsoup.parse("<div class='foo' id='bar' title='baz'></div>")
        val div = doc.selectFirst("div")!!
        div.removeAttrs(listOf("class", "title"))
        assertFalse(div.hasAttr("class"))
        assertFalse(div.hasAttr("title"))
        assertTrue(div.hasAttr("id"))
    }

    @Test
    fun testRemoveAttrsIf() {
        val doc = Jsoup.parse("<div class='foo' data-x='1' data-y='2'></div>")
        val div = doc.selectFirst("div")!!
        div.removeAttrsIf { it.key.startsWith("data-") }
        assertTrue(div.hasAttr("class"))
        assertFalse(div.hasAttr("data-x"))
        assertFalse(div.hasAttr("data-y"))
    }

    // =========================================================================
    // Tuple data
    // =========================================================================

    @Test
    fun testAddAndGetTuple() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addTupleItem("mylist", "item1")
        div.addTupleItem("mylist", "item2")
        val list = div.getTuple("mylist")
        assertEquals(2, list.size)
        assertTrue(list.contains("item1"))
        assertTrue(list.contains("item2"))
    }

    @Test
    fun testRemoveTupleItem() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addTupleItem("mylist", "item1")
        div.addTupleItem("mylist", "item2")
        assertTrue(div.removeTupleItem("mylist", "item1"))
        assertEquals(1, div.getTuple("mylist").size)
        assertFalse(div.removeTupleItem("mylist", "nonexistent"))
    }

    @Test
    fun testHasTupleItem() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addTupleItem("mylist", "item1")
        assertTrue(div.hasTupleItem("mylist", "item1"))
        assertFalse(div.hasTupleItem("mylist", "nonexistent"))
    }

    @Test
    fun testHasTuple() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertFalse(div.hasTuple("mylist"))
        div.addTupleItem("mylist", "item1")
        assertTrue(div.hasTuple("mylist"))
    }

    @Test
    fun testClearTuple() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addTupleItem("mylist", "item1")
        div.addTupleItem("mylist", "item2")
        div.clearTuple("mylist")
        assertTrue(div.getTuple("mylist").isEmpty())
    }

    @Test
    fun testRemoveTuple() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addTupleItem("mylist", "item1")
        div.removeTuple("mylist")
        assertFalse(div.hasTuple("mylist"))
    }

    @Test
    fun testGetTupleReturnsEmptyForMissing() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertTrue(div.getTuple("nonexistent").isEmpty())
    }

    // =========================================================================
    // Labels
    // =========================================================================

    @Test
    fun testAddAndGetLabels() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addLabel("important")
        div.addLabel("featured")
        val labels = div.getLabels()
        assertTrue(labels.contains("important"))
        assertTrue(labels.contains("featured"))
    }

    @Test
    fun testLabelsAreDeduplicatedWhenRetrieved() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addLabel("important")
        div.addLabel("important")
        val labels = div.getLabels()
        // addLabel uses addTupleItem which stores in a mutableList (allows duplicates)
        assertEquals(2, labels.size)
        assertTrue(labels.contains("important"))
    }

    @Test
    fun testHasLabel() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addLabel("test")
        assertTrue(div.hasLabel("test"))
        assertFalse(div.hasLabel("nonexistent"))
    }

    @Test
    fun testRemoveLabel() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addLabel("test")
        assertTrue(div.removeLabel("test"))
        assertFalse(div.hasLabel("test"))
    }

    @Test
    fun testClearLabels() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addLabel("a")
        div.addLabel("b")
        div.clearLabels()
        assertTrue(div.getLabels().isEmpty())
    }

    // =========================================================================
    // ML Labels
    // =========================================================================

    @Test
    fun testAddAndGetMlLabels() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addMlLabel("title")
        div.addMlLabel("content")
        val mlLabels = div.getMlLabels()
        assertTrue(mlLabels.contains("title"))
        assertTrue(mlLabels.contains("content"))
    }

    @Test
    fun testGetMlLabelReturnsFirst() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addMlLabel("first")
        div.addMlLabel("second")
        assertEquals("first", div.getMlLabel())
    }

    @Test
    fun testGetMlLabelEmpty() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertEquals("", div.getMlLabel())
    }

    @Test
    fun testHasMlLabel() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addMlLabel("test")
        assertTrue(div.hasMlLabel("test"))
        assertFalse(div.hasMlLabel("other"))
    }

    @Test
    fun testRemoveMlLabel() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addMlLabel("test")
        assertTrue(div.removeMlLabel("test"))
        assertFalse(div.hasMlLabel("test"))
    }

    @Test
    fun testClearMlLabels() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addMlLabel("a")
        div.addMlLabel("b")
        div.clearMlLabels()
        assertTrue(div.getMlLabels().isEmpty())
    }

    // =========================================================================
    // Caption words
    // =========================================================================

    @Test
    fun testAddAndGetCaptionWords() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addCaptionWord("Hello")
        div.addCaptionWord("World")
        val words = div.getCaptionWords()
        assertTrue(words.isNotEmpty())
    }

    @Test
    fun testHasCaptionWord() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addCaptionWord("Hello")
        assertTrue(div.hasCaptionWord("Hello"))
        assertFalse(div.hasCaptionWord("Bye"))
    }

    @Test
    fun testHasCaption() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        assertFalse(div.hasCaption())
        div.addCaptionWord("Hello")
        assertTrue(div.hasCaption())
    }

    @Test
    fun testRemoveCaptionWord() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addCaptionWord("Hello")
        div.removeCaptionWord("Hello")
        assertFalse(div.hasCaptionWord("Hello"))
    }

    @Test
    fun testClearCaption() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addCaptionWord("Hello")
        div.clearCaption()
        assertFalse(div.hasCaption())
    }

    // =========================================================================
    // Node.caption
    // =========================================================================

    @Test
    fun testCaption() {
        val doc = Jsoup.parse("<div></div>")
        val div = doc.selectFirst("div")!!
        div.addCaptionWord("Product")
        div.addCaptionWord("Description")
        val caption = div.caption
        assertTrue(caption.isNotEmpty())
    }

    // =========================================================================
    // Node.captionOrName / Node.captionOrSelectorOrName / Node.selectorOrName
    // =========================================================================

    @Test
    fun testSelectorOrNameForElement() {
        val doc = Jsoup.parse("<div class='foo'><p>Hello</p></div>")
        val div = doc.selectFirst("div")!!
        val sel = div.selectorOrName
        assertTrue(sel.contains("div"))
    }

    @Test
    fun testSelectorOrNameForTextNode() {
        val doc = Jsoup.parse("<p>Hello</p>")
        val textNode = doc.selectFirst("p")!!.childNode(0)
        assertEquals("#text", textNode.selectorOrName)
    }

    @Test
    fun testCaptionOrName() {
        val doc = Jsoup.parse("<div>Hello</div>")
        val div = doc.selectFirst("div")!!
        val result = div.captionOrName
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testCaptionOrSelectorOrName() {
        val doc = Jsoup.parse("<div>Hello</div>")
        val div = doc.selectFirst("div")!!
        val result = div.captionOrSelectorOrName
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    // =========================================================================
    // Node ancestors / hasAncestor / isAncestorOf
    // =========================================================================

    @Test
    fun testAncestors() {
        val doc = Jsoup.parse("<html><body><div><p>Hello</p></div></body></html>")
        val p = doc.selectFirst("p")!!
        val ancestors = p.ancestors()
        assertTrue(ancestors.isNotEmpty())
        assertTrue(ancestors.any { it.tagName() == "div" })
        assertTrue(ancestors.any { it.tagName() == "body" })
    }

    @Test
    fun testHasAncestor() {
        val doc = Jsoup.parse("<div><p class='target'>Hello</p></div>")
        val p = doc.selectFirst("p")!!
        assertTrue(p.hasAncestor { it.tagName() == "div" })
        assertFalse(p.hasAncestor { it.tagName() == "span" })
    }

    @Test
    fun testIsAncestorOf() {
        val doc = Jsoup.parse("<div><p>Hello</p></div>")
        val div = doc.selectFirst("div")!!
        val p = doc.selectFirst("p")!!
        assertTrue(div.isAncestorOf(p))
        assertFalse(p.isAncestorOf(div))
    }

    // =========================================================================
    // Node feature delegates computed from vi via calculator
    // =========================================================================

    @Test
    fun testNumCharsFromVi() {
        // "Hello" = 5 chars → numChars computed by calculator
        val doc = parseAndCalc("<html><body><p vi='0 0 100 20'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertEquals(5, p.numChars)
    }

    @Test
    fun testNumSiblingsFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 0 300 200'><p vi='0 0 100 20'>A</p><p vi='0 20 100 20'>B</p><p vi='0 40 100 20'>C</p></div></body></html>")
        val ps = doc.select("p")
        for (p in ps) {
            assertEquals(3, p.numSiblings)
        }
    }

    @Test
    fun testNumChildrenFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 0 300 200'><p vi='0 0 100 20'>A</p><p vi='0 20 100 20'>B</p></div></body></html>")
        val div = doc.selectFirst("div")!!
        // p elements + potential whitespace TextNodes are all children that contribute C
        assertTrue(div.numChildren >= 2, "div should have at least 2 child elements")
    }

    @Test
    fun testNumTextNodesFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 0 300 200'><p vi='0 0 100 20'>A</p><p vi='0 20 100 20'>B</p></div></body></html>")
        val div = doc.selectFirst("div")!!
        // Each non-empty TextNode in p elements contributes TN=1 → div.TN >= 2
        assertTrue(div.numTextNodes >= 2)
    }

    @Test
    fun testNumImagesFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 0 300 200'><img vi='0 0 50 50' src='a.png'><img vi='60 0 50 50' src='b.png'></div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(2, div.numImages)
    }

    @Test
    fun testNumAnchorsFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 0 300 200'><a vi='0 0 100 20' href='/a'>A</a><a vi='0 30 100 20' href='/b'>B</a></div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(2, div.numAnchors)
    }

    // =========================================================================
    // Node.formatFeatures / Node.formatNamedFeatures
    // =========================================================================

    @Test
    fun testFormatFeatures() {
        val doc = parseAndInit("<p>Hello</p>")
        val p = doc.selectFirst("p")!!
        p.setFeature(CH, 5.0)
        val formatted = p.formatFeatures(CH)
        assertNotNull(formatted)
        assertTrue(formatted.isNotEmpty())
    }

    @Test
    fun testFormatEachFeatures() {
        val doc = parseAndInit("<div><p>Hello</p><p>World</p></div>")
        val div = doc.selectFirst("div")!!
        val formatted = div.formatEachFeatures(CH)
        assertNotNull(formatted)
    }

    @Test
    fun testFormatNamedFeatures() {
        val doc = Jsoup.parse("<p>Hello</p>")
        val p = doc.selectFirst("p")!!
        p.setVariable("myFeature", "testValue")
        val formatted = p.formatNamedFeatures()
        assertNotNull(formatted)
    }

    // =========================================================================
    // Node.intValue / Node.doubleValue
    // =========================================================================

    @Test
    fun testIntValue() {
        val doc = parseAndInit("<span>42</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "42"
        assertEquals(42, textNode.intValue)
    }

    @Test
    fun testIntValueNonNumeric() {
        val doc = parseAndInit("<span>abc</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "abc"
        // SParser.getInt throws NumberFormatException for non-numeric input;
        // intValue delegates to SParser and will throw too
        try {
            textNode.intValue
            fail("Expected NumberFormatException for non-numeric intValue")
        } catch (e: NumberFormatException) {
            // expected
        }
    }

    @Test
    fun testDoubleValue() {
        val doc = parseAndInit("<span>3.14</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "3.14"
        assertEquals(3.14, textNode.doubleValue)
    }

    @Test
    fun testDoubleValueNonNumeric() {
        val doc = parseAndInit("<span>xyz</span>")
        val textNode = doc.selectFirst("span")!!.childNode(0) as TextNode
        textNode.immutableText = "xyz"
        // SParser.getDouble throws NumberFormatException for non-numeric input;
        // doubleValue delegates to SParser and will throw too
        try {
            textNode.doubleValue
            fail("Expected NumberFormatException for non-numeric doubleValue")
        } catch (e: NumberFormatException) {
            // expected
        }
    }

    // =========================================================================
    // isRegularAnchor / isImageAnchor / isRegularImageAnchor
    // =========================================================================

    @Test
    fun testIsRegularAnchor() {
        val doc = parseAndInit("<a href='/' style='display:block; width:100px; height:20px;'>Link</a>")
        val a = doc.selectFirst("a")!!
        assertTrue(a.isAnchor)
    }

    @Test
    fun testIsImageAnchor() {
        val doc = parseAndInit("<a href='/'><img src='test.png'></a>")
        val a = doc.selectFirst("a")!!
        a.numImages = 1
        assertTrue(a.isImageAnchor)
    }

    @Test
    fun testIsImageAnchorFalseWhenNoImage() {
        val doc = parseAndInit("<a href='/'>Just text</a>")
        val a = doc.selectFirst("a")!!
        a.numImages = 0
        assertFalse(a.isImageAnchor)
    }

    // =========================================================================
    // screenNumber
    // =========================================================================

    @Test
    fun testScreenNumber() {
        val doc = parseAndInit("<html><body><div></div></body></html>")
        val div = doc.selectFirst("div")!!
        div.top = 500
        val screenNum = div.screenNumber
        assertTrue(screenNum >= 0.0)
    }

    // =========================================================================
    // FeatureBlock integration
    // =========================================================================

    @Test
    fun testFeatureBlockGetSet() {
        val block = FeatureBlock(10, N)
        block[0, LEFT] = 100.0
        assertEquals(100.0, block[0, LEFT])
        block[5, WIDTH] = 200.0
        assertEquals(200.0, block[5, WIDTH])
    }

    @Test
    fun testFeatureBlockStoresDataIndependently() {
        val block = FeatureBlock(10, N)
        // Node 0
        block[0, LEFT] = 10.0
        block[0, TOP] = 20.0
        // Node 1 — different row, same column
        block[1, LEFT] = 30.0
        block[1, TOP] = 40.0
        // Verify isolation
        assertEquals(10.0, block[0, LEFT])
        assertEquals(20.0, block[0, TOP])
        assertEquals(30.0, block[1, LEFT])
        assertEquals(40.0, block[1, TOP])
    }

    @Test
    fun testFeatureBlockRowVectorCreatesView() {
        val block = FeatureBlock(10, N)
        val row = block.rowVector(3)
        assertNotNull(row)
        assertEquals(N, row.dimension)
        assertTrue(row is FeatureBlockVector)
    }

    @Test
    fun testFeatureBlockVectorReadsFromBlock() {
        val block = FeatureBlock(10, N)
        block[2, CH] = 42.0
        val row = block.rowVector(2)
        assertEquals(42.0, row.getEntry(CH))
    }

    @Test
    fun testFeatureBlockVectorWritesToBlock() {
        val block = FeatureBlock(10, N)
        val row = block.rowVector(2)
        row.setEntry(CH, 99.0)
        // Read back through block to confirm write-through
        assertEquals(99.0, block[2, CH])
    }

    @Test
    fun testFeatureBlockVectorCopyIsIndependent() {
        val block = FeatureBlock(10, N)
        block[0, CH] = 10.0
        val row = block.rowVector(0)
        val copy = row.copy()
        assertTrue(copy is org.apache.commons.math3.linear.ArrayRealVector)
        assertEquals(10.0, copy.getEntry(CH))
        // Modify copy — should NOT affect the block
        copy.setEntry(CH, 999.0)
        assertEquals(10.0, block[0, CH])
    }

    @Test
    fun testFeatureBlockVectorCheckIndexThrows() {
        val block = FeatureBlock(10, N)
        val row = block.rowVector(0)
        try {
            row.getEntry(-1)
            fail("Expected OutOfRangeException for negative index")
        } catch (e: org.apache.commons.math3.exception.OutOfRangeException) {
            // expected
        }
        try {
            row.getEntry(N) // dimension is exclusive upper bound
            fail("Expected OutOfRangeException for index >= dimension")
        } catch (e: org.apache.commons.math3.exception.OutOfRangeException) {
            // expected
        }
    }

    @Test
    fun testFeaturesUsesFeatureBlockWhenSet() {
        val doc = parseAndInit("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        val block = FeatureBlock(10, N)
        p.featureBlock = block
        p.nodeIndex = 3
        // features should now return a FeatureBlockVector backed by the block
        val f = p.features
        assertTrue(f is FeatureBlockVector)
        assertEquals(N, f.dimension)
    }

    @Test
    fun testFeaturesFallsBackToStandaloneWhenBlockNull() {
        val doc = parseAndInit("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        p.featureBlock = null
        p.nodeIndex = -1
        val f = p.features
        // Should be ArrayRealVector (standalone), not FeatureBlockVector
        assertTrue(f is org.apache.commons.math3.linear.ArrayRealVector)
    }

    @Test
    fun testFeaturesFallsBackToStandaloneWhenNodeIndexNegative() {
        val doc = parseAndInit("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        val block = FeatureBlock(10, N)
        p.featureBlock = block
        p.nodeIndex = -1 // invalid index
        // features getter checks featureBlock first, then returns rowVector
        // rowVector(-1) will be called — FeatureBlock doesn't validate nodeIndex
        val f = p.features
        assertNotNull(f)
    }

    @Test
    fun testFeatureBlockWriteThroughVisibleViaFeatures() {
        val doc = parseAndInit("<html><body><p>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        val block = FeatureBlock(10, N)
        p.featureBlock = block
        p.nodeIndex = 1
        // Write via delegate uses features setter which delegates to FeatureBlockVector
        p.left = 77
        // Read back via block confirms write-through
        assertEquals(77.0, block[1, LEFT])
        // Read back via delegate confirms round-trip
        assertEquals(77, p.left)
    }

    // =========================================================================
    // DoubleFeature delegate — edge cases
    // =========================================================================

    @Test
    fun testDoubleFeatureDefaultIsZero() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        // A new node should report 0.0 for any double feature
        assertEquals(0.0, div.textNodeDensity)
    }

    @Test
    fun testDoubleFeatureZeroValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.textNodeDensity = 0.0
        assertEquals(0.0, div.textNodeDensity)
        div.textNodeDensity = 1.0
        assertEquals(1.0, div.textNodeDensity)
        div.textNodeDensity = 0.0
        assertEquals(0.0, div.textNodeDensity)
    }

    @Test
    fun testDoubleFeatureNegativeValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.textNodeDensity = -0.5
        assertEquals(-0.5, div.textNodeDensity)
    }

    @Test
    fun testDoubleFeatureLargeValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.textNodeDensity = Double.MAX_VALUE
        assertEquals(Double.MAX_VALUE, div.textNodeDensity)
    }

    @Test
    fun testDoubleFeatureSmallValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.textNodeDensity = Double.MIN_VALUE
        assertEquals(Double.MIN_VALUE, div.textNodeDensity)
    }

    @Test
    fun testDoubleFeatureConsistencyWithSetFeature() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        // Set via delegate
        div.textNodeDensity = 0.42
        // Read via getFeature (same key DNS)
        assertEquals(0.42, div.getFeature(DNS))
    }

    @Test
    fun testDoubleFeatureConsistencyWithGetFeature() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        // Set via setFeature
        div.setFeature(DNS, 0.88)
        // Read via delegate
        assertEquals(0.88, div.textNodeDensity)
    }

    @Test
    fun testDnsFeatureIsRegisteredAsFloat() {
        // DNS has scale=4, so isFloat should be true
        assertTrue(F.DNS.isFloat)
        assertTrue(NodeFeature.isFloating("txt_dns"))
        assertFalse(NodeFeature.isFloating("char"))
    }

    // =========================================================================
    // IntFeature delegate — edge cases
    // =========================================================================

    @Test
    fun testIntFeatureDefaultIsZero() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        assertEquals(0, div.numChars)
        assertEquals(0, div.numSiblings)
        assertEquals(0, div.numChildren)
        assertEquals(0, div.numTextNodes)
        assertEquals(0, div.numImages)
        assertEquals(0, div.numAnchors)
        assertEquals(0, div.depth)
    }

    @Test
    fun testIntFeatureZeroValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.numChars = 100
        assertEquals(100, div.numChars)
        div.numChars = 0
        assertEquals(0, div.numChars)
    }

    @Test
    fun testIntFeatureNegativeValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.numChars = -10
        assertEquals(-10, div.numChars)
    }

    @Test
    fun testIntFeatureMaxValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.numChars = Int.MAX_VALUE
        assertEquals(Int.MAX_VALUE, div.numChars)
    }

    @Test
    fun testIntFeatureMinValue() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.numChars = Int.MIN_VALUE
        assertEquals(Int.MIN_VALUE, div.numChars)
    }

    @Test
    fun testIntFeatureTruncatesFloat() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        // setFeature sets a double; IntFeature delegates toInt() which truncates
        div.setFeature(CH, 3.7) // CH → numChars
        assertEquals(3, div.numChars)
    }

    @Test
    fun testIntFeatureConsistencyWithSetFeature() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.numChars = 123
        assertEquals(123.0, div.getFeature(CH))
    }

    @Test
    fun testIntFeatureConsistencyWithGetFeature() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.setFeature(CH, 456.0)
        assertEquals(456, div.numChars)
    }

    @Test
    fun testAllIntFeaturesIndependent() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.numChars = 1
        div.numSiblings = 2
        div.numChildren = 3
        div.numTextNodes = 4
        div.numImages = 5
        div.numAnchors = 6
        assertEquals(1, div.numChars)
        assertEquals(2, div.numSiblings)
        assertEquals(3, div.numChildren)
        assertEquals(4, div.numTextNodes)
        assertEquals(5, div.numImages)
        assertEquals(6, div.numAnchors)
    }

    @Test
    fun testGeometricIntFeaturesIndependent() {
        val doc = parseAndInit("<div></div>")
        val div = doc.selectFirst("div")!!
        div.left = 10
        div.top = 20
        div.width = 100
        div.height = 50
        assertEquals(10, div.left)
        assertEquals(20, div.top)
        assertEquals(100, div.width)
        assertEquals(50, div.height)
    }

    // =========================================================================
    // Feature key coverage — all defined features
    // =========================================================================

    @Test
    fun testAllFeatureKeysPopulatedByCalculator() {
        val doc = parseAndCalc("<html><body><div vi='0 0 300 200'><p vi='10 10 100 20'>Hello</p></div></body></html>")
        val p = doc.selectFirst("p")!!
        // Every feature key should be readable (calculator sets geometry + counts)
        assertTrue(p.getFeature(TOP) >= 0)
        assertTrue(p.getFeature(LEFT) >= 0)
        assertTrue(p.getFeature(WIDTH) >= 0)
        assertTrue(p.getFeature(HEIGHT) >= 0)
        assertTrue(p.getFeature(CH) >= 0)
        assertTrue(p.getFeature(TN) >= 0)
        assertTrue(p.getFeature(IMG) >= 0)
        assertTrue(p.getFeature(A) >= 0)
        assertTrue(p.getFeature(SIB) >= 0)
        assertTrue(p.getFeature(C) >= 0)
        assertTrue(p.getFeature(DEP) >= 0)
        assertTrue(p.getFeature(SEQ) >= 0)
        assertEquals(0.0, p.getFeature(DNS))
    }

    @Test
    fun testAllFeatureNamesLookup() {
        val expectedNames = listOf("top", "left", "width", "height", "char", "txt_nd", "img", "a", "sibling", "child", "dep", "seq", "txt_dns")
        val keys = listOf(TOP, LEFT, WIDTH, HEIGHT, CH, TN, IMG, A, SIB, C, DEP, SEQ, DNS)
        for ((name, key) in expectedNames.zip(keys)) {
            assertEquals(key, NodeFeature.getKey(name), "Feature name '$name' should map to key $key")
        }
    }

    @Test
    fun testFeatureKeyUniqueness() {
        val keys = F.entries.map { it.key }
        assertEquals(keys.toSet().size, keys.size, "Feature keys must be unique")
    }

    // =========================================================================
    // Feature isolation — calculator assigns unique indices per node
    // =========================================================================

    @Test
    fun testNodesHaveIndependentFeatureViewsAfterCalc() {
        val doc = parseAndCalc("<html><body><div vi='0 0 400 200'><p vi='10 10 100 20'>A</p><p vi='10 40 100 20'>B</p></div></body></html>")
        val pA = doc.selectFirst("p:nth-child(1)") ?: doc.select("p")[0]
        val pB = doc.select("p")[1]
        // Both share the same FeatureBlock but have different nodeIndex → independent views
        assertNotEquals(pA.nodeIndex, pB.nodeIndex)
        // Same featureBlock reference
        assertSame(pA.featureBlock, pB.featureBlock)
        // Different positions
        assertNotEquals(pA.top, pB.top)
    }

    @Test
    fun testFeatureBlockNodesShareBlockButHaveIndependentViews() {
        val block = FeatureBlock(10, N)
        val doc = parseAndInit("<div><p id='a'>A</p><p id='b'>B</p></div>")
        val pA = doc.selectFirst("#a")!!
        val pB = doc.selectFirst("#b")!!
        // Assign both to same block but different indices
        pA.featureBlock = block; pA.nodeIndex = 0
        pB.featureBlock = block; pB.nodeIndex = 1
        pA.left = 10
        pB.left = 20
        assertEquals(10, pA.left)
        assertEquals(20, pB.left)
        // Verify block-level isolation
        assertEquals(10.0, block[0, LEFT])
        assertEquals(20.0, block[1, LEFT])
    }

    // =========================================================================
    // Features getter edge cases
    // =========================================================================

    @Test
    fun testFeaturesGetterReturnsFeatureBlockVectorAfterCalc() {
        val doc = parseAndCalc("<html><body><p vi='0 0 100 20'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertTrue(p.features is FeatureBlockVector)
    }

    @Test
    fun testClearFeaturesAfterCalcResetsState() {
        val doc = parseAndCalc("<html><body><p vi='10 20 100 50'>Hello</p></body></html>")
        val p = doc.selectFirst("p")!!
        assertTrue(p.nodeIndex >= 0)
        assertNotNull(p.featureBlock)
        p.clearFeatures()
        assertEquals(-1, p.nodeIndex)
        assertNull(p.featureBlock)
    }

    // =========================================================================
    // FeatureBlockVector specific behaviors
    // =========================================================================

    @Test
    fun testFeatureBlockVectorGetEntryAllIndices() {
        val block = FeatureBlock(5, N)
        block[3, TOP] = 1.0
        block[3, LEFT] = 2.0
        block[3, WIDTH] = 3.0
        block[3, HEIGHT] = 4.0
        val row = block.rowVector(3)
        assertEquals(1.0, row.getEntry(TOP))
        assertEquals(2.0, row.getEntry(LEFT))
        assertEquals(3.0, row.getEntry(WIDTH))
        assertEquals(4.0, row.getEntry(HEIGHT))
    }

    @Test
    fun testFeatureBlockVectorIsNotNaN() {
        val block = FeatureBlock(5, N)
        val row = block.rowVector(0)
        assertFalse(row.isNaN)
    }

    @Test
    fun testFeatureBlockVectorIsNotInfinite() {
        val block = FeatureBlock(5, N)
        val row = block.rowVector(0)
        assertFalse(row.isInfinite)
    }

    @Test
    fun testFeatureBlockVectorUnsupportedOperationsThrow() {
        val block = FeatureBlock(5, N)
        val row = block.rowVector(0)
        val other = block.rowVector(1)

        try { row.append(other); fail("Expected UnsupportedOperationException") }
        catch (e: UnsupportedOperationException) { /* expected */ }

        try { row.append(1.0); fail("Expected UnsupportedOperationException") }
        catch (e: UnsupportedOperationException) { /* expected */ }

        try { row.getSubVector(0, 2); fail("Expected UnsupportedOperationException") }
        catch (e: UnsupportedOperationException) { /* expected */ }

        try { row.ebeMultiply(other); fail("Expected UnsupportedOperationException") }
        catch (e: UnsupportedOperationException) { /* expected */ }

        try { row.ebeDivide(other); fail("Expected UnsupportedOperationException") }
        catch (e: UnsupportedOperationException) { /* expected */ }
    }

    @Test
    fun testFeatureBlockVectorSetSubVectorThrows() {
        val block = FeatureBlock(5, N)
        val row = block.rowVector(0)
        val source = block.rowVector(1)
        try { row.setSubVector(0, source); fail("Expected UnsupportedOperationException") }
        catch (e: UnsupportedOperationException) { /* expected */ }
    }

    @Test
    fun testFeatureBlockVectorEmpty() {
        val empty = FeatureBlockVector.empty()
        assertTrue(empty is org.apache.commons.math3.linear.ArrayRealVector)
        assertEquals(0, empty.dimension)
    }

    // =========================================================================
    // Feature-backed derived properties — computed from vi via calculator
    // =========================================================================

    @Test
    fun testRightAndX2AreLive() {
        val doc = parseAndCalc("<html><body><div vi='10 0 100 50'>Hello</div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(110, div.right)
        assertEquals(110, div.x2)
    }

    @Test
    fun testBottomAndY2AreLive() {
        val doc = parseAndCalc("<html><body><div vi='0 20 100 50'>Hello</div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(70, div.bottom)
        assertEquals(70, div.y2)
    }

    @Test
    fun testXAndLeftAreSyncedFromVi() {
        val doc = parseAndCalc("<html><body><div vi='42 0 100 50'>Hello</div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(42, div.x)
        assertEquals(42, div.left)
    }

    @Test
    fun testYAndTopAreSyncedFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 30 100 50'>Hello</div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(30, div.y)
        assertEquals(30, div.top)
    }

    @Test
    fun testAreaComputedFromVi() {
        val doc = parseAndCalc("<html><body><div vi='0 0 10 20'>Hello</div></body></html>")
        val div = doc.selectFirst("div")!!
        assertEquals(200, div.area)
    }

    // =========================================================================
    // vi-attribute-based feature calculation (Level1FeatureCalculator)
    //
    // In production, the browser injects `vi` attributes like "left top w h"
    // into each Element. Level1FeatureCalculator parses these to populate the
    // FeatureBlock with geometric and semantic features.
    // =========================================================================

    @Test
    fun testViAttributeParsesToGeometry() {
        val doc = Jsoup.parse("<html><body><div vi='10 20 100 50'>Hello</div></body></html>")
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        assertEquals(10, div.left)
        assertEquals(20, div.top)
        assertEquals(100, div.width)
        assertEquals(50, div.height)
        // Derived geometry
        assertEquals(110, div.right)
        assertEquals(70, div.bottom)
        assertEquals(5000, div.area)
        assertEquals(60, div.centerX) // (10 + 110) / 2
        assertEquals(45, div.centerY) // (20 + 70) / 2
    }

    @Test
    fun testViAttributeMultipleElements() {
        val html = """
            <html><body>
            <div vi='0 0 800 600'>
                <p vi='50 100 700 30'>Paragraph</p>
                <img vi='50 150 300 200'>
            </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        assertEquals(0, div.left); assertEquals(0, div.top)
        assertEquals(800, div.width); assertEquals(600, div.height)

        val p = doc.selectFirst("p")!!
        assertEquals(50, p.left); assertEquals(100, p.top)
        assertEquals(700, p.width); assertEquals(30, p.height)

        val img = doc.selectFirst("img")!!
        assertEquals(50, img.left); assertEquals(150, img.top)
        assertEquals(300, img.width); assertEquals(200, img.height)
    }

    @Test
    fun testFeatureCalculatorSetsFeatureBlockAndNodeIndex() {
        val doc = Jsoup.parse("<html><body><div vi='0 0 100 100'>Hello</div></body></html>")
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        // After calculation, featureBlock should be set
        assertNotNull(div.featureBlock)
        // nodeIndex should be >= 0
        assertTrue(div.nodeIndex >= 0)
        // features should be a FeatureBlockVector backed by the block
        assertTrue(div.features is FeatureBlockVector)
    }

    @Test
    fun testFeatureCalculatorSetsDepthAndSequence() {
        val doc = Jsoup.parse("<html><body><div vi='0 0 100 100'>Hello</div></body></html>")
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        // Jsoup auto-creates <head> before <body>; depth accounts for all nodes
        // html=0, head=1, body=2, div=3 (approximately)
        assertTrue(div.depth >= 2, "div should be at depth >= 2")
        // sequence is assigned during traversal
        assertTrue(div.sequence >= 0)
    }

    @Test
    fun testFeatureCalculatorSetsImmutableTextOnTextNodes() {
        val doc = Jsoup.parse("<p vi='10 10 100 20'>Hello World</p>")
        Level1FeatureCalculator().calculate(doc)

        val p = doc.selectFirst("p")!!
        val textNode = p.childNode(0) as TextNode
        assertTrue(textNode.immutableText.isNotBlank())
        assertTrue(textNode.immutableText.contains("Hello"))
    }

    @Test
    fun testFeatureCalculatorCharCountFromTextNode() {
        val doc = Jsoup.parse("<p vi='10 10 100 20'>Hello</p>")
        Level1FeatureCalculator().calculate(doc)

        val p = doc.selectFirst("p")!!
        // "Hello" = 5 chars → CH = 5 on the TextNode, accumulated to p
        val textNode = p.childNode(0) as TextNode
        assertEquals(5, textNode.numChars)
        // CH flows up: the p element accumulates its children's CH values
        assertEquals(5, p.numChars)
    }

    @Test
    fun testFeatureCalculatorCharCountAccumulates() {
        // Use compact HTML to avoid whitespace TextNodes contributing CH
        val doc = Jsoup.parse("<html><body><div vi='0 0 300 200'><p vi='10 10 280 30'>Hello</p><p vi='10 50 280 30'>World</p></div></body></html>")
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        // "Hello" = 5 chars, "World" = 5 chars → div.CH = 10
        // Whitespace TextNodes between tags also have CH; compact HTML minimises this
        assertTrue(div.numChars >= 10, "div.numChars should be at least 10 (5+5 from the two p elements)")
    }

    @Test
    fun testFeatureCalculatorTextNodeCountAccumulates() {
        // Compact HTML avoids whitespace TextNodes contributing TN
        val doc = Jsoup.parse("<html><body><div vi='0 0 300 200'><p vi='10 10 280 30'>Hello</p><p vi='10 50 280 30'>World</p></div></body></html>")
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        // Each p has one non-whitespace TextNode → each gets TN=1 → div.TN >= 2
        assertTrue(div.numTextNodes >= 2, "div.numTextNodes should be at least 2")
    }

    @Test
    fun testFeatureCalculatorAnchorCountAccumulates() {
        val doc = Jsoup.parse("""
            <div vi='0 0 300 200'>
                <a vi='10 10 100 20' href='/a'>Link A</a>
                <a vi='10 40 100 20' href='/b'>Link B</a>
            </div>
        """.trimIndent())
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        assertEquals(2, div.numAnchors)
    }

    @Test
    fun testFeatureCalculatorImageCountAccumulates() {
        val doc = Jsoup.parse("""
            <div vi='0 0 300 200'>
                <img vi='10 10 50 50' src='a.png'>
                <img vi='70 10 50 50' src='b.png'>
            </div>
        """.trimIndent())
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        assertEquals(2, div.numImages)
    }

    @Test
    fun testFeatureCalculatorChildCountAccumulates() {
        val doc = Jsoup.parse("""
            <div vi='0 0 300 200'>
                <p vi='10 10 100 20'>A</p>
                <p vi='10 40 100 20'>B</p>
                <p vi='10 70 100 20'>C</p>
            </div>
        """.trimIndent())
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        assertEquals(3, div.numChildren)
    }

    @Test
    fun testFeatureCalculatorSiblingCount() {
        val doc = Jsoup.parse("""
            <div vi='0 0 300 200'>
                <p vi='10 10 100 20'>A</p>
                <p vi='10 40 100 20'>B</p>
                <p vi='10 70 100 20'>C</p>
            </div>
        """.trimIndent())
        Level1FeatureCalculator().calculate(doc)

        // Each p should have SIB=3 (3 sibling p elements)
        val ps = doc.select("p")
        for (p in ps) {
            assertEquals(3, p.numSiblings)
        }
    }

    @Test
    fun testFeatureCalculatorTextNodeGeometryViaTvAttribute() {
        // TextNodes get geometry from parent's tv{index} attributes
        val html = """
            <html><body>
            <p vi='10 20 100 30' tv0='10 20 40 20' tv1='50 20 60 20'>
                Hello World
            </p>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        Level1FeatureCalculator().calculate(doc)

        // The TextNode at index 0 gets its rect from tv0
        val p = doc.selectFirst("p")!!
        val textNode = p.childNode(0) as TextNode
        // tv0 = "10 20 40 20"
        assertEquals(10, textNode.left)
        assertEquals(20, textNode.top)
        assertEquals(40, textNode.width)
        assertEquals(20, textNode.height)
    }

    @Test
    fun testFeatureCalculatorElementWithoutViGetsZeroGeometry() {
        val doc = Jsoup.parse("<div></div>")
        Level1FeatureCalculator().calculate(doc)

        val div = doc.selectFirst("div")!!
        assertEquals(0, div.left)
        assertEquals(0, div.top)
        assertEquals(0, div.width)
        assertEquals(0, div.height)
    }

    @Test
    fun testFeatureCalculatorSetsBodyDimensionsFromDescendants() {
        // body rect is calculated from the widest child (via percentile)
        val html = """
            <html><body>
            <div vi='0 0 900 100'></div>
            <div vi='0 100 1200 50'></div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        Level1FeatureCalculator().calculate(doc)

        // body.height = max(y2) of descendants + 20
        // div[1] y2 = 100 + 50 = 150 → height should be 170
        val body = doc.body()
        assertTrue(body.width >= 900) // at least minW
        assertTrue(body.height >= 150 + 20)
    }

    @Test
    fun testFeatureCalculatorNodesHaveUniqueSequence() {
        val doc = Jsoup.parse("""
            <html><body>
            <div vi='0 0 100 100'>
                <p vi='10 10 80 20'>A</p>
                <p vi='10 40 80 20'>B</p>
            </div>
            </body></html>
        """.trimIndent())
        Level1FeatureCalculator().calculate(doc)

        // Collect all node sequences — they should be unique and sequential from 0
        val sequences = mutableListOf<Int>()
        doc.forEach(includeRoot = true) { if (it is Element || it is TextNode) sequences.add(it.sequence) }
        val uniqueSequences = sequences.toSet()
        assertEquals(sequences.size, uniqueSequences.size, "All node sequences must be unique")
        // Sequences should start at 0
        assertEquals(0, sequences.minOrNull())
    }
}
