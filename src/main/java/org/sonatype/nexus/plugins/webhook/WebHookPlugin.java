/*
 * Copyright 2011 Vincent Behar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sonatype.nexus.plugins.webhook;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.nexus.Nexus;

/**
 * Holds the plugin's configuration.
 * 
 * @author Vincent Behar
 */
@Component(role = WebHookPlugin.class)
public class WebHookPlugin {

    /** full path : sonatype-work/nexus/conf/webhooks.properties */
    public static final transient String CONFIG_FILENAME = "webhooks.properties";

    private final Properties configuration;

    @Requirement
    private Logger logger;

    @Requirement
    private Nexus nexus;

    public WebHookPlugin() {
        super();
        this.configuration = new Properties();
    }

    /**
     * Reload the plugin's configuration, based on the content of the configuration's file (see
     * {@link #getConfigurationFile()}).<br>
     * In case of error, we won't throw exceptions but log them.
     */
    public void reloadConfigurationQuietly() {
        try {
            reloadConfiguration();
            logger.info("Nexus WebHook Plugin successfully configured from " + getConfigurationFile().getAbsolutePath());
        } catch (IllegalArgumentException e) {
            logger.error("Failed to configure the Nexus WebHook Plugin from "
                         + getConfigurationFile().getAbsolutePath(), e);
        } catch (IOException e) {
            logger.error("Failed to configure the Nexus WebHook Plugin from "
                         + getConfigurationFile().getAbsolutePath(), e);
        }
    }

    /**
     * Reload the plugin's configuration, based on the content of the configuration's file (see
     * {@link #getConfigurationFile()}).
     * 
     * @throws IOException in case of error when reading the configuration's file content
     * @throws IllegalArgumentException if the configuration's file content contains a malformed Unicode escape
     *             sequence.
     */
    public void reloadConfiguration() throws IOException, IllegalArgumentException {
        configuration.clear();

        InputStream stream = null;
        try {
            stream = FileUtils.openInputStream(getConfigurationFile());
            configuration.load(stream);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    /**
     * Get the WebHook urls to notify for the given repository/artifact.
     * 
     * @param repository ID of the repository
     * @param groupId of the artifact
     * @param artifactId of the artifact
     * @return a {@link List} of urls (as String) - may be empty, won't be null
     */
    public Collection<String> getWebHooks(String repository, String groupId, String artifactId) {
        boolean inherited = Boolean.parseBoolean(configuration.getProperty("webhooks.inherited"));
        String[] defaultUrls = StringUtils.split(configuration.getProperty("webhooks.default", ""), ",");
        String[] repositoryUrls = StringUtils.split(configuration.getProperty(repository, ""), ",");
        String[] groupIdUrls = StringUtils.split(configuration.getProperty(repository + "." + groupId, ""), ",");
        String[] artifactIdUrls = StringUtils.split(configuration.getProperty(repository + "." + groupId + "."
                                                                              + artifactId, ""), ",");

        Collection<String> urls = new HashSet<String>();
        urls.addAll(Arrays.asList(artifactIdUrls));
        if (urls.isEmpty() || inherited) {
            urls.addAll(Arrays.asList(groupIdUrls));
        }
        if (urls.isEmpty() || inherited) {
            urls.addAll(Arrays.asList(repositoryUrls));
        }
        if (urls.isEmpty() || inherited) {
            urls.addAll(Arrays.asList(defaultUrls));
        }

        return urls;
    }

    /**
     * @return the plugin's configuration - won't be null
     */
    public Properties getConfiguration() {
        return configuration;
    }

    /**
     * @return the plugin's configuration file
     */
    public File getConfigurationFile() {
        return new File(nexus.getNexusConfiguration().getConfigurationDirectory(), CONFIG_FILENAME);
    }

}
