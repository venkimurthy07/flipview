package com.venkatesh.flipview.flipview

import android.graphics.Canvas

/**
 * Created by Venkatesh on 07/06/18.
 */
interface OverFlipper {

    /**
     *
     * @return the total flip distance the has been over flipped. This is used
     * by the onOverFlipListener so make sure to return the correct
     * value.
     */
    val totalOverFlip: Float

    /**
     *
     * @param flipDistance
     * the current flip distance
     *
     * @param minFlipDistance
     * the minimum flip distance, usually 0
     *
     * @param maxFlipDistance
     * the maximum flip distance
     *
     * @return the flip distance after calculations
     */
    fun calculate(flipDistance: Float, minFlipDistance: Float,
                  maxFlipDistance: Float): Float

    /**
     *
     * @param v
     * the view to apply any drawing onto
     *
     * @return a boolean flag indicating if the view needs to be invalidated
     */
    fun draw(c: Canvas): Boolean

    /**
     * Triggered from a touch up or cancel event. reset and release state
     * variables here.
     */
    fun overFlipEnded()

}
