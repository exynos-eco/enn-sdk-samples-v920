// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.samsung.poseestimation.data.Human
import java.lang.Float.max
import java.lang.Float.min
import kotlin.let
import kotlin.with


class OverlayView(
    context: Context?, attrs: AttributeSet?
) : View(context, attrs) {
    private var result: Human? = null
    private val pointPaint = Paint()
    private val edgePaint = Paint()

    private var scaleX = 1F
    private var scaleY = 1F
    private var offsetX = 0F
    private var offsetY = 0F


    init {
        initPaints()
    }

    private fun initPaints() {
        with(pointPaint) {
            color = ContextCompat.getColor(context!!, R.color.pose_color)
            strokeWidth = 12f
            style = Paint.Style.FILL
        }

        with(edgePaint) {
            color = ContextCompat.getColor(context!!, R.color.pose_color)
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
    }

    fun setResults(human: Human) {
        result = human
        scaleX = min(width.toFloat(), height.toFloat()) / 257
        scaleY = height.toFloat() / 353
        offsetX = (max(width.toFloat(), height.toFloat()) - min(width.toFloat(), height.toFloat())) / 2 + 0
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        result?.let { human ->
            human.points.forEach {
                canvas.drawPoint(
                    it.coordinate.x * scaleX + offsetX, it.coordinate.y * scaleY + offsetY, pointPaint
                )
            }
            human.edges.forEach {
                canvas.drawLine(
                    it.first.coordinate.x * scaleX + offsetX,
                    it.first.coordinate.y * scaleY + offsetY,
                    it.second.coordinate.x * scaleX + offsetX,
                    it.second.coordinate.y * scaleY + offsetY,
                    edgePaint
                )
            }
        }
    }

    fun clear() {
        result = null
        scaleX = 0F
        scaleY = 0F
        offsetX = 0F
        offsetY = 0F
        pointPaint.reset()
        edgePaint.reset()
        invalidate()
        initPaints()
    }
}