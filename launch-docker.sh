#!/bin/bash
set -x

NEWSTREAM=false

if [ $# -eq 0 ]
  then
    INSTANCE=2
elif [ $# -ge 1 ]
  then
    INSTANCE=$1
fi

if [ $# -eq 2 ]
  then
    NEWSTREAM=$2
fi

echo "NS=$NEWSTREAM"

export PATH=/home/dgaumont/DOCKER:/home/dgaumont/COMMIT/gvfbgrab-swizzle/:$PATH
cd /home/dgaumont/Downloads

#sudo chmod 666 /dev/binder /dev/ashmem
#Nohup ./service_manager >/dev/null  2>1 &

docker rm -f android-instance-$INSTANCE&
sleep 2

if [ "$NEW_STREAM" == true ]
  then
    start-android-marsh-generic.sh $INSTANCE nostream &
    /home/dgaumont/stream-instance.sh $INSTANCE &
else
    start-android-marsh-generic.sh $INSTANCE
fi
sleep 15

run-adb.sh $INSTANCE install apprtc-debug.apk
#run-adb.sh $INSTANCE install sf.apk
#run-adb.sh $INSTANCE install sf_k_stb-charlety_ux-debug.apk
#run-adb.sh $INSTANCE install com.google.android.youtube_apkmirror.com.apk
run-adb.sh $INSTANCE install sf_k_stb-tmeibc-debug.apk
run-adb.sh $INSTANCE shell ./PrepareSdcard.sh
IP=$(ifconfig enp3s0 | grep addr: | awk '{ print ip=$2 }' | cut -d: -f2)
if [ $INSTANCE -eq 4 ]
  then
    run-adb.sh $INSTANCE shell "echo shuttle-instance$INSTANCE > /private/sf/id.txt"
else
  run-adb.sh $INSTANCE shell "echo shuttle-instance-$INSTANCE > /private/sf/id.txt"
fi
run-adb.sh $INSTANCE shell 'cat /private/sf/id.txt'
run-adb.sh $INSTANCE shell "touch /private/sf/leave$INSTANCE.txt"
run-adb.sh $INSTANCE shell 'pm grant org.appspot.apprtc android.permission.RECORD_AUDIO'
run-adb.sh $INSTANCE shell 'pm grant org.appspot.apprtc android.permission.MODIFY_AUDIO_SETTINGS'
run-adb.sh $INSTANCE shell 'pm grant org.appspot.apprtc android.permission.READ_EXTERNAL_STORAGE'
run-adb.sh $INSTANCE shell 'pm grant org.appspot.apprtc android.permission.WRITE_EXTERNAL_STORAGE'

sleep 2

if [ "$NEWSTREAM" == false ]
then
  show-instance.sh $INSTANCE
fi
