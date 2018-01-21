package com.crushonly.simplesearchfield

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.support.v7.widget.AppCompatImageButton
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Created by tom.stoepker on 1/21/18.
 */
class MicrophoneButton: FrameLayout {

    private lateinit var meter: Circle
    private lateinit var highlight: Circle
    private lateinit var button: AppCompatImageButton

    var activeMicColor: Int = 0xFFFFFF
    var micColor: Int = 0x000000
        set(value) {
            field = value
            button.imageTintList = ColorStateList.valueOf(value)
        }
    private var animDuration = 300L

    var isActive: Boolean = false
        set(value) {
            field = value
            layoutForActiveState(value)
        }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    fun onRmsChanged(db: Float) {
        val multiplier = if (Math.abs(db) < 7) 22 else 10
        meter.alpha = 0.6F
        val scale = if (db > 0) (db * multiplier) / 100 else 0F
        meter.animate().scaleY(scale).scaleX(scale).apply {
            duration = 90L
        }.start()
    }

    fun onStopListening() {
        meter.animate()
                .scaleY(0.0F)
                .scaleX(0.0F)
                .apply {
                    duration = 90L
                }
                .start()
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        button = AppCompatImageButton(context)
        button.setImageResource(R.drawable.ic_mic)
        button.background = context.resources.getDrawable(android.R.color.transparent, null)

        val a = context.obtainStyledAttributes(attrs, R.styleable.MicrophoneButton, defStyle, 0)

        try {
            meter = Circle(context)
            meter.setHighlightColor(context.resources.colorFromResId(android.R.color.background_dark))
            meter.scaleY = 0.0F
            meter.scaleX = 0.0F
            addView(meter)

            highlight = Circle(context)
            highlight.setHighlightColor(a.getColor(R.styleable.MicrophoneButton_highlightColor, context.resources.colorFromResId(android.R.color.background_light)))
            highlight.alpha = 0.0F
            highlight.scaleX = 0.0F
            highlight.scaleY = 0.0F
            addView(highlight)

            micColor = a.getColor(R.styleable.MicrophoneButton_micColor, context.resources.colorFromResId(android.R.color.white))
            activeMicColor = a.getColor(R.styleable.MicrophoneButton_micColorActive, context.resources.colorFromResId(android.R.color.white))
            button.imageTintList = ColorStateList.valueOf(micColor)
            addView(button)
        } finally {
            a.recycle()
        }
    }

    private fun layoutForActiveState(isActive: Boolean) {
        val aVal = if (isActive) 0.8F else 0.0F
        val alpha = if (isActive) 1.0F else 0.0F
        highlight.animate().alpha(alpha).scaleX(aVal).scaleY(aVal).apply {
            duration = animDuration
            interpolator = SpringInterpolator(0.17, 6.0)
        }.start()
        val fromColor = if (isActive) micColor else activeMicColor
        val toColor = if (isActive) activeMicColor else micColor
        ValueAnimator.ofArgb(fromColor, toColor).apply {
            duration = animDuration
            addUpdateListener {
                val animVal = it.animatedValue as Int
                button.imageTintList = ColorStateList.valueOf(animVal)
            }
        }.start()
        if(isActive) {
            meter.scaleY = 0.0F
            meter.scaleX = 0.0F
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        button.setOnClickListener(l)
    }

}