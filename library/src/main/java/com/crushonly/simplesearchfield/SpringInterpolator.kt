package com.crushonly.simplesearchfield

import android.view.animation.Interpolator

/**
 * Created by tom.stoepker on 1/21/18.
 */
class SpringInterpolator constructor(private var amplitude: Double = 0.17,
                                     private var frequency: Double = 6.0): Interpolator {

    override fun getInterpolation(time: Float): Float {
        return (-1.0 * Math.pow(Math.E, -time / amplitude) *
                Math.cos(frequency * time) + 1).toFloat()
    }
}