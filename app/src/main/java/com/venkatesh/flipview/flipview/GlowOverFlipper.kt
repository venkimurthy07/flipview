package com.covacare.doctor.views.flipview

import android.graphics.Canvas
import android.support.v4.widget.EdgeEffectCompat

/**
 * Created by Venkatesh on 07/06/18.
 */

class GlowOverFlipper(private val mFlipView: FlipView) : OverFlipper {

    private val mTopEdgeEffect: EdgeEffectCompat
    private val mBottomEdgeEffect: EdgeEffectCompat
    override var totalOverFlip: Float = 0.toFloat()
        private set

    init {
        mTopEdgeEffect = EdgeEffectCompat(mFlipView.context)
        mBottomEdgeEffect = EdgeEffectCompat(mFlipView.context)
    }

    override fun calculate(flipDistance: Float, minFlipDistance: Float,
                           maxFlipDistance: Float): Float {
        val deltaOverFlip = flipDistance - if (flipDistance < 0) minFlipDistance else maxFlipDistance

        totalOverFlip += deltaOverFlip

        if (deltaOverFlip > 0) {
            mBottomEdgeEffect.onPull(deltaOverFlip / if (mFlipView.isFlippingVertically) mFlipView.height else mFlipView.width)
        } else if (deltaOverFlip < 0) {
            mTopEdgeEffect.onPull(-deltaOverFlip / if (mFlipView.isFlippingVertically) mFlipView.height else mFlipView.width)
        }
        return if (flipDistance < 0) minFlipDistance else maxFlipDistance
    }

    override fun draw(c: Canvas): Boolean {
        return drawTopEdgeEffect(c) or drawBottomEdgeEffect(c)
    }

    private fun drawTopEdgeEffect(canvas: Canvas): Boolean {
        var needsMoreDrawing = false
        if (!mTopEdgeEffect.isFinished) {
            canvas.save()
            if (mFlipView.isFlippingVertically) {
                mTopEdgeEffect.setSize(mFlipView.width, mFlipView.height)
                canvas.rotate(0f)
            } else {
                mTopEdgeEffect.setSize(mFlipView.height, mFlipView.width)
                canvas.rotate(270f)
                canvas.translate((-mFlipView.height).toFloat(), 0f)
            }
            needsMoreDrawing = mTopEdgeEffect.draw(canvas)
            canvas.restore()
        }
        return needsMoreDrawing
    }

    private fun drawBottomEdgeEffect(canvas: Canvas): Boolean {
        var needsMoreDrawing = false
        if (!mBottomEdgeEffect.isFinished) {
            canvas.save()
            if (mFlipView.isFlippingVertically) {
                mBottomEdgeEffect.setSize(mFlipView.width, mFlipView.height)
                canvas.rotate(180f)
                canvas.translate((-mFlipView.width).toFloat(), (-mFlipView.height).toFloat())
            } else {
                mBottomEdgeEffect.setSize(mFlipView.height, mFlipView.width)
                canvas.rotate(90f)
                canvas.translate(0f, (-mFlipView.width).toFloat())
            }
            needsMoreDrawing = mBottomEdgeEffect.draw(canvas)
            canvas.restore()
        }
        return needsMoreDrawing
    }

    override fun overFlipEnded() {
        mTopEdgeEffect.onRelease()
        mBottomEdgeEffect.onRelease()
        totalOverFlip = 0f
    }

}
