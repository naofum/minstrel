#!/bin/bash

ROOT_DIR=$1
SRC_FILE="$1/static/android/strings.xml"
DST_PLAT="$1/platforms/android"
DST_DIR="$1/platforms/android/app/src/main/res/values-ja"
DST_FILE="$1/platforms/android/app/src/main/res/values-ja/strings.xml"

echo "[INFO] Root dir is $ROOT_DIR ..."

if [ -e "$DST_PLAT" ]
then
    echo "[INFO] Copying $SRC_FILE into $DST_FILE ..."
    mkdir $DST_DIR
    cp $SRC_FILE $DST_FILE
    echo "[INFO] Copying $SRC_FILE into $DST_FILE ... done"
fi

exit 0
