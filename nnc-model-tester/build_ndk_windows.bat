@echo off

set TARGET_DIRECTORY=.

if exist %TARGET_DIRECTORY%\libs rmdir /s /q %TARGET_DIRECTORY%\libs
if exist %TARGET_DIRECTORY%\obj rmdir /s /q %TARGET_DIRECTORY%\obj

if %ANDROID_NDK_HOME%=="" (
    echo ## Please set ANDROID_NDK_HOME to the top-level directory of your NDK installation.
    echo Example:
    echo    set ANDROID_NDK_HOME=^<ndk dir^>
    exit /b
)

%ANDROID_NDK_HOME%\ndk-build.cmd -C %TARGET_DIRECTORY%\jni

echo DONE!
