package de.vzg.maven;

import de.vzg.maven.tools.SOLRRunner;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "stop")
public class StopSOLRMojo extends AbstractSolrMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        setUpSolr();
        try {
            SOLRRunner runner = buildRunner();
            if (runner.stop(getLog()) != 0) {
                throw new MojoExecutionException("Solr command did not return 0. See Log for errors.");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Error while stopping SOLR!", e);
        }
    }

}
