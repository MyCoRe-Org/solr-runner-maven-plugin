# solr-runner-maven-plugin

This plugin is just a wrapper for SOLR and some extra mojos. It downloads SOLR to the local repository 
`~/.m2/repository/solr-7.7.3/`. It will be started from there, but the path to the solr home will be changed with the 
solr -s parameter. 
```
~/.m2/repository/solr-7.7.3/bin/solr start -p 8983 -s ~/my-solr-location
```

Works with SOLR 6, 7, 8.

## solr-runner:start & solr-runner:stop
Downloads SOLR to local repository and extract it, if it is not present already(in repository).

```
<plugin>
  <groupId>org.mycore.plugins</groupId>
    <artifactId>solr-runner-maven-plugin</artifactId>
    <version>1.2-SNAPSHOT</version>
    <configuration>
      <!-- You can specify a custom SOLR mirror -->
      <solrMirrorURL>http://apache.mirror.iphh.net/lucene/solr/</solrMirrorURL>
      
      <!-- You can specify a custom SOLR version -->
      <solrVersionString>7.7.3</solrVersionString>
      
      <!-- You can specify a custom SOLR port -->
      <solrPort>8983</solrPort>
      
      <!-- You have to specify the SOLR home with your configuration. (see also copyHome) -->
      <solrHome>${user.home}/solr</solrHome>
    </configuration>
</plugin>
``` 

## solr-runner:copyHome
Can be used to copy SOLR home template from your project files to solr home location to keep your project files clean.

```
<plugin>
  <groupId>org.mycore.plugins</groupId>
    <artifactId>solr-runner-maven-plugin</artifactId>
    <version>1.2-SNAPSHOT</version>
    <configuration>      
      <!-- You have to specify the SOLR home with your configuration. -->
      <solrHome>${user.home}/solr</solrHome>
      
      <!-- This is my "template" solr-home which will be copied to solrHome -->
      <solrHomeTemplate>${project.basedir}/src/main/resources/solrHome</solrHomeTemplate>
    </configuration>
</plugin>
```

## solr-runner:installSolrPlugins
This can be used to install plugins to specific cores in your SOLR home.
You need to define a plugin dependency.
```
<plugin>
  <groupId>org.mycore.plugins</groupId>
    <artifactId>solr-runner-maven-plugin</artifactId>
    <version>1.2-SNAPSHOT</version>
    <dependencies>
       <dependency>
          <groupId>my.amazing</groupId>
          <artifactId>solr-plugin</artifactId>
          <version>1.2</version>
       </dependency>
    </dependencies>
    
    <configuration>      
      <!-- You have to specify the SOLR home with your configuration. -->
      <solrHome>${user.home}/solr</solrHome>
      
      ...
    
    </configuration>  
</plugin>
```

And then you need to map the plugin to a specific Core.
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
