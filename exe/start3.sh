echo 'call start.sh'

CLUSTER_XML=cluster.xml
if [ "$(uname)" = 'Darwin' ] ; then
	CLUSTER_XML=cluster-mac.xml
fi
java -Djava.net.preferIPv4Stack=true -Duser.timezone=Asia/Tokyo -Djava.util.logging.config.file=./logging.properties -Dvertx.hazelcast.config=./$CLUSTER_XML -jar ../target/apis-main-3.0.0-fat.jar -conf ./config3.json -cluster -cluster-host 127.0.0.1

echo '... done'
