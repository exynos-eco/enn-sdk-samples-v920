# ENN SDK Samples v920 Android

## Introduction
|Category|Sample Name|Description|
|-------------|-------|----------------------------------------------------------------------------------------------------------|
|Image Classification|[Mobilenet v2](#mobilenet-v2)| Sample Android application to demonstrate the execution of `Mobilenet v2-7` model with ENN SDK|
|Pose Estimation|[Posenet-mobilenet](#posenet-mobilenet)| Sample Android application to demonstrate the execution of `Posenet-mobilenet` model with ENN SDK|

## Android (Kotlin) Samples
This section provides an overview of Android (Kotlin) sample applications.
Each sample application entry provides the details of the functionality of the sample application, its location, and instructions for running it.

***

### Mobilenet v2
This document explains Android Sample Application operates using the [Mobilenet v2](https://github.com/onnx/models/blob/main/validated/vision/classification/mobilenet/model/mobilenetv2-7.onnx) model optimized for Exynos hardware.

#### Functionality
The application accepts input from an image/video file. Then, it detects the points of a person and overlays the points and edges of a person.

#### Location
The sample is available in the `enn-sdk-samples-v920/image-classification/mobilenetv2` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-v920) repository, where you can find detailed instructions on prerequisites, build steps, and how to run the sample.

***

### Posenet-mobilenet
This document explains Android Sample Application operates using the [Posenet-mobilenet](https://www.kaggle.com/models/tensorflow/posenet-mobilenet/frameworks/tfJs/variations/float-075/versions/1) model optimized for Exynos hardware.

#### Functionality
The application accepts input from an image/video file. Then, it detects the points of a person and overlays the points and edges of a person.

#### Location
The sample is available in the `enn-sdk-samples-v920/pose-estimation/posenet-mobilenet` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-v920) repository, where you can find detailed instructions on prerequisites, build steps, and how to run the sample.


***