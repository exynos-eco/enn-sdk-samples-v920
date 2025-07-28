// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageclassification.executor

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.samsung.imageclassification.data.DataType
import com.samsung.imageclassification.data.LayerType
import com.samsung.imageclassification.data.ModelConstants
import com.samsung.imageclassification.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
    var threshold: Float = 4.0F,
    val context: Context,
    val executorListener: ExecutorListener?
) {
    private external fun ennInitialize()
    private external fun ennDeinitialize()
    private external fun ennOpenModel(filename: String): Long
    private external fun ennCloseModel(modelId: Long)
    private external fun ennAllocateAllBuffers(modelId: Long): BufferSetInfo
    private external fun ennReleaseBuffers(bufferSet: Long, bufferSize: Int)
    private external fun ennExecute(modelId: Long)
    private external fun ennMemcpyHostToDevice(bufferSet: Long, layerNumber: Int, data: ByteArray)
    private external fun ennMemcpyDeviceToHost(bufferSet: Long, layerNumber: Int): ByteArray

    private var modelId: Long = 0
    private var bufferSet: Long = 0
    private var nInBuffer: Int = 0
    private var nOutBuffer: Int = 0

    init {
        System.loadLibrary("enn_jni")
        copyNNCFromAssetsToInternalStorage(MODEL_NAME)
        getLabels()
        setupENN()
    }

    private fun setupENN() {
        // Initialize ENN
        ennInitialize()

        // Open model
        val fileAbsoluteDirectory = File(context.filesDir, MODEL_NAME).absolutePath
        modelId = ennOpenModel(fileAbsoluteDirectory)

        // Allocate all required buffers
        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf
    }

    fun process(image: Bitmap) {
        // Process Image to Input Byte Array
        val input = preProcess(image)
        // Copy Input Data
        ennMemcpyHostToDevice(bufferSet, 0, input)

        var inferenceTime = SystemClock.uptimeMillis()
        // Model execute
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        // Copy Output Data
        val output = ennMemcpyDeviceToHost(bufferSet, nInBuffer)
        Log.i("ModelExecutor process", output.size.toString())
        Log.i("ModelExecutor process", output[0].toString())

        executorListener?.onResults(
            postProcess(output), inferenceTime
        )
    }

    fun closeENN() {
        // Release a buffer array
        ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
        // Close a Model and Free all resources
        ennCloseModel(modelId)
        // Destructs ENN process
        ennDeinitialize()
    }

    private fun preProcess(image: Bitmap): ByteArray {
        val byteArray = when (INPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                convertBitmapToUByteArray(image, INPUT_DATA_LAYER).asByteArray()
            }

            DataType.FLOAT32 -> {
                val data = convertBitmapToFloatArray(image, INPUT_DATA_LAYER)
                val byteBuffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.asFloatBuffer().put(data)
                byteBuffer.array()
            }

            else -> {
                throw IllegalArgumentException("Unsupported input data type: ${INPUT_DATA_TYPE}")
            }
        }

        return byteArray
    }

    private fun postProcess(modelOutput: ByteArray): Map<String, Float> {
        val output = when (OUTPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                modelOutput.asUByteArray().mapIndexed { index, value ->
//                    Log.i("ModelExecutor process", value.toInt().toString())
                    labelList[index] to dequantizedValues[((value.toInt()
                            - OUTPUT_CONVERSION_OFFSET)
                            / OUTPUT_CONVERSION_SCALE).toInt()]
                }.filter { it.second >= threshold }.sortedByDescending { it.second }.toMap()
            }

            DataType.FLOAT32 -> {
                val byteBuffer = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
                Log.i("ModelExecutor ByteBuffer", byteBuffer[0].toString())
                val floatBuffer = byteBuffer.asFloatBuffer()
                Log.i("ModelExecutor FloatBuffer", floatBuffer[0].toString())
                val data = FloatArray(floatBuffer.remaining())
                Log.i("ModelExecutor data", "${data.toList().sortedDescending().take(5)}")

                floatBuffer.get(data)
                data.mapIndexed { index, value ->
                    labelList[index] to ((value * 2.0F
                            - OUTPUT_CONVERSION_OFFSET)
                            / OUTPUT_CONVERSION_SCALE)
                }.filter { it.second >= threshold }.sortedByDescending { it.second }.toMap()
            }

            else -> {
                throw IllegalArgumentException("Unsupported output data type: ${OUTPUT_DATA_TYPE}")
            }
        }

        Log.i("ModelExecutor postProcess", output.size.toString())
        Log.i("ModelExecutor process", output.toString())

        return softmax(getTopNEntries(output, 5))
    }

    private fun getTopNEntries(inputMap: Map<String, Float>, n: Int): Map<String, Float> {
        // Step 1: Sort the map entries by value in descending order and take the top N entries
        return inputMap.entries
            .sortedByDescending { it.value } // Sort by value
            .take(n) // Take the top N entries
            .associate { it.toPair() } // Convert to Map<String, Float>
    }

    private fun softmax(inputMap: Map<String, Float>): Map<String, Float> {
        // Step 1: Calculate the exponentials of the values
        val expMap = inputMap.mapValues { Math.exp(it.value.toDouble()).toFloat() }

        // Step 2: Sum the exponentials
        val sumExp = expMap.values.sum()

        // Step 3: Calculate the softmax probabilities
        val softmaxMap = expMap.mapValues { it.value / sumExp }

        Log.i("ModelExecutor softmax", softmaxMap.toString())

        return softmaxMap
    }

    private fun convertBitmapToUByteArray(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.HWC
    ): UByteArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)

        image.getPixels(
            pixels,
            0,
            INPUT_SIZE_W,
            0,
            0,
            INPUT_SIZE_W,
            INPUT_SIZE_H
        )

        val uByteArray = UByteArray(totalPixels * INPUT_SIZE_C)
        val offset: IntArray
        val stride: Int

        if (layerType == LayerType.CHW) {
            offset = intArrayOf(0, totalPixels, 2 * totalPixels)
            stride = 1
        } else {
            offset = intArrayOf(0, 1, 2)
            stride = 3
        }

        for (i in 0 until totalPixels) {
            val color = pixels[i]
            uByteArray[i * stride + offset[0]] = ((((color shr 16) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE).toInt().toUByte()
            uByteArray[i * stride + offset[1]] = ((((color shr 8) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE).toInt().toUByte()
            uByteArray[i * stride + offset[2]] = ((((color shr 0) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE).toInt().toUByte()
        }

        return uByteArray
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.HWC
    ): FloatArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)
        val meanVec = floatArrayOf(0.485f, 0.456f, 0.406f)
        val stddevVec = floatArrayOf(0.229f, 0.224f, 0.225f)

        image.getPixels(
            pixels,
            0,
            INPUT_SIZE_W,
            0,
            0,
            INPUT_SIZE_W,
            INPUT_SIZE_H
        )

        val floatArray = FloatArray(totalPixels * INPUT_SIZE_C)
        val offset: IntArray
        val stride: Int

        if (layerType == LayerType.CHW) {
            offset = intArrayOf(0, totalPixels, 2 * totalPixels)
            stride = 1
        } else {
            offset = intArrayOf(0, 1, 2)
            stride = 3
        }

        for (i in 0 until totalPixels) {
            val color = pixels[i]
            floatArray[i * stride + offset[0]] = ((((color shr 16) and 0xFF)/255.0F
//                    - INPUT_CONVERSION_OFFSET)
//                    / INPUT_CONVERSION_SCALE)
                    - meanVec[0])
                    / stddevVec[0])
            floatArray[i * stride + offset[1]] = ((((color shr 8) and 0xFF)/255.0F
//                    - INPUT_CONVERSION_OFFSET)
//                    / INPUT_CONVERSION_SCALE)
                    - meanVec[1])
                    / stddevVec[1])
            floatArray[i * stride + offset[2]] = ((((color shr 0) and 0xFF)/255.0F
//                    - INPUT_CONVERSION_OFFSET)
//                    / INPUT_CONVERSION_SCALE)
                    - meanVec[2])
                    / stddevVec[2])
        }

        return floatArray
    }

    private fun copyNNCFromAssetsToInternalStorage(filename: String) {
        try {
            val inputStream = context.assets.open(filename)
            val outputFile = File(context.filesDir, filename)
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(2048)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getLabels() {
        try {
            context.assets.open(LABEL_FILE)
                .bufferedReader().use { reader -> labelList = reader.readLines() }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            result: Map<String, Float>, inferenceTime: Long
        )
    }

    companion object {
        var labelList: List<String> = mutableListOf()
        val dequantizedValues = List(256) { it.toFloat() * 0.00390625F }

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE

        private const val OUTPUT_CONVERSION_SCALE = ModelConstants.OUTPUT_CONVERSION_SCALE
        private const val OUTPUT_CONVERSION_OFFSET = ModelConstants.OUTPUT_CONVERSION_OFFSET

        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}
