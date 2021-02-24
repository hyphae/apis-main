# apis-main

## Introduction
apis-main is the name of energy sharing software for autonomous distributed control of energy developed by Sony CSL. apis-main is installed in each node (battery system connected to the DC grid via a bidirectional DC/DC converter). Via the Device Driver, the software periodically obtains the remaining capacity of the battery and automatically carries out energy sharing between nodes by nagotiating with other apis-mains in accordance with behavior rulesets determined by each node’s remaining battery capacity. Negotiations between apis-mains use a communication line such as Ethernet, and the energy sharing takes place with direct current on the DC grid. apis-main does not rely on software that carries out centralized control of nodes. All nodes have the same software, apis-main, and autonomous distributed control is carried out with only apis-main in each node.  

Refer to the [apis-main_specification](#anchor1)  for more information

![キャプチャ](https://user-images.githubusercontent.com/71874910/94899039-87ea0600-04cd-11eb-96a0-afa5466b3742.PNG)

## Installation
Here is how to install apis-main individually.  
git, maven, groovy and JDK must be installed in advance.

```bash
$ git clone https://github.com/hyphae/apis-bom.git
$ cd apis-bom
$ mvn install
$ cd ../
$ git clone https://github.com/hyphae/apis-common.git
$ cd apis-common
$ mvn install
$ cd ../
$ git cone https://github.com/hypahe/apis-main.git
$ cd apis-main
$ mvn package
```

## Running
Here is how to run apis-main individually.  
```bash
$ cd exe
$ bash start.sh
```

## Stopping
Here is how to stop apis-main individually.  
```bash
$ cd exe
$ bash stop.sh
```

## Parameter Setting
Set the following parameters in the exe folder as necessary.   
Refer to "Chapter 6, About Configuration Files" in the [apis-main_specification](#anchor1) for more information.

&emsp;config.json   
&emsp;&emsp;&emsp;- communityId   &emsp;(default : oss_communityId)  
&emsp;&emsp;&emsp;- clusterId     &emsp;(default : oss_clusterId)  
&emsp;&emsp;&emsp;- unitId        &emsp;(default : E001)  
&emsp;&emsp;&emsp;- unitName      &emsp;(default : E001)  
&emsp;&emsp;&emsp;- systemType    &emsp;(default : dcdc_emulator)  

&emsp;policy.json    
&emsp;&emsp;&emsp;- memberUnitIds  &emsp;(default : "E001", "E002", "E003", "E004")

&emsp;cluster.xml  
&emsp;&emsp;&emsp;- \<member\>  &emsp;(default : 127.0.0.1)  
&emsp;&emsp;&emsp;- \<interface\>  &emsp;(default : 127.0.0.1)  

&emsp;start.sh  
&emsp;&emsp;&emsp;-conf &emsp; (default : ./config.json)  
&emsp;&emsp;&emsp;-cluster-host &emsp; (default : 127.0.0.1)  



## Tips
In order to run multiple apis-mains on the same PC, multiple config.json and start.sh are needed.

### \<Example\>
To run 4 apis-mains on the same PC.

[Parameter Setting Example]  

\<First Setting\>  
&emsp;config.json     
&emsp;&emsp;&emsp;- unitId     : E001  
&emsp;&emsp;&emsp;- unitName   : E001  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog/'uuuu'/'MM'/'dd'" 

&emsp;start.sh  
&emsp;&emsp;&emsp;-conf ./config.json  
&emsp;&emsp;&emsp;-cluster-host 127.0.0.1  
<br />

\<Second Setting\>  
&emsp;config2.json   
&emsp;&emsp;&emsp;- unitId : E002  
&emsp;&emsp;&emsp;- unitName   : E002  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state2/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog2/'uuuu'/'MM'/'dd'"  

&emsp;start2.sh  
&emsp;&emsp;&emsp;-conf ./config2.json   
&emsp;&emsp;&emsp;-cluster-host 127.0.0.1  
<br />

<\Third Setting\>  
&emsp;config3.json    
&emsp;&emsp;&emsp;- unitId : E003  
&emsp;&emsp;&emsp;- unitName   : E003  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state3/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog3/'uuuu'/'MM'/'dd'"  

&emsp;start3.sh  
&emsp;&emsp;&emsp;-conf ./config3.json  
&emsp;&emsp;&emsp;-cluster-host 127.0.0.1  
<br />

\<Fourth Setting\>  
&emsp;config4.json  
&emsp;&emsp;&emsp;- unitId : E004  
&emsp;&emsp;&emsp;- unitName   : E004  
&emsp;&emsp;&emsp;- stateFileFormat   : "{tmpdir}/apis/state4/%s"  
&emsp;&emsp;&emsp;- dealLogDirFormat   : "{tmpdir}/apis/dealLog4/'uuuu'/'MM'/'dd'"  

&emsp;start4.sh  
&emsp;&emsp;&emsp;-conf ./config4.json  
&emsp;&emsp;&emsp;-cluster-host 127.0.0.1  

All other files are used in common.

<br />

[Running]  
```bash
$ cd exe  
$ bash start.sh  
$ bash start2.sh  
$ bash start3.sh  
$ bash start4.sh  
```
  
<a id="anchor1"></a>
## Documentation

&emsp;[apis-main_specificaton(EN)](https://github.com/hyphae/apis-main/blob/master/doc/en/apis-main_specification_en.md)  
&emsp;[apis-main_specificaton(JP)](https://github.com/hyphae/apis-main/blob/master/doc/jp/apis-main_specification.md)


## API Specification  

An example of creating an API specification using the Javadoc command is shown below.  
(For Ubuntu18.04)  
  
```bash  
$ export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/  
$ cd apis-main  
$ mvn javadoc:javadoc  
```  

The API specification is created in apis-main/target/site/apidocs/.  

## License
&emsp;[Apache License Version 2.0](https://github.com/hyphae/apis-main/blob/master/LICENSE)


## Notice
&emsp;[Notice](https://github.com/hyphae/apis-main/blob/master/NOTICE.md)
