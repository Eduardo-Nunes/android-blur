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
import androidx.core.content.ContextCompat

private const val DEFAULT_RADIUS = 10f
private const val DEFAULT_SIZE = 4f
private const val SCREEN_LOCATIONS = 2
private const val SAFE_RADIUS = 25
private const val MAX_PARENT_VIEW_CONTEXT = 4
private const val IMAGE_SCALE = 1

class BlurLayout(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var mDownsampleFactor: Float = 0f
    private var mOverlayColor: Int = 0
    private var mBlurRadius: Float = 0f

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
    private var mDecorView: View? = null
    private var mDifferentRoot: Boolean = false

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        val locations = IntArray(SCREEN_LOCATIONS)
        var oldBmp: Bitmap? = mBlurredBitmap
        val decor = mDecorView
        if (decor != null && isShown && prepare()) {
            val redrawBitmap = mBlurredBitmap != oldBmp
            oldBmp = null
            decor.getLocationOnScreen(locations)
            var x = -locations.first()
            var y = -locations.last()

            getLocationOnScreen(locations)
            x += locations.first()
            y += locations.last()

            // just erase transparent
            mBitmapToBlur!!.eraseColor(mOverlayColor and android.R.color.white)

            val rc = mBlurringCanvas!!.save()
            mIsRendering = true
            RENDERING_COUNT++
            try {
                mBlurringCanvas!!.scale(
                    IMAGE_SCALE.toFloat() * mBitmapToBlur!!.width / width,
                    IMAGE_SCALE.toFloat() * mBitmapToBlur!!.height / height
                )
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
            while (i < MAX_PARENT_VIEW_CONTEXT && ctx != null && ctx !is Activity && ctx is ContextWrapper) {
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
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_RADIUS,
                context.resources.displayMetrics
            )
        )
        mDownsampleFactor = a.getFloat(R.styleable.BlurLayout_downSampleFactor, DEFAULT_SIZE)
        mOverlayColor = a.getColor(
            R.styleable.BlurLayout_overlayColor,
            ContextCompat.getColor(context, android.R.color.transparent)
        )
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

    fun getBlurRadius(): Float = mBlurRadius

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
        if (radius > SAFE_RADIUS) {
            downsampleFactor = downsampleFactor * radius / SAFE_RADIUS
            radius = SAFE_RADIUS.toFloat()
        }

        if (mDirty || mRenderScript == null) {
            if (mRenderScript == null) {
                try {
                    mRenderScript = RenderScript.create(context)
                    mBlurScript =
                        ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript))
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

        val scaledWidth = Math.max(IMAGE_SCALE, (width / downsampleFactor).toInt())
        val scaledHeight = Math.max(IMAGE_SCALE, (height / downsampleFactor).toInt())

        if (mBlurringCanvas == null || mBlurredBitmap == null ||
            mBlurredBitmap!!.width != scaledWidth ||
            mBlurredBitmap!!.height != scaledHeight
        ) {
            releaseBitmap()

            var r = false
            try {
                mBitmapToBlur =
                    Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                if (mBitmapToBlur == null) {
                    return false
                }
                mBlurringCanvas = Canvas(mBitmapToBlur!!)

                mBlurInput = Allocation.createFromBitmap(
                    mRenderScript, mBitmapToBlur,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT
                )
                mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput!!.type)

                mBlurredBitmap =
                    Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
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
        if (mBlurInput == null || mBlurOutput == null || bitmapToBlur == null || blurredBitmap == null) return
        mBlurInput?.copyFrom(bitmapToBlur)
        mBlurScript?.setInput(mBlurInput)
        mBlurScript?.forEach(mBlurOutput)
        mBlurOutput?.copyTo(blurredBitmap)
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
        mDecorView?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        release()
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        when {
            mIsRendering -> // Quit here, don't draw views above me
                throw STOP_EXCEPTION
            RENDERING_COUNT > 0 -> {
                // Doesn't support blurLayout overlap on another blurLayout
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
        @JvmStatic
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
            return DEBUG == true
        }
    }
}