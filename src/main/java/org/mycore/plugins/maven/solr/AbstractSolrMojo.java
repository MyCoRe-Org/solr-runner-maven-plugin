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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.mycore.plugins.maven.solr.tools.SOLRRunner;

abstract class AbstractSolrMojo extends AbstractMojo {

    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    private ArtifactRepository localRepository;

    @Parameter(property = "solrMirror", defaultValue = "http://apache.mirror.iphh.net/lucene/solr/")
    private URL solrMirrorURL;

    @Parameter(property = "solrVersion", defaultValue = "7.3.1")
    private String solrVersionString;

    @Parameter(property = "additionalVMParam", defaultValue = "-XX:+IgnoreUnrecognizedVMOptions")
    private String additionalVMParam;

    @Parameter(property = "solrHome", required = false)
    private File solrHome;

    @Parameter(property = "solrPort", required = true, defaultValue = "8983") protected Integer solrPort;

    @Parameter(property = "force", required = false, defaultValue = "false") protected Boolean force;

    @Parameter(property = "cloudMode", required = false, defaultValue = "false")
    protected Boolean cloudMode;

    @Parameter(property = "securityJsonContent", required = false)
    protected String securityJsonContent = null;

    protected void setUpSolr() throws MojoFailureException {
        if (!isSOLRExecutableExisting()) {
            if (!isSOLRZipExisting()) {
                getLog().debug("Download solr-zip because it does not exists!");
                downloadZip();
            }
            extractSOLRZip();
        }
    }

    protected void downloadZip() throws MojoFailureException {
        String file = solrMirrorURL.getFile();

        if (!file.endsWith("/")) {
            file += "/";
        }

        String solrZipFileName = getSOLRZipFileName();
        try {
            URL url = buildNewURL(solrMirrorURL, file + solrVersionString + "/" + solrZipFileName);

            try (InputStream is = url.openStream()) {
                Path zipPath = getZipPath();
                getLog().info("Downloading " + url.toString() + " to " + zipPath.toString());
                Files.copy(is, zipPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoFailureException("Error while downloading!", e);
            }
        } catch (MalformedURLException e) {
            throw new MojoFailureException("Could not build URL to download SOLR!", e);
        }
    }

    protected boolean isSOLRZipExisting() throws MojoFailureException {
        Path solrZipFilePath = getZipPath();
        getLog().debug("Checking if solr zip exists: " + solrZipFilePath.toString());
        return Files.exists(solrZipFilePath);
    }

    protected boolean isSOLRExecutableExisting() throws MojoFailureException {
        return Files.exists(getSOLRExecutablePath());
    }

    protected void extractSOLRZip() throws MojoFailureException {
        Log log = getLog();
        Path solrPath = getSOLRPath();
        String solrFolderName = getSOLRFolderName();
        log.info("Extracting " + getZipPath() + " to " + solrPath + " \u2026");

        try (ZipFile zipFile = new ZipFile(getZipPath().toFile())) {
            List<? extends ZipEntry> zipEntries = zipFile.stream().collect(Collectors.toList());
            // use for loop for easy error handling
            for (ZipEntry entry : zipEntries) {
                String name = entry.getName();

                if (name.startsWith(solrFolderName)) {
                    name = name.substring(solrFolderName.length());
                }

                if (name.startsWith("/")) {
                    name = name.substring(1);
                }

                Path target = solrPath.resolve(name);

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    log.debug("Extract file: " + name);
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new MojoFailureException("Error while extracting :" + name);
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while reading ZIP-File!");
        }
    }

    protected SOLRRunner buildRunner() throws MojoFailureException {
        SOLRRunner solrRunner = new SOLRRunner(getSOLRExecutablePath());
        solrRunner.setForce(force);
        solrRunner.setAdditionalVMParams(additionalVMParam);
        solrRunner.setForeground(false);
        solrRunner.setSolrHome(this.getSOLRHome().toString());
        solrRunner.setCloudMode(this.cloudMode);
        return solrRunner;
    }

    public Path getSOLRHome() {
        return this.solrHome.toPath();
    }

    private Path getSOLRExecutablePath() throws MojoFailureException {
        Path solr = getSOLRPath().resolve("bin/");

        if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")){
            solr = solr.resolve("solr.cmd");
        } else {
            solr = solr.resolve("solr");
        }

        return solr;
    }

    private Path getZipPath() throws MojoFailureException {
        return getLocalRepoPath().resolve(getSOLRZipFileName());
    }

    public Path getSOLRPath() throws MojoFailureException {
        return getLocalRepoPath().resolve(getSOLRFolderName());
    }

    private String getSOLRZipFileName() {
        return "solr-" + solrVersionString + ".zip";
    }

    private String getSOLRFolderName() {
        return "solr-" + solrVersionString;
    }

    public Path getLocalRepoPath() throws MojoFailureException {
        try {
            return Paths.get(new URL(localRepository.getUrl()).toURI());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new MojoFailureException("Could not resolve path to local .m2", e);
        }
    }

    private URL buildNewURL(URL base, String file) throws MalformedURLException {
        return new URL(base.getProtocol(), base.getHost(), file);
    }
}
