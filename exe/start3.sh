echo 'call start.sh'

CLUSTER_XML=cluster.xml
if [ "$(uname)" = 'Darwin' ] ; then
	CLUSTER_XML=cluster-mac.xml
fi
java -Djava.net.preferIPv4Stack=true -Duser.timezone=Asia/Tokyo -Dlogback.configurationFile=./logback.xml -Dvertx.hazelcast.config=./$CLUSTER_XML -jar ../target/apis-main-4.5.10-fat.jar -conf ./config3.json -cluster -cluster-host 127.0.0.1

echo '... done'
