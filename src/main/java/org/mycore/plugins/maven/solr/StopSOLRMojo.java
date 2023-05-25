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

import org.mycore.plugins.maven.solr.tools.SOLRRunner;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "stop")
public class StopSOLRMojo extends AbstractSolrMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        setUpSolr();
        try {
            SOLRRunner runner = buildRunner();
            runner.setPort(this.solrPort);

            Process process = runner.stop();
            int result = runner.waitAndOutput(process);
            if (result != 0) {
                throw new MojoExecutionException("Solr command did not return 0. See Log for errors.");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoFailureException("Error while stopping SOLR!", e);
        }
    }

}
