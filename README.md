# ENN SDK Samples v920

## Introduction
|Sample Name|Description|
|-------------|-------|
|[Image Classification In Android](#image-classification-in-android)| Sample Android application to demonstrate the execution of `Mobilenet v2` model with ENN SDK|
|[Pose Estimation In Android](#pose-estimation-in-android)| Sample Android application to demonstrate the execution of `PoseNet` model with ENN SDK|

## Android (Kotlin) Samples
This section provides an overview of Android (Kotlin) sample applications.
Each sample application entry provides the details of the functionality of the sample application, its location, and instructions for running it.

***

### Image Classification In Android
This sample application demonstrates the execution of a converted [Mobilenet v2](https://github.com/onnx/models/blob/main/validated/vision/classification/mobilenet/model/mobilenetv2-7.onnx) model using the ENN framework.
The model is converted using ENN SDK service with the **Accelerate** hardware type option.

#### Functionality
The sample application accepts input from a camera feed or an image/video file and classifies the object within the input.
The classified items, their corresponding scores, and the inference time are displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-v920/image-classification` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-v920) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.	Open the sample application project in Android Studio.
3.	Connect the SADK(V920) board to the computer.
4.	Run the application (using Shift + F10).
5.	Select Camera, Image or Video mode and provide the data for inference.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()` functions.

***

### Pose Estimation In Android
This sample application demonstrates the execution of a converted [PoseNet](https://www.kaggle.com/models/tensorflow/posenet-mobilenet/frameworks/tfJs/variations/float-075/versions/1) model using the ENN framework.
The model is converted using ENN SDK service with the **Default** hardware type option.

#### Functionality
The application accepts input from a camera feed or an image/video file.
Then, it detects the points of a person and overlays the points and edges of a person.
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-v920/pose-estimation` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-v920) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.	Open the sample application project in Android Studio.
3.	Connect the SADK(V920) board to the computer.
4.	Run the application (using Shift + F10).
5.	Select Camera, Image or Video mode and provide the data for inference.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory within the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()` functions.

***