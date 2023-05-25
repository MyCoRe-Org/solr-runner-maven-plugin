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

import org.apache.maven.plugins.annotations.Parameter;
import org.mycore.plugins.maven.solr.tools.SOLRCoreReadyChecker;
import org.mycore.plugins.maven.solr.tools.SOLRRunner;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "start")
public class StartSOLRMojo extends AbstractSolrMojo {

    @Parameter(property = "coreReadyTries", required = false, defaultValue = "10")
    protected Integer coreReadyTries;

    @Parameter(property = "coreReadyRetryWaitTimeInMillis", required = false, defaultValue = "1000")
    protected Integer coreReadyRetryWaitTimeInMillis;

    /**
     * If true, a shutdown hook will be registered to stop the SOLR instance when the JVM is terminated.
     */
    @Parameter(property = "shutdownHook", required = false, defaultValue = "false")
    protected Boolean shutdownHook;

    public void execute() throws MojoExecutionException, MojoFailureException {
        setUpSolr();
        try {
            SOLRRunner runner = buildRunner();
            runner.setPort(this.solrPort);

            Process process = runner.start();
            int result = runner.waitAndOutput(process);
            if (result != 0) {
                throw new MojoExecutionException("Solr command did not return 0. See Log for errors.");
            }

            SOLRCoreReadyChecker readyChecker = new SOLRCoreReadyChecker(solrPort, "localhost");
            if(coreReadyTries > 0) {
                readyChecker.setRetries(coreReadyTries);
            }
            if(coreReadyRetryWaitTimeInMillis > 0) {
                readyChecker.setRetryWaitTimeMS(coreReadyRetryWaitTimeInMillis);
            }
            readyChecker.setLog(getLog());
            readyChecker.waitForAllCoresReady();

            if (this.shutdownHook != null && this.shutdownHook) {
                getLog().info("Shutdown hook registered to stop SOLR when JVM is terminated.");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    getLog().info("Check if SOLR is running...");
                    try {
                        readyChecker.setRetries(1);
                        readyChecker.setLog(null);
                        readyChecker.setRetryWaitTimeMS(0);
                        readyChecker.waitForAllCoresReady();
                    } catch (InterruptedException e) {
                        getLog().info("SOLR is not running anymore.");
                        return;
                    }
                    getLog().info("SOLR is still running. Stop it now.");
                    try {
                        runner.stop();
                    } catch (IOException|InterruptedException e) {
                        getLog().error("Error while stopping SOLR!", e);
                    }
                }));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Error while starting SOLR!", e);
        }
    }

}
