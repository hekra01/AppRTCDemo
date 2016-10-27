#!/bin/sh
set -x
if [ $# -ne 1 ]
  then
    #echo "Provide docker instance number only" && exit 1
    INSTANCE=2
else
    INSTANCE=$1
fi

export PATH=/home/dgaumont/DOCKER:/home/dgaumont/COMMIT/gvfbgrab-swizzle/:$PATH
cd /home/dgaumont/Downloads

docker kill android-instance-$INSTANCE&
sleep 2
start-android-marsh-generic.sh $INSTANCE &

sleep 15

run-adb.sh $INSTANCE install Facets-release.apk
run-adb.sh $INSTANCE install sf.apk
run-adb.sh $INSTANCE install apprtc-debug.apk
run-adb.sh $INSTANCE shell ./PrepareSdcard.sh
IP=$(ifconfig enp3s0 | grep addr: | awk '{ print ip=$2 }' | cut -d: -f2)
run-adb.sh $INSTANCE shell "echo $IP'-instance-'$INSTANCE > /private/sf/id.txt"
run-adb.sh $INSTANCE shell 'cat /private/sf/id.txt'
run-adb.sh $INSTANCE shell 'pm grant org.appspot.apprtc android.permission.RECORD_AUDIO'
run-adb.sh $INSTANCE shell 'pm grant org.appspot.apprtc android.permission.MODIFY_AUDIO_SETTINGS'


sleep 2

show-instance.sh $INSTANCE


#sleep 2

#RTP_OPTIONS="--sout-rtp-caching 50 --network-caching 50 --rtsp-tcp"
#VIDEO_PORT=$IP:6664
#FEED="#rtp{sdp=rtsp://$IP:8554/testfeed}"
#cvlc -vvv tcp/h264://$VIDEO_PORT $RTP_OPTIONS --sout $FEED &
#cvlc -vvv tcp/h264://10.6i0.62.12:6664 --sout-rtp-caching 50 --network-caching 50 --rtsp-tcp --sout #rtp{sdp=rtsp://10.60.62.12:8554/testfeed} &
#websockify 1338 $IP:6664 &
#sleep 3
#websockify 1337 $IP:7774
