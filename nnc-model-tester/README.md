# NNC Model Tester Guide

## Overview
NNC Model Tester is a native program that utilizes the ENN framework to execute ENN NNC models on the Exynos platform. 
Users can execute the model on the ADB shell to check their model before developing an application with UI.

## Preparation
### Build NNC Model Tester
- The pre-built program is located in `/libs/arm564-v8a`
- If you only want to test your model, skip this step
#### Linux
1. Install NDK
1. Set NDK directory as an environment variable in `{$HOME}/.bashrc`
    ```bash
    export ANDROID_NDK_HOME={ndk_path}
    # Example
    export ANDROID_NDK_HOME="/home/exynos-eco/Android/Sdk/ndk/25.2.9519653"
    ```
1. Build NNC Model Tester using provided script in project directory
    ```bash
    ./build_ndk_linux.sh
    ```
1. After build process is complete, the compiled program can be verified by checking the libs directory
    ```bash
    ls libs/arm64-v8a/
    ```

#### Windows
1. Install NDK
1. Set NDK directory as an environment variable on Command Prompt
    ```bash
    set ANDROID_NDK_HOME={ndk_path}
    # Example
    set ANDROID_NDK_HOME="%localappdata%\Android\Sdk\ndk\25.2.9519653"
    ```
1. Build NNC Model Tester using the provided script in the project directory on Command Prompt
    ```bash
    build_ndk_windows.bat
    ```
1. After build process is complete, the compiled program can be verified by checking the libs directory
    ```bash
    dir libs\arm64-v8a\
    ```

### Push neccesary files
- Push `enn_nnc_model_tester` binary
- Push `libenn_public_api_ndk_v1.so` library
- Push test data (model, input, golden)

example:
```bash
adb push libs/arm64-v8a/enn_nnc_model_tester /data/local/tmp/
adb push libs/arm64-v8a/libenn_public_api_ndk_v1.so /data/local/tmp/
adb push example/* /data/local/tmp/
```

#### Execute Permission
- When `nnc_model_tester` was built from Windows, execution permission should be given
```bash
adb shell "chmod +x /data/local/tmp/enn_nnc_model_tester"
```


## Usage
```bash
LD_LIBRARY_PATH=/data/local/tmp ./enn_nnc_model_tester --help
ENN SDK NNC Model Tester
Usage: ./enn_nnc_model_tester [OPTIONS]

Options:
  -h,--help                   Print this help message and exit
  --model TEXT REQUIRED       Name of the model to execute
  --input TEXT ...            Input data files
  --golden TEXT ...           Golden (reference) files
  --iteration INT:NONNEGATIVE Number of iterations to run the model
  --threshold FLOAT [0.0]     Threshold value for model execution
  --force                     Run the model without input data
```

### 1. Execute without golden matching
- Executing without golden matching
- Execution results will be dumped to files (output1.bin, ...)
```bash
adb shell
cd /data/local/tmp/
export LD_LIBRARY_PATH=/data/local/tmp 
./enn_nnc_model_tester --model model.nnc --input input.bin
```

### 2. Execute with golden matching 
- Executing without a threshold parameter will set the threshold to 0.
- Executing without a threshold is not recommended for float datatypes.
```bash
adb shell
cd /data/local/tmp/
export LD_LIBRARY_PATH=/data/local/tmp 
./enn_nnc_model_tester --model model.nnc --input input.bin --golden golden.bin
```

### 3. Execute with golden matching and setting threshold
```bash
adb shell
cd /data/local/tmp/
export LD_LIBRARY_PATH=/data/local/tmp 
./enn_nnc_model_tester --model model.nnc --input input.bin --golden golden.bin --threshold 0.0001
```

### 4. Execute with multiple input/output layers
```bash
adb shell
cd /data/local/tmp/
export LD_LIBRARY_PATH=/data/local/tmp 
./enn_nnc_model_tester --model model.nnc --input input0.bin input1.bin input2.bin \
    --golden golden0.bin golden1.bin golden2.bin
```

### 5. Execute with multiple iteration
```bash
adb shell
cd /data/local/tmp/
export LD_LIBRARY_PATH=/data/local/tmp 
./enn_nnc_model_tester --model model.nnc --input input.bin --golden golden.bin \
    --threshold 0.0001 --iteration 30
```

### 6. Execute without input
```bash
adb shell
cd /data/local/tmp/
export LD_LIBRARY_PATH=/data/local/tmp 
./enn_nnc_model_tester --force --model model.nnc
```

## Test result
### 1.  Execute model with 30 iterations
```bash
erd9925:/data/local/tmp # ./enn_nnc_model_tester \
>     --model model.nnc --input input.bin --golden golden.bin \
>     --threshold 0.0001 --iteration 30
Loaded Model:
        model.nnc(00001FA001000000)
Model Execution Time (1): 4264 microseconds
Model Execution Time (2): 3930 microseconds
Model Execution Time (3): 3925 microseconds
Model Execution Time (4): 3948 microseconds
Model Execution Time (5): 4128 microseconds
Model Execution Time (6): 4105 microseconds
Model Execution Time (7): 4137 microseconds
Model Execution Time (8): 4138 microseconds
Model Execution Time (9): 4299 microseconds
Model Execution Time (10): 4186 microseconds
Model Execution Time (11): 4149 microseconds
Model Execution Time (12): 4410 microseconds
Model Execution Time (13): 4328 microseconds
Model Execution Time (14): 4170 microseconds
Model Execution Time (15): 4312 microseconds
Model Execution Time (16): 4586 microseconds
Model Execution Time (17): 4355 microseconds
Model Execution Time (18): 4404 microseconds
Model Execution Time (19): 4513 microseconds
Model Execution Time (20): 4769 microseconds
Model Execution Time (21): 4284 microseconds
Model Execution Time (22): 4431 microseconds
Model Execution Time (23): 4437 microseconds
Model Execution Time (24): 4218 microseconds
Model Execution Time (25): 4346 microseconds
Model Execution Time (26): 4155 microseconds
Model Execution Time (27): 4023 microseconds
Model Execution Time (28): 4040 microseconds
Model Execution Time (29): 4193 microseconds
Model Execution Time (30): 4416 microseconds
Avg. Model Execution Time: 4253 microseconds
Output Layer(0): Golden Match
-       snr value:104.802
ENN Framework Execute Model Sucess

```

### 2. Execute without setting threshold

```bash
erd9925:/data/local/tmp # ./enn_nnc_model_tester \
>     --model model.nnc --input input.bin --golden golden.bin
Loaded Model:
        model.nnc(00001FC401000000)
Model Execution Time (1): 4538 microseconds
Avg. Model Execution Time: 4538 microseconds
Output Layer(0): Golden Mismatch
-       different indices:1000
-       snr value:104.802
ENN Framework Execute Model Sucess
```
