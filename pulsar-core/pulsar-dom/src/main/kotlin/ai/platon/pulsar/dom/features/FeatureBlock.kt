package ai.platon.pulsar.dom.features

/**
 * A contiguous memory block storing numerical feature vectors for all nodes in a [FeaturedDocument][ai.platon.pulsar.dom.FeaturedDocument].
 *
 * Instead of allocating a separate double array per node (as [org.apache.commons.math3.linear.ArrayRealVector] does),
 * this class stores all node feature vectors in a single [DoubleArray], indexed by `nodeIndex * dimension + featureKey`.
 *
 * Benefits:
 * - Single allocation per document instead of N allocations per node
 * - Contiguous memory improves cache locality
 * - Reduced GC pressure from fewer heap objects
 *
 * @param nodeCount The total number of nodes in the document
 * @param dimension The number of features per node (e.g. 13 for Level 1 features)
 */
class FeatureBlock(val nodeCount: Int, val dimension: Int) {

    private val data = DoubleArray(nodeCount * dimension)

    /**
     * Get a feature value for a node.
     *
     * @param nodeIndex The node's index in the document (0-based)
     * @param featureKey The feature key (0-based index into the feature vector)
     * @return The feature value
     */
    operator fun get(nodeIndex: Int, featureKey: Int): Double {
        return data[nodeIndex * dimension + featureKey]
    }

    /**
     * Set a feature value for a node.
     *
     * @param nodeIndex The node's index in the document (0-based)
     * @param featureKey The feature key (0-based index into the feature vector)
     * @param value The feature value to set
     */
    operator fun set(nodeIndex: Int, featureKey: Int, value: Double) {
        data[nodeIndex * dimension + featureKey] = value
    }

    /**
     * Create a lightweight [RealVector][org.apache.commons.math3.linear.RealVector] view for a specific node,
     * backed by this block.
     *
     * @param nodeIndex The node's index in the document (0-based)
     * @return A [FeatureBlockVector] that delegates get/set operations to this block
     */
    fun rowVector(nodeIndex: Int): FeatureBlockVector {
        return FeatureBlockVector(this, nodeIndex, dimension)
    }
}
