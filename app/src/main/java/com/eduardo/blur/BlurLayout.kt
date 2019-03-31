package com.eduardo.blur

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.renderscript.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver

class BlurLayout(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var mDownsampleFactor: Float = 0.toFloat() // default 4
    private var mOverlayColor: Int = 0 // default #aaffffff
    private var mBlurRadius: Float = 0.toFloat() // default 10dp (0 < r <= 25)

    private var mDirty: Boolean = false
    private var mBitmapToBlur: Bitmap? = null
    private var mBlurredBitmap: Bitmap? = null
    private var mBlurringCanvas: Canvas? = null
    private var mRenderScript: RenderScript? = null
    private var mBlurScript: ScriptIntrinsicBlur? = null
    private var mBlurInput: Allocation? = null
    private var mBlurOutput: Allocation? = null
    private var mIsRendering: Boolean = false
    private val mPaint: Paint
    private val mRectSrc = Rect()
    private val mRectDst = Rect()
    // mDecorView should be the root view of the activity (even if you are on a different window like a dialog)
    private var mDecorView: View? = null
    // If the view is on different root view (usually means we are on a PopupWindow),
    // we need to manually call invalidate() in onPreDraw(), otherwise we will not be able to see the changes
    private var mDifferentRoot: Boolean = false

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        val locations = IntArray(2)
        var oldBmp = mBlurredBitmap
        val decor = mDecorView
        if (decor != null && isShown && prepare()) {
            val redrawBitmap = mBlurredBitmap != oldBmp
            oldBmp = null
            decor.getLocationOnScreen(locations)
            var x = -locations[0]
            var y = -locations[1]

            getLocationOnScreen(locations)
            x += locations[0]
            y += locations[1]

            // just erase transparent
            mBitmapToBlur!!.eraseColor(mOverlayColor and 0xffffff)

            val rc = mBlurringCanvas!!.save()
            mIsRendering = true
            RENDERING_COUNT++
            try {
                mBlurringCanvas!!.scale(1f * mBitmapToBlur!!.width / width, 1f * mBitmapToBlur!!.height / height)
                mBlurringCanvas!!.translate((-x).toFloat(), (-y).toFloat())
                if (decor.background != null) {
                    decor.background.draw(mBlurringCanvas!!)
                }
                decor.draw(mBlurringCanvas)
            } catch (e: StopException) {
            } finally {
                mIsRendering = false
                RENDERING_COUNT--
                mBlurringCanvas!!.restoreToCount(rc)
            }

            blur(mBitmapToBlur, mBlurredBitmap)

            if (redrawBitmap || mDifferentRoot) {
                invalidate()
            }
        }

        true
    }

    private val activityDecorView: View?
        get() {
            var ctx: Context? = context
            var i = 0
            while (i < 4 && ctx != null && ctx !is Activity && ctx is ContextWrapper) {
                ctx = ctx.baseContext
                i++
            }
            return if (ctx is Activity) {
                ctx.window.decorView
            } else {
                null
            }
        }

    init {

        val a = context.obtainStyledAttributes(attrs, R.styleable.BlurLayout)
        mBlurRadius = a.getDimension(
            R.styleable.BlurLayout_blurRadius,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, context.resources.displayMetrics)
        )
        mDownsampleFactor = a.getFloat(R.styleable.BlurLayout_downSampleFactor, 4f)
        mOverlayColor = a.getColor(R.styleable.BlurLayout_overlayColor, 0xffffff0)
        a.recycle()

        mPaint = Paint()
    }

    fun setBlurRadius(radius: Float) {
        if (mBlurRadius != radius) {
            mBlurRadius = radius
            mDirty = true
            invalidate()
        }
    }

    fun setDownsampleFactor(factor: Float) {
        if (factor <= 0) {
            throw IllegalArgumentException("Downsample factor must be greater than 0.")
        }

        if (mDownsampleFactor != factor) {
            mDownsampleFactor = factor
            mDirty = true // may also change blur radius
            releaseBitmap()
            invalidate()
        }
    }

    private fun releaseBitmap() {
        if (mBlurInput != null) {
            mBlurInput!!.destroy()
            mBlurInput = null
        }
        if (mBlurOutput != null) {
            mBlurOutput!!.destroy()
            mBlurOutput = null
        }
        if (mBitmapToBlur != null) {
            mBitmapToBlur!!.recycle()
            mBitmapToBlur = null
        }
        if (mBlurredBitmap != null) {
            mBlurredBitmap!!.recycle()
            mBlurredBitmap = null
        }
    }

    private fun releaseScript() {
        if (mRenderScript != null) {
            mRenderScript!!.destroy()
            mRenderScript = null
        }
        if (mBlurScript != null) {
            mBlurScript!!.destroy()
            mBlurScript = null
        }
    }

    private fun release() {
        releaseBitmap()
        releaseScript()
    }

    private fun prepare(): Boolean {
        if (mBlurRadius == 0f) {
            release()
            return false
        }

        var downsampleFactor = mDownsampleFactor
        var radius = mBlurRadius / downsampleFactor
        if (radius > 25) {
            downsampleFactor = downsampleFactor * radius / 25
            radius = 25f
        }

        if (mDirty || mRenderScript == null) {
            if (mRenderScript == null) {
                try {
                    mRenderScript = RenderScript.create(context)
                    mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript))
                } catch (e: RSRuntimeException) {
                    if (isDebug(context)) {
                        if (e.message != null && e.message!!.startsWith("Error loading RS jni library: java.lang.UnsatisfiedLinkError:")) {
                            throw RuntimeException("Error loading RS jni library, Upgrade buildToolsVersion=\"24.0.2\" or higher may solve this issue")
                        } else {
                            throw e
                        }
                    } else {
                        // In release mode, just ignore
                        releaseScript()
                        return false
                    }
                }

            }

            mBlurScript!!.setRadius(radius)
            mDirty = false
        }

        val width = width
        val height = height

        val scaledWidth = Math.max(1, (width / downsampleFactor).toInt())
        val scaledHeight = Math.max(1, (height / downsampleFactor).toInt())

        if (mBlurringCanvas == null || mBlurredBitmap == null
            || mBlurredBitmap!!.width != scaledWidth
            || mBlurredBitmap!!.height != scaledHeight
        ) {
            releaseBitmap()

            var r = false
            try {
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                if (mBitmapToBlur == null) {
                    return false
                }
                mBlurringCanvas = Canvas(mBitmapToBlur!!)

                mBlurInput = Allocation.createFromBitmap(
                    mRenderScript, mBitmapToBlur,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT
                )
                mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput!!.type)

                mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                if (mBlurredBitmap == null) {
                    return false
                }

                r = true
            } catch (e: OutOfMemoryError) {
                // Bitmap.createBitmap() may cause OOM error
                // Simply ignore and fallback
            } finally {
                if (!r) {
                    releaseBitmap()
                    return false
                }
            }
        }
        return true
    }

    private fun blur(bitmapToBlur: Bitmap?, blurredBitmap: Bitmap?) {
        mBlurInput!!.copyFrom(bitmapToBlur)
        mBlurScript!!.setInput(mBlurInput)
        mBlurScript!!.forEach(mBlurOutput)
        mBlurOutput!!.copyTo(blurredBitmap)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mDecorView = activityDecorView
        if (mDecorView != null) {
            mDecorView!!.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            mDifferentRoot = mDecorView!!.rootView !== rootView
            if (mDifferentRoot) {
                mDecorView!!.postInvalidate()
            }
        } else {
            mDifferentRoot = false
        }
    }

    override fun onDetachedFromWindow() {
        if (mDecorView != null) {
            mDecorView!!.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        release()
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        when {
            mIsRendering -> // Quit here, don't draw views above me
                throw STOP_EXCEPTION
            RENDERING_COUNT > 0 -> {
                // Doesn't support blurview overlap on another blurview
            }
            else -> super.draw(canvas)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBlurredBitmap(canvas, mBlurredBitmap, mOverlayColor)
    }

    /**
     * Custom draw the blurred bitmap and color to define your own shape
     *
     * @param canvas
     * @param blurredBitmap
     * @param overlayColor
     */
    private fun drawBlurredBitmap(canvas: Canvas, blurredBitmap: Bitmap?, overlayColor: Int) {
        if (blurredBitmap != null) {
            mRectSrc.right = blurredBitmap.width
            mRectSrc.bottom = blurredBitmap.height
            mRectDst.right = width
            mRectDst.bottom = height
            canvas.drawBitmap(blurredBitmap, mRectSrc, mRectDst, null)
        }
        mPaint.color = overlayColor
        canvas.drawRect(mRectDst, mPaint)
    }

    private class StopException : RuntimeException()

    companion object {
        private var RENDERING_COUNT: Int = 0

        private val STOP_EXCEPTION = StopException()

        init {
            try {
                BlurLayout::class.java.classLoader!!.loadClass("androidx.renderscript.RenderScript")
            } catch (e: ClassNotFoundException) {
                throw RuntimeException("RenderScript support not enabled. Add \"android { defaultConfig { renderscriptSupportModeEnabled true }}\" in your build.gradle")
            }

        }

        private var DEBUG: Boolean? = null

        internal fun isDebug(ctx: Context?): Boolean {
            if (DEBUG == null && ctx != null) {
                DEBUG = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            }
            return DEBUG === java.lang.Boolean.TRUE
        }
    }
}
