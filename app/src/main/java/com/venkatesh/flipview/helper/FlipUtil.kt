package com.venkatesh.flipview.helper

import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.venkatesh.flipview.model.FruitsModel
import java.lang.reflect.Type
import java.util.*

/**
 * Created by Venkatesh on 07/06/18.
 */
class FlipUtil {

    companion object {
        fun <T> chunks(bigList: ArrayList<T>, n: Int): MutableList<Any> {
            val chunks = ArrayList<Any>().toMutableList()

            var i = 0
            while (i < bigList.size) {
                val chunk = bigList.subList(i, Math.min(bigList.size, i + n))
                chunks.add(chunk)
                i += n
            }

            return chunks
        }

        inline fun <reified T> parseArray(json: String, typeToken: Type): T {
            val gson = GsonBuilder().create()
            return gson.fromJson<T>(json, typeToken)
        }

        fun returnResult(list: Any?): MutableList<FruitsModel> {
            val type = object : TypeToken<MutableList<FruitsModel>>() {}.type
            val result: MutableList<FruitsModel> =
                    parseArray<MutableList<FruitsModel>>(json = Gson().toJson(list),
                            typeToken = type)
            return result
        }

        fun getRandomColor(): Int {
            val rnd = Random()
            return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        }

    }
}