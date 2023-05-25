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
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    public void waitForAllCoresReady() throws InterruptedException {
        if(log != null){
            log.info("Waiting for all cores to be ready...");
        }
        int tries;
        for (tries = 0; tries < retries; tries++) {
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                try (CloseableHttpResponse response = client
                    .execute(new HttpGet(buildCoresURL()))) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        try (InputStream is = entity.getContent();
                            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            JsonStreamParser parser = new JsonStreamParser(isr);
                            JsonElement root = parser.next();

                            JsonObject rootObject = root.getAsJsonObject();
                            if (allCoresReady(rootObject)) {
                                if(log != null){
                                    log.info("All cores are ready.");
                                }
                                return;
                            } else {
                                if (log != null) {
                                    log.info("SOLR not ready yet, waiting...");
                                }
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                if (log != null) {
                    log.info("SOLR not ready yet, waiting...");
                }
            } finally {
                if(retryWaitTimeMS > 0){
                    Thread.sleep(retryWaitTimeMS);
                }
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
            return false;
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
