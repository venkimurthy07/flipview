package com.venkatesh.flipview.flipview

import android.graphics.Canvas

/**
 * Created by Venkatesh on 07/06/18.
 */
class RubberBandOverFlipper : OverFlipper {

    override var totalOverFlip: Float = 0.toFloat()
        private set
    private var mCurrentOverFlip: Float = 0.toFloat()

    override fun calculate(flipDistance: Float, minFlipDistance: Float,
                           maxFlipDistance: Float): Float {

        val deltaOverFlip: Float
        if (flipDistance < minFlipDistance) {
            deltaOverFlip = flipDistance - minFlipDistance - mCurrentOverFlip
        } else {
            deltaOverFlip = flipDistance - maxFlipDistance - mCurrentOverFlip
        }

        totalOverFlip += deltaOverFlip

        val sign = Math.signum(totalOverFlip)

        mCurrentOverFlip = Math.pow(Math.abs(totalOverFlip).toDouble(), EXPONENTIAL_DECREES.toDouble()).toFloat() * sign


        if (mCurrentOverFlip < 0) {
            mCurrentOverFlip = Math.max(-MAX_OVER_FLIP_DISTANCE, mCurrentOverFlip)
        } else {
            mCurrentOverFlip = Math.min(MAX_OVER_FLIP_DISTANCE, mCurrentOverFlip)
        }

        return mCurrentOverFlip + if (mCurrentOverFlip < 0) minFlipDistance else maxFlipDistance
    }

    override fun draw(c: Canvas): Boolean {
        return false
    }

    override fun overFlipEnded() {
        totalOverFlip = 0f
        mCurrentOverFlip = 0f
    }

    companion object {

        private val MAX_OVER_FLIP_DISTANCE = 70f
        private val EXPONENTIAL_DECREES = 0.85f
    }

}
