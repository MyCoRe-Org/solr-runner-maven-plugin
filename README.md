# solr-runner-maven-plugin

This Plugin is just a wrapper for SOLR and some extra mojos. Works with SOLR 6.

##solr-runner:start & solr-runner:stop
Downloads SOLR to local repository and extract it, if it is not present already(in repository).

```
<plugin>
  <groupId>de.vzg.maven</groupId>
    <artifactId>solr-runner-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <configuration>
      <!-- You can specify a custom SOLR mirror -->
      <solrMirror>http://apache.mirror.iphh.net/lucene/solr/</solrMirror>
      
      <!-- You can specify a custom SOLR version -->
      <solrVersion>6.5.0</solrVersion>
      
      <!-- You can specify a custom SOLR port -->
      <solrPort>8983</solrPort>
      
      <!-- You have to specify the SOLR home with your configuration. (see also copyHome) -->
      <solrHome>${project.basedir}/solr</solrHome>
    </configuration>
</plugin>
``` 

## solr-runner:copyHome
Can be used to copy SOLR home in your project to the real SOLR home.

```
<plugin>
  <groupId>de.vzg.maven</groupId>
    <artifactId>solr-runner-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <configuration>      
      <!-- You have to specify the SOLR home with your configuration. -->
      <solrHome>${project.basedir}/solr</solrHome>
      
      <!-- This is my "template" solr-home which will be copied to solrHome -->
      <solrHomeTemplate>${project.basedir}/src/main/resources/solrHome</solrHomeTemplate>
    </configuration>
</plugin>
```

## solr-runner:installSolrPlugins
This can be used to install Plugins to specific cores in your SOLR home.
You need to define a plugin dependency.
```
<plugin>
  <groupId>de.vzg.maven</groupId>
    <artifactId>solr-runner-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <dependencies>
       <dependency>
          <groupId>my.amazing</groupId>
          <artifactId>solr-plugin</artifactId>
          <version>1.2</version>
       </dependency>
    </dependencies>
    
    <configuration>      
      <!-- You have to specify the SOLR home with your configuration. -->
      <solrHome>${project.basedir}/solr</solrHome>
      
      ...
    
    </configuration>  
</plugin>
```

And then you need to map the Plugin to a specific Core.
```
...
<configuration>      
  <pluginCoreMappings>
      <pluginCoreMapping>
          <core>mySolrCore</core>
          <plugin>my.amazing:solr-plugin</plugin>
      </pluginCoreMapping>
  </pluginCoreMappings>
</configuration>
...
```

## lifecycle mapping

You can bind those goals to run with your integration tests.

```
...
<executions>
  <execution>
    <id>start-solr</id>
    <phase>pre-integration-test</phase>
    <goals>
      <goal>copyHome</goal>
      <goal>installSolrPlugins</goal>
      <goal>start</goal>
    </goals>
  </execution>
  <execution>
    <id>stop-solr</id>
    <phase>post-integration-test</phase>
    <goals>
      <goal>stop</goal>
    </goals>
  </execution>
</executions>
...
```
