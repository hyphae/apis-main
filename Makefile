
package:
	mvn package -Dmaven.test.skip=true

clean:
	mvn clean
	rm -f *.log *.err
