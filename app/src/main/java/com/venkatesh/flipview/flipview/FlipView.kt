package com.venkatesh.flipview.flipview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.content.res.TypedArray
import android.database.DataSetObserver
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.Rect
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.VelocityTrackerCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ListAdapter
import android.widget.Scroller

import com.venkatesh.flipview.R

/**
 * Created by Venkatesh on 07/06/18.
 */

class FlipView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    private val dataSetObserver = object : DataSetObserver() {

        override fun onChanged() {
            dataSetChanged()
        }

        override fun onInvalidated() {
            dataSetInvalidated()
        }

    }

    private var mScroller: Scroller? = null
    private val flipInterpolator = DecelerateInterpolator()
    private var mPeakAnim: ValueAnimator? = null
    private val mPeakInterpolator = AccelerateDecelerateInterpolator()

    /**
     *
     * @return true if the view is flipping vertically, can only be set via xml
     * attribute "orientation"
     */
    var isFlippingVertically = true
    private var mIsFlipping: Boolean = false
    private var mIsUnableToFlip: Boolean = false
    private val mIsFlippingEnabled = true
    private var mLastTouchAllowed = true
    private var mTouchSlop: Int = 0
    private var mIsOverFlipping: Boolean = false

    // keep track of pointer
    private var mLastX = -1f
    private var mLastY = -1f
    private var mActivePointerId = INVALID_POINTER

    // velocity stuff
    private var mVelocityTracker: VelocityTracker? = null
    private var mMinimumVelocity: Int = 0
    private var mMaximumVelocity: Int = 0

    // views get recycled after they have been pushed out of the active queue
    private var mRecycler = Recycler()

    private var mAdapter: ListAdapter? = null
    var pageCount = 0
        private set
    private val mPreviousPage = Page()
    private val mCurrentPage = Page()
    private val mNextPage = Page()
    private var mEmptyView: View? = null

    private var mOnFlipListener: OnFlipListener? = null
    private var mOnOverFlipListener: OnOverFlipListener? = null

    private var mFlipDistance = INVALID_FLIP_DISTANCE.toFloat()
    var currentPage = INVALID_PAGE_POSITION
        private set
    private var mLastDispatchedPageEventIndex = 0
    private var mCurrentPageId: Long = 0

    /**
     *
     * @return the overflip mode of this flipview. Default is GLOW
     */
    /**
     * Set the overflip mode of the flipview. GLOW is the standard seen in all
     * andriod lists. RUBBER_BAND is more like iOS lists which list you flip
     * past the first/last flip_page_one but adding friction, like a rubber band.
     *
     * @param overFlipMode
     */
    var overFlipMode: OverFlipMode? = null
        set(overFlipMode) {
            field = overFlipMode
            mOverFlipper = OverFlipperFactory.create(this, this.overFlipMode!!)
        }
    private var mOverFlipper: OverFlipper? = null

    // clipping rects
    private val mTopRect = Rect()
    private val mBottomRect = Rect()
    private val mRightRect = Rect()
    private val mLeftRect = Rect()

    // used for transforming the canvas
    private val mCamera = Camera()
    private val mMatrix = Matrix()

    // paints drawn above views when flipping
//    private val mShadowPaint = Paint()
//    private val mShadePaint = Paint()
//    private val mShinePaint = Paint()

    private// check if id is on same position, this is because it will
    // often be that and this way you do not need to iterate the whole
    // dataset. If it is the same position, you are done.
    // iterate the dataset and look for the correct id. If it
    // exists, set that position as the current position.
    // Id no longer is dataset, keep current flip_page_one
    val newPositionOfCurrentPage: Int
        get() {
            if (mCurrentPageId == mAdapter!!.getItemId(currentPage)) {
                return currentPage
            }
            for (i in 0 until mAdapter!!.count) {
                if (mCurrentPageId == mAdapter!!.getItemId(i)) {
                    return i
                }
            }
            return currentPage
        }

    private// fix for negative modulo. always want a positive flip degree
    val degreesFlipped: Float
        get() {
            var localFlipDistance = mFlipDistance % FLIP_DISTANCE_PER_PAGE
            if (localFlipDistance < 0) {
                localFlipDistance += FLIP_DISTANCE_PER_PAGE.toFloat()
            }

            return localFlipDistance / FLIP_DISTANCE_PER_PAGE * 180
        }

    private val currentPageRound: Int
        get() = Math.round(mFlipDistance / FLIP_DISTANCE_PER_PAGE)

    private val currentPageFloor: Int
        get() = Math.floor((mFlipDistance / FLIP_DISTANCE_PER_PAGE).toDouble()).toInt()

    private val currentPageCeil: Int
        get() = Math.ceil((mFlipDistance / FLIP_DISTANCE_PER_PAGE).toDouble()).toInt()

    /* ---------- API ---------- */

    /**
     *
     * @param adapter
     * a regular ListAdapter, not all methods if the list adapter are
     * used by the flipview
     */
    // remove all the current views
    // TODO pretty confusing
    // this will be correctly set in setFlipDistance method
    var adapter: ListAdapter?
        get() = mAdapter
        set(adapter) {
            if (mAdapter != null) {
                mAdapter!!.unregisterDataSetObserver(dataSetObserver)
            }
            removeAllViews()

            mAdapter = adapter
            pageCount = if (adapter == null) 0 else mAdapter!!.count

            if (adapter != null) {
                mAdapter!!.registerDataSetObserver(dataSetObserver)

                mRecycler.setViewTypeCount(mAdapter!!.viewTypeCount)
                mRecycler.invalidateScraps()
            }
            currentPage = INVALID_PAGE_POSITION
            mFlipDistance = INVALID_FLIP_DISTANCE.toFloat()
            setFlipDistance(0f)

            updateEmptyStatus()
        }

    interface OnFlipListener {
        fun onFlippedToPage(v: FlipView, position: Int, id: Long)
    }

    interface OnOverFlipListener {
        fun onOverFlip(v: FlipView, mode: OverFlipMode?,
                       overFlippingPrevious: Boolean, overFlipDistance: Float,
                       flipDistancePerPage: Float)
    }

    /**
     *
     * @author emilsjolander
     *
     * Class to hold a view and its corresponding info
     */
    internal class Page {
        var v: View? = null
        var position: Int = 0
        var viewType: Int = 0
        var valid: Boolean = false
    }

    init {

        val a = context.obtainStyledAttributes(attrs,
                R.styleable.FlipView)

        // 0 is vertical, 1 is horizontal
        isFlippingVertically = a.getInt(R.styleable.FlipView_orientation,
                VERTICAL_FLIP) == VERTICAL_FLIP

        overFlipMode = OverFlipMode.values()[a.getInt(
                R.styleable.FlipView_overFlipMode, 0)]

        a.recycle()

        init()
    }

    private fun init() {
        val context = context
        val configuration = ViewConfiguration.get(context)

        mScroller = Scroller(context, flipInterpolator)
        mTouchSlop = configuration.scaledPagingTouchSlop
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity

//        mShadowPaint.color = Color.TRANSPARENT
//        mShadowPaint.style = Style.FILL
//        mShadePaint.color = Color.TRANSPARENT
//        mShadePaint.style = Style.FILL
//        mShinePaint.color = Color.TRANSPARENT
//        mShinePaint.style = Style.FILL
    }

    private fun dataSetChanged() {
        val currentPage = this.currentPage
        var newPosition = currentPage

        // if the adapter has stable ids, try to keep the flip_page_one currently on
        // stable.
        if (mAdapter!!.hasStableIds() && currentPage != INVALID_PAGE_POSITION) {
            newPosition = newPositionOfCurrentPage
        } else if (currentPage == INVALID_PAGE_POSITION) {
            newPosition = 0
        }

        // remove all the current views
        recycleActiveViews()
        mRecycler.setViewTypeCount(mAdapter!!.viewTypeCount)
        mRecycler.invalidateScraps()

        pageCount = mAdapter!!.count

        // put the current flip_page_one within the new adapter range
        newPosition = Math.min(pageCount - 1,
                if (newPosition == INVALID_PAGE_POSITION) 0 else newPosition)

        if (newPosition != INVALID_PAGE_POSITION) {
            // TODO pretty confusing
            // this will be correctly set in setFlipDistance method
            this.currentPage = INVALID_PAGE_POSITION
            mFlipDistance = INVALID_FLIP_DISTANCE.toFloat()
            flipTo(newPosition)
        } else {
            mFlipDistance = INVALID_FLIP_DISTANCE.toFloat()
            pageCount = 0
            setFlipDistance(0f)
        }

        updateEmptyStatus()
    }

    private fun dataSetInvalidated() {
        if (mAdapter != null) {
            mAdapter!!.unregisterDataSetObserver(dataSetObserver)
            mAdapter = null
        }
        mRecycler = Recycler()
        removeAllViews()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.getDefaultSize(0, widthMeasureSpec)
        val height = View.getDefaultSize(0, heightMeasureSpec)

        measureChildren(widthMeasureSpec, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun measureChildren(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.getDefaultSize(0, widthMeasureSpec)
        val height = View.getDefaultSize(0, heightMeasureSpec)

        val childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width,
                View.MeasureSpec.EXACTLY)
        val childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height,
                View.MeasureSpec.EXACTLY)
        val childCount = childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, childWidthMeasureSpec, childHeightMeasureSpec)
        }
    }

    override fun measureChild(child: View, parentWidthMeasureSpec: Int,
                              parentHeightMeasureSpec: Int) {
        child.measure(parentWidthMeasureSpec, parentHeightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        layoutChildren()

        mTopRect.top = 0
        mTopRect.left = 0
        mTopRect.right = width
        mTopRect.bottom = height / 2

        mBottomRect.top = height / 2
        mBottomRect.left = 0
        mBottomRect.right = width
        mBottomRect.bottom = height

        mLeftRect.top = 0
        mLeftRect.left = 0
        mLeftRect.right = width / 2
        mLeftRect.bottom = height

        mRightRect.top = 0
        mRightRect.left = width / 2
        mRightRect.right = width
        mRightRect.bottom = height
    }

    private fun layoutChildren() {
        val childCount = childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            layoutChild(child)
        }
    }

    private fun layoutChild(child: View) {
        child.layout(0, 0, width, height)
    }

    private fun setFlipDistance(flipDistance: Float) {

        if (pageCount < 1) {
            mFlipDistance = 0f
            currentPage = INVALID_PAGE_POSITION
            mCurrentPageId = -1
            recycleActiveViews()
            return
        }

        if (flipDistance == mFlipDistance) {
            return
        }

        mFlipDistance = flipDistance

        val currentPageIndex = Math.round(mFlipDistance / FLIP_DISTANCE_PER_PAGE)

        if (currentPage != currentPageIndex) {
            currentPage = currentPageIndex
            mCurrentPageId = mAdapter!!.getItemId(currentPage)

            // TODO be smarter about this. Dont remove a view that will be added
            // again on the next line.
            recycleActiveViews()

            // add the new active views
            if (currentPage > 0) {
                fillPageForIndex(mPreviousPage, currentPage - 1)
                addView(mPreviousPage.v)
            }
            if (currentPage >= 0 && currentPage < pageCount) {
                fillPageForIndex(mCurrentPage, currentPage)
                addView(mCurrentPage.v)
            }
            if (currentPage < pageCount - 1) {
                fillPageForIndex(mNextPage, currentPage + 1)
                addView(mNextPage.v)
            }
        }

        invalidate()
    }

    private fun fillPageForIndex(p: Page, i: Int) {
        p.position = i
        p.viewType = mAdapter!!.getItemViewType(p.position)
        p.v = getView(p.position, p.viewType)
        p.valid = true
    }

    private fun recycleActiveViews() {
        // remove and recycle the currently active views
        if (mPreviousPage.valid) {
            removeView(mPreviousPage.v)
            mRecycler.addScrapView(mPreviousPage.v!!, mPreviousPage.position,
                    mPreviousPage.viewType)
            mPreviousPage.valid = false
        }
        if (mCurrentPage.valid) {
            removeView(mCurrentPage.v)
            mRecycler.addScrapView(mCurrentPage.v!!, mCurrentPage.position,
                    mCurrentPage.viewType)
            mCurrentPage.valid = false
        }
        if (mNextPage.valid) {
            removeView(mNextPage.v)
            mRecycler.addScrapView(mNextPage.v!!, mNextPage.position,
                    mNextPage.viewType)
            mNextPage.valid = false
        }
    }

    private fun getView(index: Int, viewType: Int): View? {
        // get the scrap from the recycler corresponding to the correct view
        // type
        val scrap = mRecycler.getScrapView(index, viewType)

        // get a view from the adapter if a scrap was not found or it is
        // invalid.
        var v: View? = null
        if (scrap == null || !scrap.valid) {
            v = mAdapter!!.getView(index, scrap?.v, this)
        } else {
            v = scrap.v
        }

        // return view
        return v
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {

        if (!mIsFlippingEnabled) {
            return false
        }

        if (pageCount < 1) {
            return false
        }

        val action = ev.action and MotionEvent.ACTION_MASK

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mIsFlipping = false
            mIsUnableToFlip = false
            mActivePointerId = INVALID_POINTER
            if (mVelocityTracker != null) {
                mVelocityTracker!!.recycle()
                mVelocityTracker = null
            }
            return false
        }

        if (action != MotionEvent.ACTION_DOWN) {
            if (mIsFlipping) {
                return true
            } else if (mIsUnableToFlip) {
                return false
            }
        }

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                val activePointerId = mActivePointerId
                if (activePointerId == INVALID_POINTER) {
                    return false
                }

                val pointerIndex = MotionEventCompat.findPointerIndex(ev,
                        activePointerId)
                if (pointerIndex == -1) {
                    mActivePointerId = INVALID_POINTER
                    return false
                }

                val x = MotionEventCompat.getX(ev, pointerIndex)
                val dx = x - mLastX
                val xDiff = Math.abs(dx)
                val y = MotionEventCompat.getY(ev, pointerIndex)
                val dy = y - mLastY
                val yDiff = Math.abs(dy)

                if (isFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff || !isFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff) {
                    mIsFlipping = true
                    mLastX = x
                    mLastY = y
                } else if (isFlippingVertically && xDiff > mTouchSlop || !isFlippingVertically && yDiff > mTouchSlop) {
                    mIsUnableToFlip = true
                }
            }

            MotionEvent.ACTION_DOWN -> {
                mActivePointerId = ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK
                mLastX = MotionEventCompat.getX(ev, mActivePointerId)
                mLastY = MotionEventCompat.getY(ev, mActivePointerId)

                mIsFlipping = !mScroller!!.isFinished or (mPeakAnim != null)
                mIsUnableToFlip = false
                mLastTouchAllowed = true
            }
            MotionEventCompat.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        if (!mIsFlipping) {
            trackVelocity(ev)
        }

        return mIsFlipping
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {

        if (!mIsFlippingEnabled) {
            return false
        }

        if (pageCount < 1) {
            return false
        }

        if (!mIsFlipping && !mLastTouchAllowed) {
            return false
        }

        val action = ev.action

        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_OUTSIDE) {
            mLastTouchAllowed = false
        } else {
            mLastTouchAllowed = true
        }

        trackVelocity(ev)

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {

                // start flipping immediately if interrupting some sort of animation
                if (endScroll() || endPeak()) {
                    mIsFlipping = true
                }

                // Remember where the motion event started
                mLastX = ev.x
                mLastY = ev.y
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mIsFlipping) {
                    val pointerIndex = MotionEventCompat.findPointerIndex(ev,
                            mActivePointerId)
                    if (pointerIndex == -1) {
                        mActivePointerId = INVALID_POINTER
                        return false

                    }
                    val x = MotionEventCompat.getX(ev, pointerIndex)
                    val xDiff = Math.abs(x - mLastX)
                    val y = MotionEventCompat.getY(ev, pointerIndex)
                    val yDiff = Math.abs(y - mLastY)
                    if (isFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff || !isFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff) {
                        mIsFlipping = true
                        mLastX = x
                        mLastY = y
                    }
                }
                if (mIsFlipping) {
                    // Scroll to follow the motion event
                    val activePointerIndex = MotionEventCompat
                            .findPointerIndex(ev, mActivePointerId)
                    if (activePointerIndex == -1) {
                        mActivePointerId = INVALID_POINTER
                        return false
                    }
                    val x = MotionEventCompat.getX(ev, activePointerIndex)
                    val deltaX = mLastX - x
                    val y = MotionEventCompat.getY(ev, activePointerIndex)
                    val deltaY = mLastY - y
                    mLastX = x
                    mLastY = y

                    var deltaFlipDistance = 0f
                    if (isFlippingVertically) {
                        deltaFlipDistance = deltaY
                    } else {
                        deltaFlipDistance = deltaX
                    }

                    deltaFlipDistance /= ((if (isFlippingVertically)
                        height
                    else
                        width) / FLIP_DISTANCE_PER_PAGE).toFloat()
                    setFlipDistance(mFlipDistance + deltaFlipDistance)

                    val minFlipDistance = 0
                    val maxFlipDistance = (pageCount - 1) * FLIP_DISTANCE_PER_PAGE
                    val isOverFlipping = mFlipDistance < minFlipDistance || mFlipDistance > maxFlipDistance
                    if (isOverFlipping) {
                        mIsOverFlipping = true
                        setFlipDistance(mOverFlipper!!.calculate(mFlipDistance,
                                minFlipDistance.toFloat(), maxFlipDistance.toFloat()))
                        if (mOnOverFlipListener != null) {
                            val overFlip = mOverFlipper!!.totalOverFlip
                            mOnOverFlipListener!!.onOverFlip(this, overFlipMode,
                                    overFlip < 0, Math.abs(overFlip),
                                    FLIP_DISTANCE_PER_PAGE.toFloat())
                        }
                    } else if (mIsOverFlipping) {
                        mIsOverFlipping = false
                        if (mOnOverFlipListener != null) {
                            // TODO in the future should only notify flip distance 0
                            // on the correct edge (previous/next)
                            mOnOverFlipListener!!.onOverFlip(this, overFlipMode,
                                    false, 0f, FLIP_DISTANCE_PER_PAGE.toFloat())
                            mOnOverFlipListener!!.onOverFlip(this, overFlipMode,
                                    true, 0f, FLIP_DISTANCE_PER_PAGE.toFloat())
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (mIsFlipping) {
                val velocityTracker = mVelocityTracker
                velocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())

                var velocity = 0
                if (isFlippingVertically) {
                    velocity = VelocityTrackerCompat.getYVelocity(
                            velocityTracker, mActivePointerId).toInt()
                } else {
                    velocity = VelocityTrackerCompat.getXVelocity(
                            velocityTracker, mActivePointerId).toInt()
                }
                smoothFlipTo(getNextPage(velocity))

                mActivePointerId = INVALID_POINTER
                endFlip()

                mOverFlipper!!.overFlipEnded()
            }
            MotionEventCompat.ACTION_POINTER_DOWN -> {
                val index = MotionEventCompat.getActionIndex(ev)
                val x = MotionEventCompat.getX(ev, index)
                val y = MotionEventCompat.getY(ev, index)
                mLastX = x
                mLastY = y
                mActivePointerId = MotionEventCompat.getPointerId(ev, index)
            }
            MotionEventCompat.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
                val index = MotionEventCompat.findPointerIndex(ev,
                        mActivePointerId)
                val x = MotionEventCompat.getX(ev, index)
                val y = MotionEventCompat.getY(ev, index)
                mLastX = x
                mLastY = y
            }
        }
        if (mActivePointerId == INVALID_POINTER) {
            mLastTouchAllowed = false
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {

        if (pageCount < 1) {
            return
        }

        if (!mScroller!!.isFinished && mScroller!!.computeScrollOffset()) {
            setFlipDistance(mScroller!!.currY.toFloat())
        }

        if (mIsFlipping || !mScroller!!.isFinished || mPeakAnim != null) {
            showAllPages()
            drawPreviousHalf(canvas)
            drawNextHalf(canvas)
            drawFlippingHalf(canvas)
        } else {
            endScroll()
            setDrawWithLayer(mCurrentPage.v, false)
            hideOtherPages(mCurrentPage)
            drawChild(canvas, mCurrentPage.v, 0)

            // dispatch listener event now that we have "landed" on a flip_page_one.
            // TODO not the prettiest to have this with the drawing logic,
            // should change.
            if (mLastDispatchedPageEventIndex != currentPage) {
                mLastDispatchedPageEventIndex = currentPage
                postFlippedToPage(currentPage)
            }
        }

        // if overflip is GLOW mode and the edge effects needed drawing, make
        // sure to invalidate
        if (mOverFlipper!!.draw(canvas)) {
            // always invalidate whole screen as it is needed 99% of the time.
            // This is because of the shadows and shines put on the non-flipping
            // pages
            invalidate()
        }
    }

    private fun hideOtherPages(p: Page) {
        if (mPreviousPage !== p && mPreviousPage.valid && mPreviousPage.v!!.visibility != View.GONE) {
            mPreviousPage.v!!.visibility = View.GONE
        }
        if (mCurrentPage !== p && mCurrentPage.valid && mCurrentPage.v!!.visibility != View.GONE) {
            mCurrentPage.v!!.visibility = View.GONE
        }
        if (mNextPage !== p && mNextPage.valid && mNextPage.v!!.visibility != View.GONE) {
            mNextPage.v!!.visibility = View.GONE
        }
        p.v!!.visibility = View.VISIBLE
    }

    private fun showAllPages() {
        if (mPreviousPage.valid && mPreviousPage.v!!.visibility != View.VISIBLE) {
            mPreviousPage.v!!.visibility = View.VISIBLE
        }
        if (mCurrentPage.valid && mCurrentPage.v!!.visibility != View.VISIBLE) {
            mCurrentPage.v!!.visibility = View.VISIBLE
        }
        if (mNextPage.valid && mNextPage.v!!.visibility != View.VISIBLE) {
            mNextPage.v!!.visibility = View.VISIBLE
        }
    }

    /**
     * draw top/left half
     *
     * @param canvas
     */
    private fun drawPreviousHalf(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(if (isFlippingVertically) mTopRect else mLeftRect)

        val degreesFlipped = degreesFlipped
        val p = if (degreesFlipped > 90) mPreviousPage else mCurrentPage

        // if the view does not exist, skip drawing it
        if (p.valid) {
            setDrawWithLayer(p.v, true)
            drawChild(canvas, p.v, 0)
        }

        drawPreviousShadow(canvas)
        canvas.restore()
    }

    /**
     * draw top/left half shadow
     *
     * @param canvas
     */
    private fun drawPreviousShadow(canvas: Canvas) {
        val degreesFlipped = degreesFlipped
        if (degreesFlipped > 90) {
            val alpha = ((degreesFlipped - 90) / 90f * MAX_SHADOW_ALPHA).toInt()
           // mShadowPaint.alpha = alpha
           //  canvas.drawPaint(mShadowPaint)
        }
    }

    /**
     * draw bottom/right half
     *
     * @param canvas
     */
    private fun drawNextHalf(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(if (isFlippingVertically) mBottomRect else mRightRect)

        val degreesFlipped = degreesFlipped
        val p = if (degreesFlipped > 90) mCurrentPage else mNextPage

        // if the view does not exist, skip drawing it
        if (p.valid) {
            setDrawWithLayer(p.v, true)
            drawChild(canvas, p.v, 0)
        }

        drawNextShadow(canvas)
        canvas.restore()
    }

    /**
     * draw bottom/right half shadow
     *
     * @param canvas
     */
    private fun drawNextShadow(canvas: Canvas) {
        val degreesFlipped = degreesFlipped
        if (degreesFlipped < 90) {
            val alpha = (Math.abs(degreesFlipped - 90) / 90f * MAX_SHADOW_ALPHA).toInt()
         //   mShadowPaint.alpha = alpha
           // canvas.drawPaint(mShadowPaint)
        }
    }

    private fun drawFlippingHalf(canvas: Canvas) {
        canvas.save()
        mCamera.save()

        val degreesFlipped = degreesFlipped

        if (degreesFlipped > 90) {
            canvas.clipRect(if (isFlippingVertically) mTopRect else mLeftRect)
            if (isFlippingVertically) {
                mCamera.rotateX(degreesFlipped - 180)
            } else {
                mCamera.rotateY(180 - degreesFlipped)
            }
        } else {
            canvas.clipRect(if (isFlippingVertically) mBottomRect else mRightRect)
            if (isFlippingVertically) {
                mCamera.rotateX(degreesFlipped)
            } else {
                mCamera.rotateY(-degreesFlipped)
            }
        }

        mCamera.getMatrix(mMatrix)

        positionMatrix()
        canvas.concat(mMatrix)

        setDrawWithLayer(mCurrentPage.v, true)
        drawChild(canvas, mCurrentPage.v, 0)

        drawFlippingShadeShine(canvas)

        mCamera.restore()
        canvas.restore()
    }

    /**
     * will draw a shade if flipping on the previous(top/left) half and a shine
     * if flipping on the next(bottom/right) half
     *
     * @param canvas
     */
    private fun drawFlippingShadeShine(canvas: Canvas) {
        val degreesFlipped = degreesFlipped
        if (degreesFlipped < 90) {
            val alpha = (degreesFlipped / 90f * MAX_SHINE_ALPHA).toInt()
            //mShinePaint.alpha = alpha
          //  canvas.drawRect(if (isFlippingVertically) mBottomRect else mRightRect,
              //      mShinePaint)
        } else {
            val alpha = (Math.abs(degreesFlipped - 180) / 90f * MAX_SHADE_ALPHA).toInt()
            //mShadePaint.alpha = alpha
            //canvas.drawRect(if (isFlippingVertically) mTopRect else mLeftRect,
              //      mShadePaint)
        }
    }

    /**
     * Enable a hardware layer for the view.
     *
     * @param v
     * @param drawWithLayer
     */
    private fun setDrawWithLayer(v: View?, drawWithLayer: Boolean) {
        if (isHardwareAccelerated) {
            if (v!!.layerType != View.LAYER_TYPE_HARDWARE && drawWithLayer) {
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else if (v.layerType != View.LAYER_TYPE_NONE && !drawWithLayer) {
                v.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    private fun positionMatrix() {
        mMatrix.preScale(0.25f, 0.25f)
        mMatrix.postScale(4.0f, 4.0f)
        mMatrix.preTranslate((-width / 2).toFloat(), (-height / 2).toFloat())
        mMatrix.postTranslate((width / 2).toFloat(), (height / 2).toFloat())
    }

    private fun postFlippedToPage(page: Int) {
        post {
            if (mOnFlipListener != null) {
                mOnFlipListener!!.onFlippedToPage(this@FlipView, page,
                        mAdapter!!.getItemId(page))
            }
        }
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = MotionEventCompat.getActionIndex(ev)
        val pointerId = MotionEventCompat.getPointerId(ev, pointerIndex)
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            mLastX = MotionEventCompat.getX(ev, newPointerIndex)
            mActivePointerId = MotionEventCompat.getPointerId(ev,
                    newPointerIndex)
            if (mVelocityTracker != null) {
                mVelocityTracker!!.clear()
            }
        }
    }

    /**
     *
     * @param deltaFlipDistance
     * The distance to flip.
     * @return The duration for a flip, bigger deltaFlipDistance = longer
     * duration. The increase if duration gets smaller for bigger values
     * of deltaFlipDistance.
     */
    private fun getFlipDuration(deltaFlipDistance: Int): Int {
        val distance = Math.abs(deltaFlipDistance).toFloat()
        return (MAX_SINGLE_PAGE_FLIP_ANIM_DURATION * Math.sqrt((distance / FLIP_DISTANCE_PER_PAGE).toDouble())).toInt()
    }

    /**
     *
     * @param velocity
     * @return the flip_page_one you should "land" on
     */
    private fun getNextPage(velocity: Int): Int {
        val nextPage: Int
        if (velocity > mMinimumVelocity) {
            nextPage = currentPageFloor
        } else if (velocity < -mMinimumVelocity) {
            nextPage = currentPageCeil
        } else {
            nextPage = currentPageRound
        }
        return Math.min(Math.max(nextPage, 0), pageCount - 1)
    }

    /**
     *
     * @return true if ended a flip
     */
    private fun endFlip(): Boolean {
        val wasflipping = mIsFlipping
        mIsFlipping = false
        mIsUnableToFlip = false
        mLastTouchAllowed = false

        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
        return wasflipping
    }

    /**
     *
     * @return true if ended a scroll
     */
    private fun endScroll(): Boolean {
        val wasScrolling = !mScroller!!.isFinished
        mScroller!!.abortAnimation()
        return wasScrolling
    }

    /**
     *
     * @return true if ended a peak
     */
    private fun endPeak(): Boolean {
        val wasPeaking = mPeakAnim != null
        if (mPeakAnim != null) {
            mPeakAnim!!.cancel()
            mPeakAnim = null
        }
        return wasPeaking
    }

    private fun peak(next: Boolean, once: Boolean) {
        val baseFlipDistance = (currentPage * FLIP_DISTANCE_PER_PAGE).toFloat()
        if (next) {
            mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance,
                    baseFlipDistance + FLIP_DISTANCE_PER_PAGE / 4)
        } else {
            mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance,
                    baseFlipDistance - FLIP_DISTANCE_PER_PAGE / 4)
        }
        mPeakAnim!!.interpolator = mPeakInterpolator
        mPeakAnim!!.addUpdateListener { animation -> setFlipDistance(animation.animatedValue as Float) }
        mPeakAnim!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                endPeak()
            }
        })
        mPeakAnim!!.duration = PEAK_ANIM_DURATION.toLong()
        mPeakAnim!!.repeatMode = ValueAnimator.REVERSE
        mPeakAnim!!.repeatCount = if (once) 1 else ValueAnimator.INFINITE
        mPeakAnim!!.start()
    }

    private fun trackVelocity(ev: MotionEvent) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)
    }

    private fun updateEmptyStatus() {
        val empty = mAdapter == null || pageCount == 0

        if (empty) {
            if (mEmptyView != null) {
                mEmptyView!!.visibility = View.VISIBLE
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
            }

        } else {
            if (mEmptyView != null) {
                mEmptyView!!.visibility = View.GONE
            }
            visibility = View.VISIBLE
        }
    }

    fun flipTo(page: Int) {
        if (page < 0 || page > pageCount - 1) {
            throw IllegalArgumentException("That flip_page_one does not exist")
        }
        endFlip()
        setFlipDistance((page * FLIP_DISTANCE_PER_PAGE).toFloat())
    }

    fun flipBy(delta: Int) {
        flipTo(currentPage + delta)
    }

    fun smoothFlipTo(page: Int) {
        if (page < 0 || page > pageCount - 1) {
            throw IllegalArgumentException("That flip_page_one does not exist")
        }
        val start = mFlipDistance.toInt()
        val delta = page * FLIP_DISTANCE_PER_PAGE - start

        endFlip()
        mScroller!!.startScroll(0, start, 0, delta, getFlipDuration(delta))
        invalidate()
    }

    fun smoothFlipBy(delta: Int) {
        smoothFlipTo(currentPage + delta)
    }

    /**
     * Hint that there is a next flip_page_one will do nothing if there is no next flip_page_one
     *
     * @param once
     * if true, only peak once. else peak until user interacts with
     * view
     */
    fun peakNext(once: Boolean) {
        if (currentPage < pageCount - 1) {
            peak(true, once)
        }
    }

    /**
     * Hint that there is a previous flip_page_one will do nothing if there is no
     * previous flip_page_one
     *
     * @param once
     * if true, only peak once. else peak until user interacts with
     * view
     */
    fun peakPrevious(once: Boolean) {
        if (currentPage > 0) {
            peak(false, once)
        }
    }

    /**
     * The OnFlipListener will notify you when a flip_page_one has been fully turned.
     *
     * @param onFlipListener
     */
    fun setOnFlipListener(onFlipListener: OnFlipListener) {
        mOnFlipListener = onFlipListener
    }

    /**
     * The OnOverFlipListener will notify of over flipping. This is a great
     * listener to have when implementing pull-to-refresh
     *
     * @param onOverFlipListener
     */
    fun setOnOverFlipListener(onOverFlipListener: OnOverFlipListener) {
        this.mOnOverFlipListener = onOverFlipListener
    }

    /**
     * @param emptyView
     * The view to show when either no adapter is set or the adapter
     * has no items. This should be a view already in the view
     * hierarchy which the FlipView will set the visibility of.
     */
    fun setEmptyView(emptyView: View) {
        mEmptyView = emptyView
        updateEmptyStatus()
    }

    companion object {

        // this will be the postion when there is not data
        private val INVALID_PAGE_POSITION = -1
        // "null" flip distance
        private val INVALID_FLIP_DISTANCE = -1

        private val PEAK_ANIM_DURATION = 600// in ms
        private val MAX_SINGLE_PAGE_FLIP_ANIM_DURATION = 300// in ms

        // for normalizing width/height
        private val FLIP_DISTANCE_PER_PAGE = 180
        private val MAX_SHADOW_ALPHA = 180// out of 255
        private val MAX_SHADE_ALPHA = 130// out of 255
        private val MAX_SHINE_ALPHA = 100// out of 255

        // value for no pointer
        private val INVALID_POINTER = -1

        // constant used by the attributes
        private val VERTICAL_FLIP = 0

        // constant used by the attributes
        private val HORIZONTAL_FLIP = 1
    }

}
