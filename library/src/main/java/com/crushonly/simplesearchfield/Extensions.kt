package com.crushonly.simplesearchfield

import android.content.res.Resources
import android.os.Build

/**
 * Created by tom.stoepker on 1/21/18.
 */

fun Resources.colorFromResId(resId: Int, theme: Resources.Theme? = null) =
        if (Build.VERSION.SDK_INT < 23) getColor(resId) else getColor(resId, theme)