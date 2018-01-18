package de.vzg.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "installSolrPlugins",  defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class InstallSolrPluginsMojo extends AbstractSolrMojo {

    @Parameter(property = "pluginCoreMappings", required = true)
    private List<PluginCoreMapping> pluginCoreMappings;

    @Parameter(readonly = true, defaultValue = "${plugin.artifacts}")
    private List<Artifact> pluginDependencies;

    protected void copyPlugins() {
        Map<String, List<String>> pluginCoreMappings = new HashMap<>();
        this.pluginCoreMappings.stream().forEach(core -> {
            String plugin = core.getPlugin();
            List<String> coreList;
            if (!pluginCoreMappings.containsKey(plugin)) {
                coreList = new ArrayList<>();
                pluginCoreMappings.put(plugin, coreList);
            } else {
                coreList = pluginCoreMappings.get(plugin);
            }
            coreList.add(core.getCore());
        });

        Log log = getLog();

        this.pluginDependencies.stream()
            .filter(dep -> {
                String depKey = dep.getGroupId() + ":" + dep.getArtifactId();
                return pluginCoreMappings.containsKey(depKey);
            }).forEach(dep -> {
            String depKey = dep.getGroupId() + ":" + dep.getArtifactId();
            log.info("Copy dependency to SOLR: " + depKey);
            List<String> cores = pluginCoreMappings.get(depKey);

            cores.forEach(core -> {
                Path coreLibDir = getSOLRHome().resolve(core).resolve("lib");
                try {
                    if (!Files.exists(coreLibDir)) {
                        Files.createDirectories(coreLibDir);
                    }
                    Files.copy(dep.getFile().toPath(), coreLibDir.resolve(dep.getFile().getName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(new MojoFailureException("Error while copying solr plugins.", e));
                }
            });

        });
    }


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        copyPlugins();
    }

}
