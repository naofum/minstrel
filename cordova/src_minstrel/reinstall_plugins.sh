#!/bin/bash

cordova plugin remove readbeyond-plugin-commander
cordova plugin add ../src_plugins/readbeyond-plugin-commander

cordova plugin remove readbeyond-plugin-librarian
cordova plugin add ../src_plugins/readbeyond-plugin-librarian

cordova plugin remove readbeyond-plugin-media
cordova plugin add ../src_plugins/readbeyond-plugin-media

cordova plugin remove readbeyond-plugin-mediarb
cordova plugin add ../src_plugins/readbeyond-plugin-mediarb

cordova plugin remove readbeyond-plugin-unzipper
cordova plugin add ../src_plugins/readbeyond-plugin-unzipper

cordova plugin remove cordova-plugin-android-permissions
cordova plugin add ../src_plugins/cordova-plugin-android-permissions

#cordova plugin remove cordova-plugin-admobpro
#cordova plugin add cordova-plugin-admobpro

