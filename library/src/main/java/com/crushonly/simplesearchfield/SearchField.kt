package com.crushonly.simplesearchfield

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.databinding.*
import android.os.Handler
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import net.gotev.speech.Speech
import net.gotev.speech.SpeechDelegate
import net.gotev.speech.SpeechRecognitionNotAvailable
import java.lang.StringBuilder

/**
 * SearchField is a UI widget which provides features such Android speech recognition for input,
 * expose data binding adapters to subscribe to events such as text entry, as well as a convenience clear button
 */

@BindingMethods(
        BindingMethod(
                type = SearchField::class, attribute = "searchFieldTextAttrChanged", method = "setSearchFieldChangedListener"
        )
)
class SearchField : CoordinatorLayout {

    private lateinit var micButton: MicrophoneButton
    private lateinit var searchField: EditText
    private lateinit var clearButton: ImageButton
    private var osHandler = Handler()

    private val searchFieldId = hashCode()
    private val speech: Speech by lazy {
        // If Speech is not initialized, try initializing it before returning
        try {
            Speech.getInstance()
        } catch (e: Exception) {
            Speech.init(context)
            Speech.getInstance()
        }
    }

    //Placeholder for the underlying edit text fields hint
    private var placeholder: String
        get() = searchField.hint.toString()
        set(value) {
            searchField.hint = value
        }

    //Event binding listeners
    private var searchUpdateHandlers: ArrayList<SearchFieldUpdateHandler> = ArrayList()
    private var permissionDeniedHandlers: ArrayList<SpeechPermissionHandler> = ArrayList()
    private var listeningForSpeech: Boolean = false
        set(value) {
            field = value
            micButton.isActive = value
        }

    private var clearBtnEnabled: Boolean = false
        set(value) {
            if (value != field) {
                toggleClearButton(value)
            }
            field = value
        }

    /**
     * The search field companion object provides methods to expose binding adapters
     * for event listeners
     */
    companion object {

        @JvmStatic
        @BindingAdapter(value = ["onSearchUpdate"])
        fun setSearchUpdateHandler(view: SearchField, oldHandler: SearchField.SearchFieldUpdateHandler?, newHandler: SearchField.SearchFieldUpdateHandler?) {
            oldHandler?.let {
                view.removeSearchUpdateHandler(it)
            }
            newHandler?.let {
                view.addSearchUpdateHandler(it)
            }
        }

        @JvmStatic
        @BindingAdapter(value = ["onSpeechPermissionDenied"])
        fun setPermissionDeniedHandler(view: SearchField, oldHandler: SearchField.SpeechPermissionHandler?, newHandler: SearchField.SpeechPermissionHandler?) {
            oldHandler?.let {
                view.removeSpeechPermissionHandler(it)
            }
            newHandler?.let {
                view.addSpeechPermissionHandler(it)
            }
        }

        @JvmStatic
        @InverseBindingAdapter(attribute = "searchFieldText")
        fun getText(view: SearchField) = view.getSearchText()

        @JvmStatic
        @BindingAdapter("searchFieldText")
        fun setText(view: SearchField, text: String?) {
            text?.let {
                if (view.getSearchText() != text) {
                    view.setSearchText(text)
                }
            }
        }
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

    private fun init(attrs: AttributeSet?, defStyle: Int) {

        layoutParams = CoordinatorLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SearchField, defStyle, 0)
        background = typedArray.getDrawable(R.styleable.SearchField_searchFieldBackground)
        elevation = typedArray.getDimension(R.styleable.SearchField_searchFieldElevation, 0.0F)
        clipToPadding = false
        descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        isFocusableInTouchMode = true

        layoutEditText(typedArray)
        layoutClearButton(typedArray)
        layoutMicButton(typedArray)
        typedArray.recycle()

        searchField.addTextChangedListener(SearchWatcher())
        micButton.setOnClickListener {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO).let {
                if (it == PackageManager.PERMISSION_DENIED) {
                    audioPermissionDenied()
                }
            }
            if (listeningForSpeech) {
                // net.gotev.speech.Speech handles throttling of stopListening() calls, and returns
                // partial results.
                speech.stopListening()
            } else {
                try {
                    speech.startListening(SearchListener())
                } catch (e: SpeechRecognitionNotAvailable) {
                    Log.e("speech", "Speech recognition is not available on this device", e)
                }
            }
        }

        clearButton.setOnClickListener {
            searchField.setText("")
        }

    }

    override fun onMeasureChild(child: View?, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int) {
        super.onMeasureChild(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
        if (child == micButton) {
            val h = View.MeasureSpec.getSize(parentHeightMeasureSpec)
            micButton.layoutParams.width = (h * 0.75).toInt()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) {
            micButton.layoutParams.width = (height * 0.75).toInt()
            clearButton.layoutParams.width = (height * 0.75).toInt()
            if (!searchField.text.isNullOrBlank()) {
                osHandler.postDelayed({
                    toggleClearButton(true)
                    listener?.onChange()
                }, 200)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        micButton.setOnClickListener(null)
        listener = null
        speech.shutdown()
    }

    //Methods for setting up underlying views
    private fun layoutEditText(typedArray: TypedArray) {
        searchField = EditText(context)
        placeholder = typedArray.getString(R.styleable.SearchField_searchHintText)
        val textColor = typedArray.getColor(R.styleable.SearchField_searchTextColor, context.resources.colorFromResId(android.R.color.black))
        val hintTextColor = typedArray.getColor(R.styleable.SearchField_searchHintTextColor, context.resources.colorFromResId(android.R.color.darker_gray))
        val textSize = typedArray.getFloat(R.styleable.SearchField_searchTextSize, 20.0F)
        searchField.setTextColor(textColor)
        searchField.setHintTextColor(hintTextColor)
        searchField.textSize = textSize
        searchField.id = searchFieldId
        searchField.backgroundTintList = ColorStateList.valueOf(context.resources.colorFromResId(android.R.color.transparent))
        searchField.setSingleLine(true)
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        params.leftMargin = 24
        params.rightMargin = 24
        searchField.layoutParams = params
        searchField.filters = arrayOf(InputFilter.LengthFilter(100))
        addView(searchField)
    }

    private fun layoutMicButton(typedArray: TypedArray) {
        micButton = MicrophoneButton(context)
        micButton.micColor = typedArray.getColor(R.styleable.SearchField_searchFieldMicColor, context.resources.colorFromResId(android.R.color.background_dark))
        micButton.activeMicColor = typedArray.getColor(R.styleable.SearchField_searchFieldActiveMicColor, context.resources.colorFromResId(android.R.color.white))

        micButton.layoutParams = CoordinatorLayout.LayoutParams(height, LayoutParams.MATCH_PARENT)
        val params = micButton.layoutParams as CoordinatorLayout.LayoutParams
        params.height = LayoutParams.MATCH_PARENT
        params.width = height
        params.anchorId = searchFieldId
        params.anchorGravity = Gravity.END or Gravity.CENTER_VERTICAL
        micButton.layoutParams = params
        addView(micButton)
    }

    private fun layoutClearButton(@Suppress("UNUSED_PARAMETER") typedArray: TypedArray) {
        clearButton = ImageButton(context)
        clearButton.setImageResource(R.drawable.ic_close)
        clearButton.background = context.getDrawable(android.R.color.transparent)
        clearButton.alpha = 0.0F
        clearButton.isEnabled = false
        clearButton.scaleX = 0.0F
        clearButton.scaleY = 0.0F
        clearButton.imageTintList = ColorStateList.valueOf(context.resources.colorFromResId(android.R.color.black))

        val params = layoutParams as CoordinatorLayout.LayoutParams
        params.height = LayoutParams.MATCH_PARENT
        params.width = height
        params.gravity = Gravity.CENTER or Gravity.END
        clearButton.layoutParams = params

        addView(clearButton)
    }

    private var listener: InverseBindingListener? = null

    fun setSearchFieldChangedListener(bindingListener: InverseBindingListener) {
        listener = bindingListener
    }

    /**
     * Get the current search text
     */
    fun getSearchText(): String {
        return searchField.text.toString()
    }

    /**
     * Set search text
     */
    fun setSearchText(text: String) {
        searchField.setText(text)
    }

    /*
    * Methods for adding event binding listeners
    */
    /**
     * Add an event listener to watch for input changes
     *
     * @param handler A handler instance to call when input changes
     */
    fun addSearchUpdateHandler(handler: SearchFieldUpdateHandler) {
        searchUpdateHandlers.add(handler)
    }

    /**
     * Remove a search update handler
     *
     * @param handler A handler to remove
     */
    fun removeSearchUpdateHandler(handler: SearchFieldUpdateHandler) {
        searchUpdateHandlers.remove(handler)
    }

    /**
     * Add an event listener to watch for speech permission issues
     *
     * @param handler A handler instance to call when the app encounters speech permission issues
     */
    fun addSpeechPermissionHandler(handler: SpeechPermissionHandler) {
        permissionDeniedHandlers.add(handler)
    }

    /**
     * Remove a speech permission handler
     *
     * @param handler A handler to remove
     */
    fun removeSpeechPermissionHandler(handler: SpeechPermissionHandler) {
        permissionDeniedHandlers.remove(handler)
    }

    /**
     * Group together operations that should happen on a full or partial search result.
     *
     */
    fun handleSpeechResults(result: String?) {
        listeningForSpeech = false
        micButton.onStopListening()
        result?.let {
            searchField.setText(it)
        }
    }

    /**
     * Expose speech.stopListening() to viewmodels
     */
    fun stopSpeechRecognition() {
        speech.stopListening()
    }

    private fun queryUpdate(query: String) {
        if (searchUpdateHandlers.isNotEmpty()) {
            for (handler in searchUpdateHandlers) {
                handler.onUpdate(query)
            }
        }
        listener?.onChange()
        clearBtnEnabled = !query.isEmpty()
    }

    private fun audioPermissionDenied() {
        if (permissionDeniedHandlers.isNotEmpty()) {
            for (handler in permissionDeniedHandlers) {
                handler.onPermissionDenied(PackageManager.PERMISSION_DENIED.toString())
            }
        }
    }

    private fun toggleClearButton(isEnabled: Boolean) {
        val distance = (micButton.width * 1.5).toInt()
        ValueAnimator.ofInt(distance).apply {
            duration = if (isEnabled) 400L else 200L
            interpolator = if (isEnabled) SpringInterpolator() else null
            addUpdateListener {
                val params = searchField.layoutParams as CoordinatorLayout.LayoutParams
                val intValue = it.animatedValue as Int
                val animValue = if (isEnabled) intValue else distance - intValue
                params.rightMargin = animValue
                searchField.layoutParams = params
                val animFraction = if (isEnabled) it.animatedFraction else 1 - it.animatedFraction
                clearButton.scaleX = animFraction
                clearButton.scaleY = animFraction
                clearButton.alpha = animFraction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    clearButton.isEnabled = isEnabled
                }
            })
        }.start()
    }

    /**
     * Callback interface handlers must implement to receive text updates
     */
    interface SearchFieldUpdateHandler {
        fun onUpdate(text: String)
    }

    /**
     * Callback interface handlers must implement to handle permission denied errors
     */
    interface SpeechPermissionHandler {
        fun onPermissionDenied(message: String)
    }


    /**
     * TextWatcher for search field
     */
    private inner class SearchWatcher : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            queryUpdate(s.toString())
        }
    }

    //Inner class for handling speech callbacks
    private inner class SearchListener : SpeechDelegate {
        private val TAG: String = "speech"

        /**
         * Invoked when the speech recognition is started.
         */
        override fun onStartOfSpeech() {
            Log.i(TAG, "speech recognition is now active")
            listeningForSpeech = true
        }

        /**
         * Invoked when there are partial speech results (e.g. stopListening() called).
         * @param results list of strings. This is ensured to be non null and non empty.
         */
        override fun onSpeechPartialResults(results: MutableList<String>?) {
            val str = StringBuilder()
            results?.let {
                for (result in it) {
                    str.append(result)
                }
            }
            Log.i(TAG, "speech partial result: ${str.toString().trim()}")
            handleSpeechResults(str.toString().trim())
        }

        /**
         * The sound level in the audio stream has changed.
         * There is no guarantee that this method will be called.
         * @param value the new RMS dB value
         */
        override fun onSpeechRmsChanged(value: Float) {
            Log.d(TAG, "RMS Changed ${value}")
            micButton.onRmsChanged(value)
        }

        /**
         * Invoked when there is a speech result
         * @param result string resulting from speech recognition.
         * This is ensured to be non null.
         */
        override fun onSpeechResult(result: String?) {
            Log.d(TAG, "Result: $result")
            handleSpeechResults(result)

        }

    }
}