package com.eduardo.blur

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main.*
import java.util.Random
import java.util.Date
import androidx.core.widget.NestedScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val DEFAULT_RADIUS = 25

class MainActivity : AppCompatActivity() {

    private var maxRadius = DEFAULT_RADIUS
        set(value) {
            field = value
            radiusTitleTextView.text = getString(R.string.radius, "$maxRadius px")
            val scrollPercentage = getScrollPercentage(scrollView.scrollY)
            setRadius(calculateRadius(scrollPercentage))
        }

    private val exampleImages = listOf(
        R.drawable.millenium,
        R.drawable.moonlight,
        R.drawable.brooklin,
        R.drawable.denzel,
        R.drawable.first,
        R.drawable.gay,
        R.drawable.kakie,
        R.drawable.loving,
        R.drawable.startstar,
        R.drawable.dcstrange,
        R.drawable.donkeykong,
        R.drawable.fantastic,
        R.drawable.matrix2,
        R.drawable.n1,
        R.drawable.vol1,
        R.drawable.vol2
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initListeners()
        initViews()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.blur_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_change_image -> {
                selectImage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        selectImage()
        descriptionTextView.text = getString(R.string.lorem_ipslum)
        (bluredArea.layoutParams as ViewGroup.MarginLayoutParams).topMargin = (windowHeight * 0.8).toInt()
        radiusSeekBar.progress = DEFAULT_RADIUS
    }

    private fun selectImage() {
        val randomInteger = (0 until exampleImages.size).shuffled(Random(Date().time)).first()
        backgroundImageView.setImageResource(exampleImages[randomInteger])
    }

    private val windowHeight: Int
        get() {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            return metrics.heightPixels
        }

    private fun initListeners() {
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            val scrollPercentage = getScrollPercentage(scrollY)
            setRadius(calculateRadius(scrollPercentage))
        })

        switch1.setOnCheckedChangeListener { buttonView, isChecked ->
            buttonView.text = if (isChecked) getString(R.string.size_dinamico) else getText(R.string.size_estatico)
            setVisibility(isChecked)
        }
        radiusSeekBar.setOnSeekListener(::setMaxRadiusValue)
    }

    private fun getScrollPercentage(scroll: Int): Float {
        val screenHeight = (windowHeight * 0.8).toInt()
        var scrollPercentage = (scroll * 100).toFloat() / screenHeight.toFloat()
        if (scrollPercentage > 100) scrollPercentage = 100F
        return scrollPercentage
    }

    private fun calculateRadius(scrollPercentage: Float): Float = (scrollPercentage * maxRadius) / 100

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
}
