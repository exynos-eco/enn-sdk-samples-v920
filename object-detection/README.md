# Object Detection In Android
This sample application demonstrates the execution of a converted [YOLOv5](https://github.com/ultralytics/yolov5) model using the ENN framework.
The model is converted using ENN SDK service with the **Default** hardware type option.

## Functionality
The application accepts input from a camera feed or an image file and identifies the object within the input.
A bounding box is drawn around the detected item, and the label and score that are associated with the object are displayed.
Additionally, the inference time is displayed at the bottom of the application interface.

## Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.	Open the sample application project in Android Studio.
3.	Connect the ERD board to the computer.
4.	Run the application (using Shift + F10).
5.	Select Camera or Image mode and provide the data for inference.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()` functions.