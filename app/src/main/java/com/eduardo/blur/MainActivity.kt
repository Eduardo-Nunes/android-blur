package com.eduardo.blur

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_main.*
import androidx.core.view.drawToBitmap
import androidx.palette.graphics.Palette
import java.util.Random
import java.util.Date
import android.view.MenuItem


private const val DEFAULT_SIZE = 100
private const val DEFAULT_RADIUS = 50
private const val DEFAULT_ALPHA = 50

class MainActivity : AppCompatActivity() {
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
        R.drawable.avengers,
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
                setOverlayColor(alphaSeekBar.progress)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        selectImage()
        descriptionTextView.text = getString(R.string.lorem_ipslum)
        sampleSizeSeekBar.progress = DEFAULT_SIZE
        radiusSeekBar.progress = DEFAULT_RADIUS
        alphaSeekBar.progress = DEFAULT_ALPHA
    }

    private fun selectImage() {
        val randomInteger = (0 until exampleImages.size).shuffled(Random(Date().time)).first()
        backgroundImageView.setImageResource(exampleImages[randomInteger])
    }

    private fun initListeners() {
        sampleSizeSeekBar.setOnSeekListener(::setSampleSize)
        radiusSeekBar.setOnSeekListener(::setRadius)
        alphaSeekBar.setOnSeekListener(::setOverlayColor)
    }

    private inline fun SeekBar.setOnSeekListener(crossinline progressCallback: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                progressCallback(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setSampleSize(progress: Int) {
        val floatSize = progress.toFloat() / 10
        sampleSizeTextView.text = getString(R.string.sample_size, "$floatSize f")
        if (floatSize > 0) blurView.setDownsampleFactor(floatSize)
    }

    private fun setRadius(progress: Int) {
        radiusTitleTextView.text = getString(R.string.radius, "$progress px")
        blurView.setBlurRadius(progress.toFloat())
    }

    private fun setOverlayColor(progress: Int) {
        fun getColorWithAlpha(color: Int, ratio: Float): Int {
            var newColor = 0
            val alpha = Math.round(Color.alpha(color) * ratio)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            newColor = Color.argb(alpha, r, g, b)
            return newColor
        }

        val alphaRatio = progress.toFloat() / 100
        alphaTitleTextView.text = getString(R.string.alpha_text, "$alphaRatio f")
        backgroundImageView.post {
            Palette.Builder(backgroundImageView.drawToBitmap()).generate { palette ->
                val defaultColor = resources.getColor(android.R.color.transparent)
                val vibrantColor = palette?.getVibrantColor(defaultColor)
                val darkVibrantColor = palette?.getDarkVibrantColor(defaultColor)
                val lightVibrantColor = palette?.getLightVibrantColor(defaultColor)
                val dominantColor = palette?.getDominantColor(defaultColor)
                val mutedColor = palette?.getMutedColor(defaultColor)
                val lightMutedColor = palette?.getLightMutedColor(defaultColor)
                val darkMutedColor = palette?.getDarkMutedColor(defaultColor)
                blurView.post {
                    val alphaColored = getColorWithAlpha(dominantColor ?: defaultColor, alphaRatio)
                    blurView.setOverlayColor(alphaColored)
                }

            }
        }
    }
}
