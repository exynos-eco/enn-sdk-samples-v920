// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.perfcompare.executor

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.os.SystemClock
import com.samsung.perfcompare.data.DataType
import com.samsung.perfcompare.data.LayerType
import com.samsung.perfcompare.data.ModelConstants
import com.samsung.perfcompare.enn_type.BufferSetInfo
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.log10


@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
    var threshold: Float = 0.5F,
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

    private var tflite: Interpreter? = null

    init {
        System.loadLibrary("enn_jni")
        copyNNCFromAssetsToInternalStorage(NNC_MODEL_NAME)
        getLabels()
        setupENN()
        setupTFLite()
    }

    private fun setupENN() {
        // Initialize ENN
        ennInitialize()

        // Open model
        val fileAbsoluteDirectory = File(context.filesDir, NNC_MODEL_NAME).absolutePath
        modelId = ennOpenModel(fileAbsoluteDirectory)

        // Allocate all required buffers
        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf
    }

    private fun setupTFLite() {
        try {
            val options = Interpreter.Options()
            tflite = Interpreter(loadTFLiteFile(TFLITE_MODEL_NAME), options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun process(image: Bitmap) {
        // Inference with ENN
        // Convert Input
        val inputData = preProcess(image)
        // Execute
        var inferenceTimeENN = SystemClock.uptimeMillis()
        val outputENN = executeENN(inputData)
        inferenceTimeENN = SystemClock.uptimeMillis() - inferenceTimeENN


        // Inference with TFLite
        // Convert Input
        val inputTFLite = ByteBuffer.allocate(inputData.size)
        inputTFLite.put(inputData)
        inputTFLite.rewind()
        var output = when (OUTPUT_DATA_TYPE) {
            DataType.FLOAT32 -> ByteBuffer.allocate(OUTPUT_SIZE_W * 4)
            DataType.UINT8 -> ByteBuffer.allocate(OUTPUT_SIZE_W)
            else -> {
                throw IllegalArgumentException("Unsupported input data type: ${OUTPUT_DATA_TYPE}")
            }
        }
        // Execute
        var inferenceTimeTFLite = SystemClock.uptimeMillis()
        output = executeTFLite(inputTFLite, output)
        inferenceTimeTFLite = SystemClock.uptimeMillis() - inferenceTimeTFLite
        // Convert Output
        val byteArray = when (OUTPUT_DATA_TYPE) {
            DataType.FLOAT32 -> ByteArray(OUTPUT_SIZE_W * 4)
            DataType.UINT8 -> ByteArray(OUTPUT_SIZE_W)
            else -> {
                throw IllegalArgumentException("Unsupported input data type: ${OUTPUT_DATA_TYPE}")
            }
        }
        output.rewind()
        output.get(byteArray)
        val outputTFLite = byteArray

        executorListener?.onResults(
            postProcess(outputENN),
            inferenceTimeENN,
            postProcess(outputTFLite),
            inferenceTimeTFLite,
            calculateSNR(outputTFLite, outputENN)
        )
    }

    private fun executeENN(input: ByteArray): ByteArray {
        // Copy Input Data
        ennMemcpyHostToDevice(bufferSet, 0, input)
        // Execute
        ennExecute(modelId)

        // Copy Output Data
        return ennMemcpyDeviceToHost(bufferSet, nInBuffer)
    }

    private fun executeTFLite(input: ByteBuffer, output: ByteBuffer): ByteBuffer {
        tflite?.run(input, output)

        return output
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
                    labelList[index] to dequantizedValues[((value.toInt()
                            - OUTPUT_CONVERSION_OFFSET)
                            / OUTPUT_CONVERSION_SCALE).toInt()]
                }.filter { it.second >= threshold }.sortedByDescending { it.second }.toMap()
            }

            DataType.FLOAT32 -> {
                val byteBuffer = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
                val floatBuffer = byteBuffer.asFloatBuffer()
                val data = FloatArray(floatBuffer.remaining())

                floatBuffer.get(data)
                data.mapIndexed { index, value ->
                    labelList[index] to ((value
                            - OUTPUT_CONVERSION_OFFSET)
                            / OUTPUT_CONVERSION_SCALE)
                }.filter { it.second >= threshold }.sortedByDescending { it.second }.toMap()
            }

            else -> {
                throw IllegalArgumentException("Unsupported output data type: ${OUTPUT_DATA_TYPE}")
            }
        }

        return output
    }

    private fun calculateSNR(controlByteData: ByteArray, testByteData: ByteArray): Float {
        val controlData = convertOutputByteToFloatArray(controlByteData)
        val testData = convertOutputByteToFloatArray(testByteData)

        if (controlData.size != testData.size) return 0F

        val noisePower = controlData.zip(testData).sumOf { (control, test) ->
            val difference = control - test
            difference * difference.toDouble()
        }.toFloat()
        val signalPower = controlData.sumOf { it * it.toDouble() }.toFloat()

        if (noisePower == 0F) return Float.POSITIVE_INFINITY

        return 10 * log10(signalPower / noisePower)
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
            floatArray[i * stride + offset[0]] = ((((color shr 16) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
            floatArray[i * stride + offset[1]] = ((((color shr 8) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
            floatArray[i * stride + offset[2]] = ((((color shr 0) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
        }

        return floatArray
    }

    private fun convertOutputByteToFloatArray(
        modelOutput: ByteArray
    ): FloatArray {
        return when (OUTPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                modelOutput.toUByteArray().map { it.toFloat() }.toFloatArray()
            }

            DataType.FLOAT32 -> {
                val byteBuffer = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
                val floatBuffer = byteBuffer.asFloatBuffer()
                val floatArray = FloatArray(floatBuffer.remaining())

                floatBuffer.get(floatArray)
                floatArray
            }

            else -> {
                throw IllegalArgumentException("Unsupported output data type: ${OUTPUT_DATA_TYPE}")
            }
        }
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

    private fun loadTFLiteFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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
            result1: Map<String, Float>,
            inferenceTime1: Long,
            result2: Map<String, Float>,
            inferenceTime2: Long,
            snr: Float
        )
    }

    companion object {
        var labelList: List<String> = mutableListOf()
        val dequantizedValues = List(256) { it.toFloat() * 0.00390625F }

        const val NNC_MODEL_NAME = ModelConstants.NNC_MODEL_NAME
        const val TFLITE_MODEL_NAME = ModelConstants.TFLITE_MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE

        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
        private const val OUTPUT_SIZE_C = ModelConstants.OUTPUT_SIZE_C

        private const val OUTPUT_CONVERSION_SCALE = ModelConstants.OUTPUT_CONVERSION_SCALE
        private const val OUTPUT_CONVERSION_OFFSET = ModelConstants.OUTPUT_CONVERSION_OFFSET

        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}
