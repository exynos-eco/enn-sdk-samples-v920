# Image Enhance In Android
This sample application demonstrates the execution of a converted [Zero-DCE](https://www.kaggle.com/models/sayannath235/zero-dce) model using the ENN framework.
The model is converted using ENN SDK service with the **Default** hardware type option.

## Functionality
The application accepts input from an image file and enhances it.
Specifically, it takes low-light images and improves their quality.
Additionally, the inference time is displayed at the bottom of the application interface.

## Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.	Open the sample application project in Android Studio.
3.	Connect the ERD board to the computer.
4.	Run the application (using Shift + F10).
5.	Provide the image data for inference.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()` functions.