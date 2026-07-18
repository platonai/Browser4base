package ai.platon.pulsar.dom.features

import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.math.vectors.get
import ai.platon.pulsar.common.math.vectors.set
import ai.platon.pulsar.dom.features.defined.*
import ai.platon.pulsar.dom.nodes.DOMRect
import ai.platon.pulsar.dom.nodes.forEachElement
import ai.platon.pulsar.dom.nodes.node.ext.*
import org.apache.commons.math3.linear.RealVector
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
 * contiguous [DoubleArray], replacing per-node [org.apache.commons.math3.linear.ArrayRealVector]
 * allocations to reduce GC pressure.
 * */
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
     * Set by [FeaturedDocument][ai.platon.pulsar.dom.FeaturedDocument] before calling [calculate].
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
 * */
class ClassFactory : ResourceLoader.ClassFactory {
    override fun match(name: String): Boolean {
        return name.startsWith(this.javaClass.`package`.name)
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*> {
        return this.javaClass.classLoader.loadClass(name)
    }
}

private class Level1NodeFeatureCalculatorVisitor(
    private val featureBlock: FeatureBlock
) : NodeVisitor {
    var sequence: Int = 0
        private set

    // hit when the node is first seen
    override fun head(node: Node, depth: Int) {
        val features = featureBlock.rowVector(sequence)
        node.extension.features = features

        features[DEP] = depth.toDouble()
        features[SEQ] = sequence.toDouble()

        calcSelfIndicator(node, features)
        ++sequence
    }

    // 单个节点统计项
    private fun calcSelfIndicator(node: Node, features: RealVector) {
        if (node !is Element && node !is TextNode) {
            return
        }

        val rect = getDOMRect(node)
        if (!rect.isEmpty) {
            features[TOP] = rect.top
            features[LEFT] = rect.left
            features[WIDTH] = rect.width
            features[HEIGHT] = rect.height
        }

        if (node is TextNode) {
            // Trim: remove all surrounding unicode white spaces, including all HT, VT, LF, FF, CR, ASCII space, etc
            // @see https://en.wikipedia.org/wiki/Whitespace_character
            val text = node.text()
            node.extension.immutableText = text
            val ch = text.length.toDouble()

            if (ch > 0.0) {
                features[CH] = ch
            }
        }

        if (node is Element) {
            var a = 0.0
            var img = 0.0

            // link relative
            if (node.nodeName() == "a") {
                ++a
            }

            // image relative
            if (node.nodeName() == "img") {
                ++img
            }

            features[A] = a
            features[IMG] = img
        }
    }

    // hit when all the node's children (if any) have been visited
    override fun tail(node: Node, depth: Int) {
        if (node !is Element && node !is TextNode) {
            return
        }

        if (node is TextNode) {
            val parent = node.parent() ?: return
            val features = node.extension.features

            // no-blank own text node
            val otn = if (features[CH] == 0.0) 0.0 else 1.0
            val parentFeatures = parent.extension.features
            parentFeatures[TN] = parentFeatures[TN] + otn
            parentFeatures[CH] = parentFeatures[CH] + features[CH]

            return
        }

        if (node is Element) {
            // accumulate features for parent node
            val pe = node.parent() ?: return
            val nodeFeatures = node.extension.features
            val parentFeatures = pe.extension.features

            // code structure feature
            parentFeatures[CH] = parentFeatures[CH] + nodeFeatures[CH]
            parentFeatures[TN] = parentFeatures[TN] + nodeFeatures[TN]
            parentFeatures[A] = parentFeatures[A] + nodeFeatures[A]
            parentFeatures[IMG] = parentFeatures[IMG] + nodeFeatures[IMG]
            parentFeatures[C] = parentFeatures[C] + 1.0

            // count of element siblings
            val childCount = nodeFeatures[C]
            node.childNodes().forEach {
                if (it is Element) {
                    it.extension.features[SIB] = childCount
                }
            }
        }

        if (node.nodeName().equals("body", ignoreCase = true)) {
            val rect = calculateBodyRect(node)
            node.width = rect.width.toInt()
            node.height = rect.height.toInt()
        }
    }

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
