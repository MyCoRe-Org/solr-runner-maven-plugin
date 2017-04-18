package de.vzg.maven.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;

public class SOLRRunner {

    private final Path executable;

    // -f param
    private boolean foreground;

    // -p param
    private Integer port = null;

    // -s param
    private String solrHome;

    // -m param
    private String memory;

    // -noprompt flag
    private boolean noPrompt = true;

    public SOLRRunner(Path executable) {
        this.executable = executable;
    }

    public boolean isForeground() {
        return foreground;
    }

    public void setForeground(boolean foreground) {
        this.foreground = foreground;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getSolrHome() {
        return solrHome;
    }

    public void setSolrHome(String solrHome) {
        this.solrHome = solrHome;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public boolean isNoPrompt() {
        return noPrompt;
    }

    public void setNoPrompt(boolean noPrompt) {
        this.noPrompt = noPrompt;
    }

    protected List<String> buildParameterList(String operation) {
        ArrayList<String> parameters = new ArrayList<>();

        parameters.add(this.executable.toString());

        parameters.add(operation);

        if (isForeground()) {
            parameters.add("-f");
        }

        Integer port = getPort();
        if (port != null) {
            parameters.add("-p");
            parameters.add(port.toString());
        }

        String solrHome = getSolrHome();
        if (solrHome != null) {
            parameters.add("-s");
            parameters.add(solrHome);
        }

        String memory = getMemory();
        if (memory != null) {
            parameters.add("-m");
            parameters.add(memory);
        }

        if (noPrompt) {
            parameters.add("-noprompt");
        }

        return parameters;
    }

    public int start(Log log) throws IOException, InterruptedException {
        Set<PosixFilePermission> filePermissions = fixPermissions();

        Files.setPosixFilePermissions(executable, filePermissions);
        Process solrProccess = new ProcessBuilder(buildParameterList("start")).start();
        return waitAndOutput(log, solrProccess);
    }

    private int waitAndOutput(Log log, Process solrProccess) throws IOException, InterruptedException {
        int returnValue;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(solrProccess.getInputStream()))) {
            returnValue = solrProccess.waitFor();
            br.lines().forEach(log::info);
        }

        return returnValue;
    }

    public int stop(Log log) throws IOException, InterruptedException {
        Set<PosixFilePermission> filePermissions = fixPermissions();

        Files.setPosixFilePermissions(executable, filePermissions);
        Process solrProccess = new ProcessBuilder(buildParameterList("stop")).start();
        return waitAndOutput(log, solrProccess);
    }

    public Set<PosixFilePermission> fixPermissions() throws IOException {
        Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(executable);
        Stream<PosixFilePermission> requiredPermissions = Stream
            .of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE);

        requiredPermissions.forEach(requiredPermission -> {
            filePermissions.add(requiredPermission);
        });
        return filePermissions;
    }
}
