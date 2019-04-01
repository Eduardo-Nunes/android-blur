package com.eduardo.blur

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main.*
import androidx.core.widget.NestedScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEFAULT_RADIUS = 15
private const val DEFAULT_MARGIN = 24F

class MainActivity : AppCompatActivity() {

    private var maxRadius = DEFAULT_RADIUS
        set(value) {
            field = value
            radiusTitleTextView.text = getString(R.string.radius, "$maxRadius px")
            val scrollPercentage = getViewScrollPercentage(scrollView.scrollY)
            setRadius(calculateRadius(scrollPercentage))
        }

    private var maxMargin = DEFAULT_MARGIN
        set(value) {
            field = value
            val scrollPercentage = getFullScrollPercentage(scrollView.scrollY)
            setMargin(calculateMargin(scrollPercentage))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initListeners()
        initViews()
    }

    private fun initViews() {
        selectImage()
        maxMargin = resources.getDimension(R.dimen.margin)
        descriptionTextView.text = getString(R.string.lorem_ipslum)
        (bluredArea.layoutParams as ViewGroup.MarginLayoutParams).topMargin = (windowHeight * 0.8).toInt()
        radiusSeekBar.progress = DEFAULT_RADIUS

    }

    private fun selectImage() {
        backgroundImageView.setImageResource(R.drawable.moonlight)
    }

    private val windowHeight: Int
        get() {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            return metrics.heightPixels
        }

    private fun initListeners() {
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            setRadius(calculateRadius(getViewScrollPercentage(scrollY)))
            setMargin(calculateMargin(getFullScrollPercentage(scrollY)))
        })

        switch1.setOnCheckedChangeListener { buttonView, isChecked ->
            buttonView.text = if (isChecked) getString(R.string.size_dinamico) else getText(R.string.size_estatico)
            setVisibility(isChecked)
        }
        radiusSeekBar.setOnSeekListener(::setMaxRadiusValue)
    }

    private fun getViewScrollPercentage(scroll: Int): Float {
        val screenHeight = (windowHeight * 0.8).toInt()
        var scrollPercentage = (scroll * 100).toFloat() / screenHeight.toFloat()
        if (scrollPercentage > 100) scrollPercentage = 100F
        return scrollPercentage
    }

    private fun getFullScrollPercentage(scroll: Int): Float {
        val screenHeight = (windowHeight * 0.8).toInt()
        return (scroll * 100).toFloat() / screenHeight.toFloat()
    }

    private fun calculateRadius(scrollPercentage: Float): Float = (scrollPercentage * maxRadius) / 100
    private fun calculateMargin(scrollPercentage: Float): Int = ((100 - scrollPercentage) * maxMargin).roundToInt() / 100

    private inline fun SeekBar.setOnSeekListener(crossinline progressCallback: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                progressCallback(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setVisibility(show: Boolean) {
        if (blurView.isShown == show) return
        blurView.visibility = if (show) VISIBLE else GONE
    }

    private fun setRadius(progress: Float) {
        if (blurView.getBlurRadius() != progress) {
            CoroutineScope(Dispatchers.Main).launch {
                blurView.post {
                    blurView.setBlurRadius(progress)
                }
            }
        }
    }

    private fun setMaxRadiusValue(progress: Int) {
        maxRadius = progress
    }

    private fun setMargin(calculateMargin: Int) {
        with(backgroundImageView) {
            val marginLayoutParams = (layoutParams as ViewGroup.MarginLayoutParams)
            if (marginLayoutParams.marginStart != calculateMargin) {
                marginLayoutParams.setMargins(
                    calculateMargin,
                    calculateMargin,
                    calculateMargin,
                    calculateMargin
                )
                post {
                    layoutParams = marginLayoutParams
                }
            }
        }
    }
}
