package ai.platon.pulsar.dom.features

import org.apache.commons.math3.exception.DimensionMismatchException
import org.apache.commons.math3.exception.OutOfRangeException
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector

/**
 * A lightweight [RealVector] implementation backed by a [FeatureBlock].
 *
 * Unlike [ArrayRealVector], this class does NOT hold its own `double[]` data array.
 * Instead, all get/set operations are delegated to the shared [FeatureBlock], which
 * stores all node feature vectors in a single contiguous [DoubleArray].
 *
 * This dramatically reduces heap object count: for a document with N nodes, we go from
 * N `double[]` arrays (one per `ArrayRealVector`) to a single `DoubleArray` in the block.
 *
 * Only the core accessor methods ([getEntry], [setEntry], [getDimension]) are supported;
 * vector-arithmetic methods ([append], [ebeMultiply], [ebeDivide], etc.) throw
 * [UnsupportedOperationException] as they are never used in feature access patterns.
 *
 * @param block The shared feature block for the document
 * @param nodeIndex The index of this node within the document (0-based)
 * @param dimension The number of features per node
 */
class FeatureBlockVector(
    private val block: FeatureBlock,
    private val nodeIndex: Int,
    private val dimension: Int
) : RealVector() {

    override fun getDimension(): Int = dimension

    override fun getEntry(index: Int): Double {
        checkIndex(index)
        return block[nodeIndex, index]
    }

    override fun setEntry(index: Int, value: Double) {
        checkIndex(index)
        block[nodeIndex, index] = value
    }

    /**
     * Creates an independent copy of this row as an [ArrayRealVector].
     * The copy owns its own data and is detached from the [FeatureBlock].
     */
    override fun copy(): RealVector {
        val data = DoubleArray(dimension) { block[nodeIndex, it] }
        return ArrayRealVector(data, false)
    }

    // --- Unsupported vector-arithmetic operations ---
    // These are never called by the feature access patterns used in pulsar-dom.
    // They throw UnsupportedOperationException to catch any accidental use.

    override fun append(v: RealVector): RealVector = unsupported()
    override fun append(d: Double): RealVector = unsupported()
    override fun getSubVector(index: Int, n: Int): RealVector = unsupported()
    override fun setSubVector(index: Int, v: RealVector) = unsupported()
    override fun isNaN(): Boolean = false
    override fun isInfinite(): Boolean = false
    override fun ebeDivide(v: RealVector): RealVector = unsupported()
    override fun ebeMultiply(v: RealVector): RealVector = unsupported()

    override fun checkIndex(index: Int) {
        if (index < 0 || index >= dimension) {
            throw OutOfRangeException(index, 0, dimension - 1)
        }
    }

    private fun unsupported(): Nothing {
        throw UnsupportedOperationException(
            "FeatureBlockVector does not support vector arithmetic. " +
            "Use copy() to obtain a standalone ArrayRealVector for computations."
        )
    }

    companion object {
        /**
         * Returns an empty feature vector (zero dimensions).
         * Used as a replacement for [ArrayRealVector()] in [clearFeatures][ai.platon.pulsar.dom.nodes.node.ext.clearFeatures]
         * and clone operations.
         */
        fun empty(): RealVector = ArrayRealVector()
    }
}
