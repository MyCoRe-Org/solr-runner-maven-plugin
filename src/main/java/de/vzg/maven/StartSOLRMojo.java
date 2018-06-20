package de.vzg.maven;

import de.vzg.maven.tools.SOLRRunner;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "start")
public class StartSOLRMojo extends AbstractSolrMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        setUpSolr();
        try {
            SOLRRunner runner = buildRunner();
            runner.setPort(this.solrPort);

            if (runner.start() != 0) {
                throw new MojoExecutionException("Solr command did not return 0. See Log for errors.");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Error while starting SOLR!", e);
        }
    }

}
