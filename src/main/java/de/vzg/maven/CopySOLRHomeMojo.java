package de.vzg.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "copyHome",  defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class CopySOLRHomeMojo extends AbstractSolrMojo {

    @Parameter(property = "solrHomeTemplate", required = true)
    private File solrHomeTemplate;

    protected Path getSOLRHomeTemplate() {
        return this.solrHomeTemplate.toPath();
    }

    protected void copyHome() throws MojoFailureException {
        try {
            Path solrHome = getSOLRHome();
            getLog().info("Copy solr home to " + solrHome.toAbsolutePath().toString());
            Files.walkFileTree(getSOLRHomeTemplate(), new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                    String relativeName = getSOLRHomeTemplate().relativize(dir).toString();
                    Path newDirectoryPath = solrHome.resolve(relativeName);
                    if (!Files.exists(newDirectoryPath)) {
                        Files.createDirectories(newDirectoryPath);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relativeName = getSOLRHomeTemplate().relativize(file).toString();
                    Path newFilePath = getSOLRHome().resolve(relativeName);
                    Files.copy(file, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoFailureException("Error while copying solr home template", e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        copyHome();
    }

}
