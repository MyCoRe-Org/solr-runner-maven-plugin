package de.vzg.maven;

import de.vzg.maven.tools.SOLRRunner;

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

abstract class AbstractSolrMojo extends AbstractMojo {

    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    private ArtifactRepository localRepository;

    @Parameter(property = "solrMirror", defaultValue = "http://apache.mirror.iphh.net/lucene/solr/")
    private URL solrMirrorURL;

    @Parameter(property = "solrVersion", defaultValue = "7.3.1")
    private String solrVersionString;

    @Parameter(property = "solrHome", required = false)
    private File solrHome;

    @Parameter(property = "solrPort", required = true, defaultValue = "8983") protected Integer solrPort;

    protected void setUpSolr() throws MojoFailureException {
        if (!isSOLRFolderExisting()) {
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

    protected boolean isSOLRFolderExisting() throws MojoFailureException {
        return Files.exists(getSOLRPath());
    }

    protected void extractSOLRZip() throws MojoFailureException {
        Log log = getLog();
        Path solrPath = getSOLRPath();
        String solrFolderName = getSOLRFolderName();

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

        solrRunner.setForeground(false);
        solrRunner.setSolrHome(this.getSOLRHome().toString());
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
