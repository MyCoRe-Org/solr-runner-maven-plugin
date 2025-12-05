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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.mycore.plugins.maven.solr.tools.SOLRRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

abstract class AbstractSolrMojo extends AbstractMojo {

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySystemSession;

    @Parameter(property = "solrMirror", defaultValue = "https://dlcdn.apache.org/")
    private URI solrMirrorURL;

    @Parameter(property = "solrArchive", defaultValue = "https://archive.apache.org/dist/")
    private URI solrArchiveURL;

    @Parameter(property = "solrVersion", defaultValue = "9.8.1")
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
                getLog().debug("Download " + getSolrTgzFileName() + " because it does not exists!");
                downloadTGZ();
            }
            extractSolrTgz();
        }
    }

    protected void downloadTGZ() throws MojoFailureException {
        String solrTgzFileName = getSolrTgzFileName();
        String downloadPath = getDownloadPath();
        URI mirrorURI = solrMirrorURL.resolve(downloadPath + solrTgzFileName);

        List<Exception> supressed = new ArrayList<>();
        Path tgzPath = getTGZPath();
        try (HttpClient downloader = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()) {
            HttpRequest downloadRequest = HttpRequest.newBuilder().uri(mirrorURI).build();
            HttpResponse<InputStream> response = null;
            try {
                getLog().debug("Downloading " + downloadRequest.uri());
                response = downloader.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    getLog().info("Apache mirror does not provide selected SOLR version.");
                    response = null;
                }
            } catch (IOException e) {
                getLog().error(e.getMessage());
                supressed.add(e);
            }
            if (response == null) {
                URI archiveURI = solrArchiveURL.resolve(downloadPath + solrTgzFileName);
                downloadRequest = HttpRequest.newBuilder().uri(archiveURI).build();
                getLog().debug("Downloading " + downloadRequest.uri());
                response = downloader.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new MojoFailureException("Could not download selected SOLR version: " + solrVersionString);
                }
            }
            try (InputStream is = response.body()) {
                getLog().info("Downloading " + response.uri() + " to " + tgzPath.toString());
                Files.copy(is, tgzPath, StandardCopyOption.REPLACE_EXISTING);
                FileTime lastModified = getLastModifiedFileTime(response);
                if (lastModified != null) {
                    Files.setLastModifiedTime(tgzPath, lastModified);
                }
            } catch (IOException e) {
                getLog().error(e.getMessage());
                supressed.add(e);
            }
        } catch (InterruptedException | IOException e) {
            getLog().error(e.getMessage());
            supressed.add(e);
        }
        if (!supressed.isEmpty()) {
            MojoFailureException e = new MojoFailureException("Could not download or extract SOLR archive.");
            supressed.forEach(e::addSuppressed);
            throw e;
        }
    }

    public FileTime getLastModifiedFileTime(HttpResponse<?> response) {
        String lastModified = response.headers().firstValue("Last-Modified").orElse(null);
        if (lastModified == null) {
            return null;
        }
        try {
            ZonedDateTime dateTime = ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME);
            return FileTime.from(dateTime.toInstant());
        } catch (DateTimeParseException e) {
            getLog().warn("Failed to parse the Last-Modified header", e);
            return null;
        }
    }


    protected boolean isSOLRZipExisting() throws MojoFailureException {
        Path solrTGZPath = getTGZPath();
        getLog().debug("Checking if SOLR archive exists: " + solrTGZPath.toString());
        return Files.exists(solrTGZPath);
    }

    protected boolean isSOLRExecutableExisting() throws MojoFailureException {
        return Files.exists(getSOLRExecutablePath());
    }

    protected void extractSolrTgz() throws MojoFailureException {
        Log log = getLog();
        Path solrPath = getSOLRPath();
        String solrFolderName = getSOLRFolderName();
        log.info("Extracting " + getTGZPath() + " to " + solrPath + " \u2026");

        try (InputStream is = Files.newInputStream(getTGZPath());
            TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(is))) {
            Files.createDirectories(solrPath);
            ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!(entry instanceof TarArchiveEntry tarEntry)) {
                    throw new IllegalStateException(
                        "Not a tar entry: " + entry.getName() + " " + entry.getClass().getName());
                }
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
                    applyAttributes(target, tarEntry);
                } else {
                    log.debug("Extract file: " + name);
                    Files.createDirectories(target.getParent());
                    Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
                    applyAttributes(target, tarEntry);
                }
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error while reading TGZ-File!", e);
        }
    }

    private void applyAttributes(Path target, TarArchiveEntry tarEntry) throws IOException {
        PosixFileAttributeView posixAttributes = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (posixAttributes != null) {
            posixAttributes.setPermissions(mapToPosixFilePermissions(tarEntry));
        }
        Files.setLastModifiedTime(target, tarEntry.getLastModifiedTime());
    }

    private static EnumSet<PosixFilePermission> mapToPosixFilePermissions(TarArchiveEntry entry) {
        int mode = entry.getMode();
        return EnumSet.allOf(PosixFilePermission.class).stream()
            .filter(permission -> (mode & getPermissionBit(permission)) != 0)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(PosixFilePermission.class)));
    }

    private static int getPermissionBit(PosixFilePermission permission) {
        return switch (permission) {
            case OWNER_READ -> 0400;
            case OWNER_WRITE -> 0200;
            case OWNER_EXECUTE -> 0100;
            case GROUP_READ -> 0040;
            case GROUP_WRITE -> 0020;
            case GROUP_EXECUTE -> 0010;
            case OTHERS_READ -> 0004;
            case OTHERS_WRITE -> 0002;
            case OTHERS_EXECUTE -> 0001;
        };
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

    private Path getTGZPath() throws MojoFailureException {
        return getLocalRepoPath().resolve(getSolrTgzFileName());
    }

    public Path getSOLRPath() throws MojoFailureException {
        return getLocalRepoPath().resolve(getSOLRFolderName());
    }

    private String getSolrTgzFileName() {
        return "solr-" + solrVersionString + ".tgz";
    }

    private String getSOLRFolderName() {
        return "solr-" + solrVersionString;
    }

    public Path getLocalRepoPath() throws MojoFailureException {
        return repositorySystemSession.getLocalRepository().getBasedir().toPath();
    }

    private String getDownloadPath() {
        String[] versionParts = solrVersionString.split("\\.");
        int majorVersion = Integer.parseInt(versionParts[0]);
        String prefix = switch (majorVersion) {
            case 1, 2, 3, 4, 5, 7, 8 -> "lucene/solr/";
            default -> "solr/solr/";
        };
        return prefix + solrVersionString + "/";
    }

}
