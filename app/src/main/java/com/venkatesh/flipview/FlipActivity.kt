package com.venkatesh.flipview

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.covacare.doctor.views.flipview.FlipView
import com.covacare.doctor.views.flipview.OverFlipMode
import com.venkatesh.flipview.adapter.FlipViewAdapter
import com.venkatesh.flipview.helper.FlipUtil
import com.venkatesh.flipview.helper.FlipUtil.Companion.chunks
import com.venkatesh.flipview.interfaces.IFlipAdapterListener
import com.venkatesh.flipview.model.FruitsModel
import kotlinx.android.synthetic.main.activity_flip.*
import kotlinx.android.synthetic.main.activity_flip.view.*

/**
 * Created by Venkatesh on 07/06/18.
 */
class FlipActivity : AppCompatActivity(), FlipView.OnOverFlipListener, FlipView.OnFlipListener, IFlipAdapterListener {
    override fun onAcceptClick(model: FruitsModel) {
        Toast.makeText(this, "" + model.fname, Toast.LENGTH_SHORT).show()
    }

    override fun onFlippedToPage(v: FlipView, position: Int, id: Long) {
        flipviewcounttext.text = (position + 1).toString() + "/" + (flipview.adapter as FlipViewAdapter).getSize()

    }

    override fun onOverFlip(v: FlipView, mode: OverFlipMode?, overFlippingPrevious: Boolean,
                            overFlipDistance: Float, flipDistancePerPage: Float) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flip)
        intialise()
    }

    fun intialise() {

        flipview.setOnFlipListener(this)
        flipview.overFlipMode = OverFlipMode.GLOW

        val list = mutableListOf<FruitsModel>()

        list.add(FruitsModel("Apple"))
        list.add(FruitsModel("Orange"))
        list.add(FruitsModel("Mango"))
        list.add(FruitsModel("Banana"))
        list.add(FruitsModel("Cherry"))
        list.add(FruitsModel("Coconut"))
        list.add(FruitsModel("Custard apple"))
        val finalist = chunks((list as
                ArrayList<FruitsModel>?)!!, 2)
        flipview.adapter = FlipViewAdapter(this, finalist, this)
        flipviewcounttext.text = (1).toString() + "/" + (flipview.adapter as FlipViewAdapter).getSize()

        flipview.peakNext(false)

    }


}

