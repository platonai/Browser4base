package ai.platon.pulsar.dom.features

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.dom.features.defined.*
import ai.platon.pulsar.dom.nodes.DOMRect
import ai.platon.pulsar.dom.nodes.forEachElement
import ai.platon.pulsar.dom.nodes.node.ext.*
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * The level 1 feature calculator calculate for the minimal features.
 *
 * Uses a document-level [FeatureBlock] to store all node feature vectors in a single
 * contiguous [DoubleArray] with zero per-node heap allocations during calculation.
 * Only [FeatureBlock] + [DoubleArray] are allocated — no intermediate vector objects.
 */
class Level1FeatureCalculator: AbstractFeatureCalculator() {
    companion object {
        init {
            ResourceLoader.addClassFactory(ClassFactory())
            if (FeatureRegistry.registeredFeatures.isEmpty()) {
                FeatureRegistry.register(F.entries.map { it.toFeature() })
                require(FeatureRegistry.registeredFeatures.size == N)
            }
        }
    }

    /**
     * The [FeatureBlock] for the current document being calculated.
     * Set during [calculate] and accessible after calculation completes.
     */
    var featureBlock: FeatureBlock? = null
        private set

    override fun calculate(document: Document) {
        val nodeCount = countNodes(document)
        val block = FeatureBlock(nodeCount, FeatureRegistry.dimension)
        this.featureBlock = block
        NodeTraversor.traverse(Level1NodeFeatureCalculatorVisitor(block), document)
    }

    override fun dispose() {
        FeatureRegistry.unregister()
    }

    /**
     * Count the total number of nodes in the document.
     * A fast O(n) traversal with no allocations — used to size the [FeatureBlock].
     */
    private fun countNodes(document: Document): Int {
        var count = 0
        NodeTraversor.traverse({ _, _ -> ++count }, document)
        return count
    }
}

/**
 * The class factory for ResourceLoader
 */
class ClassFactory : ResourceLoader.ClassFactory {
    override fun match(name: String): Boolean {
        return name.startsWith(this.javaClass.`package`.name)
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*> {
        return this.javaClass.classLoader.loadClass(name)
    }
}

/**
 * A zero-allocation feature calculator visitor.
 *
 * Instead of creating per-node [org.apache.commons.math3.linear.RealVector] objects,
 * this visitor writes directly into the document-level [FeatureBlock] using each node's
 * index. The only heap allocations are:
 * 1. The [FeatureBlock] (one per document)
 * 2. The [DoubleArray] inside the FeatureBlock (one per document)
 *
 * No per-node objects are created during the traversal.
 */
private class Level1NodeFeatureCalculatorVisitor(
    private val block: FeatureBlock
) : NodeVisitor {
    var sequence: Int = 0
        private set

    // -- helpers for direct FeatureBlock access via node index --

    private fun Node.getF(key: Int): Double = block[extension.nodeIndex, key]
    private fun Node.setF(key: Int, value: Double) { block[extension.nodeIndex, key] = value }
    private fun Node.addF(key: Int, delta: Double) { setF(key, getF(key) + delta) }

    // -- NodeVisitor interface --

    override fun head(node: Node, depth: Int) {
        // Store metadata only — no vector objects created
        node.extension.featureBlock = block
        node.extension.nodeIndex = sequence

        node.setF(DEP, depth.toDouble())
        node.setF(SEQ, sequence.toDouble())

        calcSelfIndicator(node)
        ++sequence
    }

    override fun tail(node: Node, depth: Int) {
        if (node !is Element && node !is TextNode) {
            return
        }

        if (node is TextNode) {
            val parent = node.parent() ?: return
            val ch = node.getF(CH)
            val otn = if (ch == 0.0) 0.0 else 1.0
            parent.addF(TN, otn)
            parent.addF(CH, ch)
            return
        }

        if (node is Element) {
            val pe = node.parent() ?: return

            // accumulate features upward to parent
            pe.addF(CH, node.getF(CH))
            pe.addF(TN, node.getF(TN))
            pe.addF(A, node.getF(A))
            pe.addF(IMG, node.getF(IMG))
            pe.addF(C, 1.0)

            // count of element siblings
            val childCount = node.getF(C)
            node.childNodes().forEach {
                if (it is Element) {
                    it.setF(SIB, childCount)
                }
            }
        }

        if (node.nodeName().equals("body", ignoreCase = true)) {
            val rect = calculateBodyRect(node)
            node.width = rect.width.toInt()
            node.height = rect.height.toInt()
        }
    }

    // -- self indicator calculation --

    private fun calcSelfIndicator(node: Node) {
        if (node !is Element && node !is TextNode) {
            return
        }

        val rect = getDOMRect(node)
        if (!rect.isEmpty) {
            node.setF(TOP, rect.top)
            node.setF(LEFT, rect.left)
            node.setF(WIDTH, rect.width)
            node.setF(HEIGHT, rect.height)
        }

        if (node is TextNode) {
            // Trim: remove all surrounding unicode white spaces
            // @see https://en.wikipedia.org/wiki/Whitespace_character
            val text = node.text()
            node.extension.immutableText = text
            val ch = text.length.toDouble()

            if (ch > 0.0) {
                node.setF(CH, ch)
            }
        }

        if (node is Element) {
            var a = 0.0
            var img = 0.0

            if (node.nodeName() == "a") {
                ++a
            }

            if (node.nodeName() == "img") {
                ++img
            }

            node.setF(A, a)
            node.setF(IMG, img)
        }
    }

    // -- geometry helpers --

    private fun getDOMRect(node: Node): DOMRect {
        return if (node is TextNode) getDOMRectInternal("tv", node)
        else DOMRect.parseDOMRect(node.attr("vi"))
    }

    private fun getDOMRectInternal(attrKey: String, node: TextNode): DOMRect {
        val parent = node.parent() ?: return DOMRect()
        val i = node.siblingIndex()
        val vi = parent.attr("$attrKey$i")
        return DOMRect.parseDOMRect(vi)
    }

    private fun calculateBodyRect(body: Node): DOMRect {
        val minW = 900.0
        val widths = DescriptiveStatistics()
        widths.addValue(minW)
        var height = body.height

        body.forEachElement {
            if (it.width > minW) {
                widths.addValue(it.width.toDouble())
            }
            if (it.y2 > height) {
                height = it.y2
            }
        }

        return DOMRect(0.0, 0.0, widths.getPercentile(90.0), 20 + height.toDouble())
    }
}
