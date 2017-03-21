# Git Oozie Custom Actions
This repository contains custom oozie actions for deployment of source code from a git repository to HDFS

## Install
In order to manually install this custom action on a
[BACH](https://github.com/bloomberg/chef-bach) cluster:

Install maven 3.3 or higher
```
wget http://mirrors.sonic.net/apache/maven/maven-3/3.3.3/binaries/apache-maven-3.3.3-bin.tar.gz
tar -zxf apache-maven-3.3.3-bin.tar.gz
sudo cp -R apache-maven-3.3.3 /usr/local
sudo ln -s /usr/local/apache-maven-3.3.3/bin/mvn /usr/bin/mvn
mvn -version
```


Build the project into a jar:
```
$ mvn package
```

Copy the file in `target/gitoozie-1.0-SNAPSHOT.jar` to
`/usr/hdp/current/oozie-server/libext/gitoozie-1.0-SNAPSHOT.jar` on the node
where the oozie server is installed.

Edit the `/etc/oozie/conf/oozie-site.xml` file adding the following lines:
```
    <property>
       <name>oozie.service.ActionService.executor.ext.classes</name>
       <value>
         org.apache.oozie.action.email.EmailActionExecutor,
         org.apache.oozie.action.hadoop.HiveActionExecutor,
         org.apache.oozie.action.hadoop.ShellActionExecutor,
         org.apache.oozie.action.hadoop.SqoopActionExecutor,
         org.apache.oozie.action.hadoop.DistcpActionExecutor,
+        com.bloomberg.hbase.oozieactions.GitActionExecutor,
       </value>
     </property>

     <property>
       <name>oozie.service.SchemaService.wf.ext.schemas</name>
       <value>
         shell-action-0.1.xsd,
         shell-action-0.2.xsd,
         shell-action-0.3.xsd,
         email-action-0.1.xsd,
         hive-action-0.2.xsd,
         hive-action-0.3.xsd,
         hive-action-0.4.xsd,
         hive-action-0.5.xsd,
         sqoop-action-0.2.xsd,
         sqoop-action-0.3.xsd,
         ssh-action-0.1.xsd,
         ssh-action-0.2.xsd,
         distcp-action-0.1.xsd,
+        git-action-0.1.xsd
        </value>
     </property>
```

If you care about logging, you should add the following line to the end of `/etc/oozie/conf/oozie-log4j.properties`:
```
log4j.logger.com.bloomberg.git.oozieactions=ALL, oozie
```
This will make all log trace generated by the custom actions available in the oozie logs.

Then stop oozie:
```
$ sudo service oozie stop
```

Rebuild the oozie war:
```
$ /usr/hdp/current/oozie-server/bin/oozie-setup.sh prepare-war
```

Start oozie:
```
$ sudo service oozie start
```

Next, place the oozie jar with dependencies, named `gitoozie-1.0-SNAPSHOT-jar-with-dependencies.jar` into the shared library on HDFS.

To do this, run the following as user `oozie` on your name node, `/usr/hdp/current/oozie-server/bin/oozie-setup.sh sharelib create -fs hdfs://<name-node>`, thereby initializing the oozie shared library on HDFS.

Next, run `oozie admin -sharelibupdate` to find the path to the sharelib directory.

Next, use the `hdfs dfs -mkdir <path>` command to create a path inside that sharedlib folder (`.../lib/lib_<datetime>`) named `git`.

Then, you'll want to `hdfs dfs -put <path>` the `gitoozie-1.0-SNAPSHOT-jar-with-dependencies.jar` file into the `git` folder you just created.

After that, simply run another `oozie admin -sharelibupdate` and you should be all set to use the action.

N.B. - To use the action, you must add a line in your job.properties to use the oozie shared lib, so just append `oozie.use.system.libpath=true` to the bottom of your job.properties file.
