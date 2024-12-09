// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageclassification.data

object ModelConstants {
    const val MODEL_NAME = "inception_v4_quant.nnc"

    val INPUT_DATA_TYPE = DataType.UINT8
    val INPUT_DATA_LAYER = LayerType.HWC

    const val INPUT_SIZE_W = 299
    const val INPUT_SIZE_H = 299
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 1F
    const val INPUT_CONVERSION_OFFSET = 0F

    val OUTPUT_DATA_TYPE = DataType.UINT8

    const val OUTPUT_CONVERSION_SCALE = 1F
    const val OUTPUT_CONVERSION_OFFSET = 0F

    const val LABEL_FILE = "labels1001.txt"
}