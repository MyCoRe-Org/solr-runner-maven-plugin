/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.plugins.maven.solr;

import com.jcraft.jsch.Session;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mojo(name = "installConfigSet")
public class InstallConfigSet extends AbstractSolrMojo {

    @Parameter(property = "gitRepository", required = true)
    private String gitRepository;

    @Parameter(property = "configSetName", required = true)
    private String configSetName;


    private Path getGitRepositoryPath() throws MojoFailureException {
        return getSOLRHome().resolve("configsets/").resolve(configSetName);
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
    }
}
