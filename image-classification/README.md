# Image Classification In Android
This sample application demonstrates the execution of a converted [Mobilenet v2](https://github.com/onnx/models/blob/main/validated/vision/classification/mobilenet/model/mobilenetv2-7.onnx) model using the ENN framework.
The model is converted using ENN SDK service with the **Accelerate** hardware type option.

## Functionality
The sample application accepts input from a camera feed or an image/video file and classifies the object within the input.
The classified items, their corresponding scores, and the inference time are displayed at the bottom of the application interface.

## Location
The sample is available in the `enn-sdk-samples-v920/image-classification` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-v920) repository.

## Getting Started
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
