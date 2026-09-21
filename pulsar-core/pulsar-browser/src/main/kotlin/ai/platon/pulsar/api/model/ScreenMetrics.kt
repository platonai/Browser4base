package ai.platon.pulsar.api.model

import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.math.geometric.DimI
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Resolves the host screen size that should be reported to the page.
 *
 * The device metrics override used to pin a 1920x1080 viewport while leaving `screenWidth` /
 * `screenHeight` unset, so on a 1680-wide display the page reported
 * `window.innerWidth (1920) > screen.width (1680)` — a combination no real browser produces.
 * Passing an explicit screen size removes that tell.
 *
 * The reported screen is never smaller than the viewport, so the impossible
 * `innerWidth > screen.width` relation can not come back through this path.
 *
 * See issue #11 section 7.
 */
object ScreenMetrics {
    private val logger = getLogger(ScreenMetrics::class)

    /**
     * The host screen size, or `null` when it cannot be determined (e.g. a truly headless JVM
     * without a display).
     */
    fun detect(): DimI? {
        return try {
            if (GraphicsEnvironment.isHeadless()) {
                return null
            }
            val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
                .bounds
            DimI(bounds.width, bounds.height)
        } catch (e: Throwable) {
            // Some JVMs throw HeadlessException from Toolkit even when the check above passed.
            try {
                val size = Toolkit.getDefaultToolkit().screenSize
                DimI(size.width, size.height)
            } catch (ignored: Throwable) {
                logger.debug("Cannot determine the host screen size, the viewport size will be used: {}", e.message)
                null
            }
        }
    }

    /**
     * The screen size to report for a [viewport]-sized window: the host screen when known,
     * clamped up to the viewport so that `screen.width >= innerWidth` always holds.
     */
    fun effectiveScreen(viewport: DimI, detected: DimI? = detect()): DimI {
        if (detected == null) return viewport
        return DimI(
            maxOf(detected.width, viewport.width),
            maxOf(detected.height, viewport.height),
        )
    }
}
