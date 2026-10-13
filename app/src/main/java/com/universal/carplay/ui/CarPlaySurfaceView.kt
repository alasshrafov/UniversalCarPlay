package com.universal.carplay.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * CarPlay video axınının render edildiyi və toxunuşların qəbul olunduğu xüsusi SurfaceView
 */
class CarPlaySurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    interface SurfaceListener {
        fun onSurfaceCreated(holder: SurfaceHolder)
        fun onSurfaceDestroyed(holder: SurfaceHolder)
        fun onSurfaceChanged(holder: SurfaceHolder, width: Int, height: Int)
        fun onTouchEventCaptured(event: MotionEvent)
    }

    private var listener: SurfaceListener? = null

    init {
        holder.addCallback(this)
    }

    fun setSurfaceListener(listener: SurfaceListener) {
        this.listener = listener
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        listener?.onSurfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        listener?.onSurfaceChanged(holder, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        listener?.onSurfaceDestroyed(holder)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        listener?.onTouchEventCaptured(event)
        return true
    }
}
