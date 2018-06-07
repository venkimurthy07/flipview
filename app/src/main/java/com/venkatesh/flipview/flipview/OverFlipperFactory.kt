package com.venkatesh.flipview.flipview


/**
 * Created by Venkatesh on 07/06/18.
 */

object OverFlipperFactory {

    internal fun create(v: FlipView, mode: OverFlipMode): OverFlipper? {
        when (mode) {
            OverFlipMode.GLOW -> return GlowOverFlipper(v)
            OverFlipMode.RUBBER_BAND -> return RubberBandOverFlipper()
        }
        return null
    }

}
