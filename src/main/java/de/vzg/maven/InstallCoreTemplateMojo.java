package de.vzg.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

import com.jcraft.jsch.Session;

@Mojo(name = "installCoreTemplate")
public class InstallCoreTemplateMojo extends AbstractSolrMojo {

    @Parameter(property = "gitRepository", required = true)
    private String gitRepository;

    @Parameter(property = "configSetName", required = true)
    private String configSetName;

    @Parameter(property = "coreName", required = true)
    private String coreName;


    private Path getGitRepositoryPath() throws MojoFailureException {
        return getSOLRPath().resolve("server/solr/configsets/").resolve(configSetName);
    }

    private void checkoutOrUpdate() throws MojoFailureException {
        final Path repositoryPath = getGitRepositoryPath();

        if(Files.exists(repositoryPath)){
            try {
                Git git = Git.open(repositoryPath.toFile());
                configureGit(git.pull()).call();
            } catch (GitAPIException|IOException e) {
                throw new MojoFailureException("Error while open/updating git repository: " + repositoryPath.toString(), e);
            }
        } else {
            try {
                Git git = configureGit(Git.cloneRepository())
                    .setURI(gitRepository )
                    .setDirectory( repositoryPath.toFile() )
                    .call();
            } catch (GitAPIException e) {
                throw new MojoFailureException("Error while checkout repository: " + gitRepository,e);
            }
        }

    }

    private <T extends TransportCommand> T configureGit(T tc){
        tc.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                if(transport instanceof SshTransport){
                    ((SshTransport) transport).setSshSessionFactory(new JschConfigSessionFactory() {
                        @Override protected void configure(OpenSshConfig.Host hc, Session session) {
                            session.setConfig("StrictHostKeyChecking", "no");
                        }
                    });
                }
            }
        });
        return tc;
    }


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkoutOrUpdate();
        try {
           buildRunner().installCore(coreName, configSetName);
        } catch (IOException|InterruptedException e) {
            throw new MojoExecutionException("Error while install solr core " + coreName, e);
        }
    }
}
