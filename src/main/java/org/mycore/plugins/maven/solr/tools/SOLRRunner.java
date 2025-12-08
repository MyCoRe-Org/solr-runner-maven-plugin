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

package org.mycore.plugins.maven.solr.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    // -a param
    private String additionalVMParams;

    private boolean cloudMode = false;

    // -noprompt flag
    private boolean noPrompt = true;

    private boolean force = false;

    private boolean verbose = false;

    private String additionalParams;

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

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isCloudMode() {
        return cloudMode;
    }

    public void setCloudMode(boolean cloudMode) {
        this.cloudMode = cloudMode;
    }

    protected List<String> buildParameterList(String... operation) {
        ArrayList<String> parameters = getRawParameters(operation);

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

        String additionalVMParams = getAdditionalVMParams();
        if (additionalVMParams != null) {
            parameters.add("-a");
            parameters.add(additionalVMParams);
        }

        if (isCloudMode() && Arrays.stream(operation).noneMatch("-c"::equals)) {
            parameters.add("-c");
        }

        if (noPrompt) {
            parameters.add("-noprompt");
        }

        if (force) {
            parameters.add("-force");
        }

        if (verbose) {
            parameters.add("-v");
        }

        if(this.additionalParams != null && !this.additionalParams.isEmpty()) {
            Stream<String> additionalParams = Arrays.stream(this.additionalParams.split(" "));
            parameters.addAll(additionalParams.toList());
        }

        return parameters;
    }

    public ArrayList<String> getRawParameters(String... operation) {
        ArrayList<String> parameters = new ArrayList<>();

        parameters.add(this.executable.toString());

        parameters.addAll(Arrays.stream(operation).collect(Collectors.toList()));
        return parameters;
    }

    public int uploadSecurityJson() throws IOException, InterruptedException {
        //  zk cp $secruity_json zk:security.json -z localhost:9983
        String securityJsonPath = (this.solrHome.endsWith("/") ? this.solrHome : this.solrHome + "/") + "security.json";

        if (!Files.exists(Paths.get(securityJsonPath))) {
            return 0;
        }

        final List<String> uploadSecurityJsonParameterList = getRawParameters("zk", "cp",
            securityJsonPath, "zk:security.json", "-z", "localhost:" + (getPort() + 1000));

        Process solrProccess = new ProcessBuilder(uploadSecurityJsonParameterList)
            .redirectErrorStream(true)
            .inheritIO()
            .start();
        return waitAndOutput(solrProccess);
    }

    public int installCore(String coreName, String configSet) throws IOException, InterruptedException {
        final List<String> createCoreParameterList = buildParameterList("create", "-d", configSet, "-c", coreName);

        createCoreParameterList.remove("-noprompt");

        Process solrProccess = new ProcessBuilder(createCoreParameterList)
            .redirectErrorStream(true)
            .inheritIO()
            .start();
        return waitAndOutput(solrProccess);
    }

    public int start() throws IOException, InterruptedException {
        Process solrProccess = new ProcessBuilder(buildParameterList("start")).redirectErrorStream(true).inheritIO()
            .start();
        return waitAndOutput(solrProccess);
    }

    private int waitAndOutput(Process solrProccess) throws IOException, InterruptedException {
        return solrProccess.waitFor();
    }

    public int stop() throws IOException, InterruptedException {
        Process solrProccess = new ProcessBuilder(buildParameterList("stop")).redirectErrorStream(true).inheritIO()
            .start();
        return waitAndOutput(solrProccess);
    }

    public String getAdditionalVMParams() {
        return additionalVMParams;
    }

    public void setAdditionalVMParams(String additionalVMParams) {
        this.additionalVMParams = additionalVMParams;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setAdditionalParams(String additionalParams) {
        this.additionalParams = additionalParams;
    }
}
