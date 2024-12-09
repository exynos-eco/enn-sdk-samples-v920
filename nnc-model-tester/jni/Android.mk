LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := enn_public_api_ndk_v1
LOCAL_SRC_FILES := ${LOCAL_PATH}/lib64/libenn_public_api_ndk_v1.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := enn_nnc_model_tester

LOCAL_C_INCLUDES += \
                    ${LOCAL_PATH} \
                    ${LOCAL_PATH}/include

LOCAL_LDLIBS := -llog
LOCAL_CFLAGS += -Wall -std=c++14 -O3
LOCAL_CPPFLAGS += -fexceptions -frtti

LOCAL_SRC_FILES := enn_nnc_model_tester.cpp
LOCAL_SHARED_LIBRARIES := enn_public_api_ndk_v1
include $(BUILD_EXECUTABLE)

