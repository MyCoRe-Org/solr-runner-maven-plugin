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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonStreamParser;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

public class SOLRCoreReadyChecker {

    private final int port;
    private final String host;
    private int retryWaitTimeMS;
    private int retries;
    private Log log;

    public SOLRCoreReadyChecker(int port, String host) {
        this.port = port;
        this.host = host;
        retryWaitTimeMS = 1000;
        retries = 10;
        log = null;
    }

    public void waitForAllCoresReady() throws InterruptedException, MojoExecutionException {
        if(log != null) {
            log.info("Waiting for all cores to be ready...");
        }
        int tries;
        for (tries = 0; tries < retries; tries++) {
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest onlineRequest = HttpRequest.newBuilder()
                    .uri(URI.create(buildCoresURL())).build();
                HttpResponse<String> response = client.send(onlineRequest,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    log.info("SOLR requires authentication, skipping readiness check and wait " +
                        "5 seconds.");
                    Thread.sleep(5000);
                    return;
                }
                if (response.statusCode() == 404) {
                    throw new MojoExecutionException(
                        "Could not get SOLR core states (" + onlineRequest.uri() + "). 404:\n" + response.body());
                }

                JsonStreamParser parser = new JsonStreamParser(response.body());
                JsonElement root = parser.next();

                try {
                    JsonObject rootObject = root.getAsJsonObject();
                    if (allCoresReady(rootObject)) {
                        log.info("All cores are ready.");
                        return;
                    } else if (log != null) {
                        log.info("SOLR not ready yet, waiting...");
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not parse JSON document from SOLR (response code:" + response.statusCode() + "):\n"
                        + response.body());
                }
            } catch (IOException ex) {
                if (log != null) {
                    log.info("SOLR not ready yet, waiting...");
                }
            } finally {
                Thread.sleep(retryWaitTimeMS);
            }
        }
        throw new InterruptedException(
            "Solr not ready after " + tries + " tries. )" + (retries * retryWaitTimeMS) + " ms)");
    }

    public boolean allCoresReady(JsonObject root) throws IOException {
        JsonElement status = root.get("status");
        if (status == null) {
            if (log != null) {
                log.debug("Status is null");
            }
            return false;
        }

        if (!status.isJsonObject()) {
            if (log != null) {
                log.debug("Status is not a JsonObject");
            }
            return false;
        }
        JsonObject statusObject = status.getAsJsonObject();

        Set<Map.Entry<String, JsonElement>> coreEntries = statusObject.entrySet();
        if (coreEntries.isEmpty()) {
            if (log != null) {
                log.debug("No cores found");
            }
            return true; // no cores is a valid state and means that solr is ready
        }

        return coreEntries.stream()
            .map(this::parseCore)
            .allMatch(core -> getUptime(core) > 0);
    }

    private JsonObject parseCore(Map.Entry<String, JsonElement> entry) {
        JsonElement value = entry.getValue();
        if (!value.isJsonObject()) {
            if(log != null) {
                log.debug("Core " + entry.getKey() + " is not a JsonObject");
            }
        }
        return value.getAsJsonObject();
    }

    private Integer getUptime(JsonObject core) {
        if(core == null) {
            return -1;
        }

        JsonElement uptime = core.get("uptime");
        if (uptime == null) {
            if (log != null && log.isDebugEnabled()) {
                log.debug("Uptime is null: " + core);
            }
            return -1;
        }
        try {
            if (uptime.isJsonPrimitive()) {
                return uptime.getAsInt();
            } else {
                if (log != null && log.isDebugEnabled()) {
                    log.debug("Uptime is not a primitive: " + uptime);
                }
                return -1;
            }
        } catch (ClassCastException ex) {
            if (log != null && log.isDebugEnabled()) {
                log.warn("Uptime is not an integer: " + uptime);
            }
            return -1;
        }
    }

    private String buildCoresURL() {
        return "http://" + host + ":" + port + "/solr/admin/cores?indexInfo=false&wt=json";
    }

    public Log getLog() {
        return log;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getRetryWaitTimeMS() {
        return retryWaitTimeMS;
    }

    public void setRetryWaitTimeMS(int retryWaitTimeMS) {
        this.retryWaitTimeMS = retryWaitTimeMS;
    }
}
