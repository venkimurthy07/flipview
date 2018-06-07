package com.venkatesh.flipview.flipview

import android.annotation.TargetApi
import android.os.Build
import android.util.SparseArray
import android.view.View

/**
 * Created by Venkatesh on 07/06/18.
 */
class Recycler {

    /** Unsorted views that can be used by the adapter as a convert view.  */
    private var scraps: Array<SparseArray<Scrap>>? = null
    private var currentScraps: SparseArray<Scrap>? = null

    private var viewTypeCount: Int = 0

    internal class Scrap(var v: View, var valid: Boolean)

    internal fun setViewTypeCount(viewTypeCount: Int) {
        if (viewTypeCount < 1) {
            throw IllegalArgumentException("Can't have a viewTypeCount < 1")
        }
        // do nothing if the view type count has not changed.
        if (currentScraps != null && viewTypeCount == scraps!!.size) {
            return
        }

        val scrapViews = arrayOfNulls<SparseArray<*>>(viewTypeCount)
        for (i in 0 until viewTypeCount) {
            scrapViews[i] = SparseArray<Scrap>()
        }
        this.viewTypeCount = viewTypeCount
        currentScraps = scrapViews[0] as SparseArray<Scrap>?
        this.scraps = scrapViews as Array<SparseArray<Scrap>>
    }

    /** @return A view from the ScrapViews collection. These are unordered.
     */
    internal fun getScrapView(position: Int, viewType: Int): Scrap? {
        if (viewTypeCount == 1) {
            return retrieveFromScrap(currentScraps, position)
        } else if (viewType >= 0 && viewType < scraps!!.size) {
            return retrieveFromScrap(scraps!![viewType], position)
        }
        return null
    }

    /**
     * Put a view into the ScrapViews list. These views are unordered.
     *
     * @param scrap
     * The view to add
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    internal fun addScrapView(scrap: View, position: Int, viewType: Int) {
        // create a new Scrap
        val item = Scrap(scrap, true)

        if (viewTypeCount == 1) {
            currentScraps!!.put(position, item)
        } else {
            scraps!![viewType].put(position, item)
        }
        if (Build.VERSION.SDK_INT >= 14) {
            scrap.setAccessibilityDelegate(null)
        }
    }

    internal fun invalidateScraps() {
        for (array in scraps!!) {
            for (i in 0 until array.size()) {
                array.valueAt(i).valid = false
            }
        }
    }

    companion object {

        internal fun retrieveFromScrap(scrapViews: SparseArray<Scrap>?, position: Int): Scrap? {
            val size = scrapViews!!.size()
            if (size > 0) {
                // See if we still have a view for this position.
                var result: Scrap? = scrapViews.get(position, null)
                if (result != null) {
                    scrapViews.remove(position)
                    return result
                }
                val index = size - 1
                result = scrapViews.valueAt(index)
                scrapViews.removeAt(index)
                result!!.valid = false
                return result
            }
            return null
        }
    }

}
