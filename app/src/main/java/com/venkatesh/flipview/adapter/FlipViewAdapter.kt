package com.venkatesh.flipview.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.venkatesh.flipview.R
import com.venkatesh.flipview.R.id.fliptext_one
import com.venkatesh.flipview.helper.FlipUtil.Companion.getRandomColor
import com.venkatesh.flipview.helper.FlipUtil.Companion.returnResult
import com.venkatesh.flipview.interfaces.IFlipAdapterListener
import com.venkatesh.flipview.model.FruitsModel
import java.lang.reflect.Type
import kotlinx.android.synthetic.main.flipview_item.*

/**
 * Created by Venkatesh on 07/06/18.
 */
class FlipViewAdapter constructor(context: Context, items: MutableList<Any>, var flipListener: IFlipAdapterListener) : BaseAdapter(), View.OnClickListener {
    override fun onClick(v: View?) {

        when (v?.id) {
            R.id.flip_one_btn -> {
                flipListener.onAcceptClick(returnResult(mList?.get(v.tag as Int)).get(0))
                Log.e("Flip adapte", "" + returnResult(mList?.get(v.tag as Int)).get(0).fname)
            }
            R.id.flip_two_btn -> {
                flipListener.onAcceptClick(returnResult(mList?.get(v.tag as Int)).get(1))

                Log.e("Flip adapte", "" + returnResult(mList?.get(v.tag as Int)).get(1).fname)

            }
        }
    }

    lateinit var mList: MutableList<Any>
    lateinit var mContext: Context


    fun getSize():Int{
        return mList.size
    }
    init {
        this.mContext = context
        this.mList = items
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

        var convertView = convertView
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.flipview_item, parent, false)
        }

        val mylist = mList.get(position) as List<FruitsModel>
        val frametwo = convertView?.findViewById<FrameLayout>(R.id.frame_two)
        val flip_one_btn = convertView?.findViewById<Button>(R.id.flip_one_btn)
        val flip_two_btn = convertView?.findViewById<Button>(R.id.flip_two_btn)

        flip_one_btn?.tag = position
        flip_two_btn?.tag = position
        val result = returnResult(mylist)

        flip_one_btn?.setOnClickListener(this)
        flip_two_btn?.setOnClickListener(this)
        if (result.size > 1) {
            frametwo?.visibility = View.VISIBLE
            val flipviewone = convertView?.findViewById<TextView>(R.id.fliptext_one)
            flipviewone?.text = result.get(0).fname
            val flipviewtwo = convertView?.findViewById<TextView>(R.id.fliptext_two)
            flipviewtwo?.text = result.get(1).fname

        } else {
            frametwo?.visibility = View.INVISIBLE
            val flipviewone = convertView?.findViewById<TextView>(R.id.fliptext_one)
            flipviewone?.text = result.get(0).fname
        }
        return convertView!!
    }


    override fun getItem(position: Int): Any {
        return mList.get(position)
    }


    override fun getItemId(position: Int): Long {
        return -1
    }

    override fun getCount(): Int {
        return mList.size
    }


}