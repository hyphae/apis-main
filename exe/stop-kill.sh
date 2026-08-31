echo 'call stop-kill.sh'

NUM_OF_SIGTERM=300

get_pids() {
 ps -f -U `whoami` | grep apis-main | grep java | while read _USER_ _PID_ _OTHERS_; do
  echo $_PID_
 done
}

KILL=kill

while true; do
 _PIDS_=`get_pids`
 if [ -z "$_PIDS_" ] ; then
  break
 fi
 if [ $NUM_OF_SIGTERM -gt 0 ] ; then
  NUM_OF_SIGTERM=`expr $NUM_OF_SIGTERM - 1`
 else
  KILL='kill -KILL'
 fi
 echo $KILL $_PIDS_
 $KILL $_PIDS_
 sleep 1
done

echo '... done'
