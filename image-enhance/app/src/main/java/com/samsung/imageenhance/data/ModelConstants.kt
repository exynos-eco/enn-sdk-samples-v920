// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageenhance.data

object ModelConstants {
    const val MODEL_NAME = "zero-dce.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.HWC

    const val INPUT_SIZE_W = 600
    const val INPUT_SIZE_H = 400
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 256F
    const val INPUT_CONVERSION_OFFSET = 0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32
    val OUTPUT_DATA_LAYER = LayerType.HWC

    const val OUTPUT_SIZE_W = 600
    const val OUTPUT_SIZE_H = 400
    const val OUTPUT_SIZE_C = INPUT_SIZE_C

    const val OUTPUT_CONVERSION_SCALE = 256F
    const val OUTPUT_CONVERSION_OFFSET = 0F
}