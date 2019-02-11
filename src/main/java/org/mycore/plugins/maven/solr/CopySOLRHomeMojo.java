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
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "copyHome")
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
