#/bin/bash

rm -rf ./$TARGET_DIRECTORY/libs/
rm -rf ./$TARGET_DIRECTORY/obj/

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo " ## Please set \$ANDROID_NDK_HOME as a top of NDK installed directory."
    echo " Example)"
    echo "   $ export ANDROID_NDK_HOME=<ndk dir> or add this at ~/.bashrc"
    exit 0;
fi

TARGET_DIRECTORY="."

$ANDROID_NDK_HOME/ndk-build -C $TARGET_DIRECTORY/jni

echo "DONE!"

