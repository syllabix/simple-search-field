package com.crushonly.simplesearchfield

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Created by tom.stoepker on 1/21/18.
 */
class Circle: View {

    private lateinit var paint: Paint
    private var highlightColor: Int = 0
    private var diameter = 0.0f
    private var radius = 0.0f
    private var cx = 0.0f
    private var cy = 0.0f

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, @Suppress("UNUSED_PARAMETER") defStyle: Int) {
        val set = arrayOf(android.R.attr.backgroundTint).toIntArray()
        val a = context.obtainStyledAttributes(attrs, set)

        try {
            highlightColor = a.getColor(0, context.resources.colorFromResId(android.R.color.black))
        } finally {
            a.recycle()
        }

        paint = Paint()
        paint.style = Paint.Style.FILL
        paint.color = highlightColor
        paint.isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val xpad = (paddingLeft + paddingRight).toFloat()
        val ypad = (paddingTop + paddingBottom).toFloat()

        val ww = w.toFloat() - xpad
        val hh = h.toFloat() - ypad

        diameter = Math.min(ww, hh)
        radius = diameter / 2 - 4
        cx = (w / 2).toFloat()
        cy = (h / 2).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    fun setHighlightColor(color: Int) {
        highlightColor = color
        paint.color = highlightColor
        invalidate()
    }

}