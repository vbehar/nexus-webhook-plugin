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

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.awaitility.Awaitility.to;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.index.artifact.Gav;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.nexus.plugins.webhook.ArtifactStoredEvent.Repository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Test for the {@link WebHookNotifier}.<br>
 * Starts an embedded HTTP server on port {@link #HTTP_PORT}, and make sure that the JSON received is valid.
 * 
 * @author Vincent Behar
 */
public class WebHookNotifierTest {

    private static final transient int HTTP_PORT = 61234;

    private WebHookPlugin plugin;

    private WebHookNotifier notifier;

    private HttpServer httpServer;

    /** Map of url / json-content for each notification */
    private ConcurrentHashMap<String, String> notifications;

    @Test
    public void notifySingle() throws Exception {
        ArtifactStoredEvent event = new ArtifactStoredEvent();
        event.setRepository(new Repository("snapshots", "Snapshots"));
        event.setArtifact(new Gav("com.example", "app", "1.0.0-SNAPSHOT"));
        event.setTimestamp(new Date().getTime());
        event.setUser("robert");

        notifier.notify(event);

        await().atMost(5, SECONDS).pollInterval(10, MILLISECONDS).untilCall(to(notifications).size(), equalTo(1));

        Assert.assertEquals(1, notifications.size());
        Assert.assertTrue(notifications.containsKey("/"));
        Assert.assertTrue(notifications.containsValue(event.toJson()));
    }

    @Test
    public void notifyMultipleWithInheritance() throws Exception {
        ArtifactStoredEvent event = new ArtifactStoredEvent();
        event.setRepository(new Repository("releases", "Releases"));
        event.setArtifact(new Gav("com.example", "app", "1.0.0"));
        event.setTimestamp(new Date().getTime());
        event.setUser("robert");

        notifier.notify(event);

        await().atMost(5, SECONDS).pollInterval(10, MILLISECONDS).untilCall(to(notifications).size(), equalTo(5));

        Assert.assertEquals(5, notifications.size());
        Assert.assertTrue(notifications.containsKey("/"));
        Assert.assertTrue(notifications.containsKey("/releases/"));
        Assert.assertTrue(notifications.containsKey("/releases/com.example/"));
        Assert.assertTrue(notifications.containsKey("/releases/com.example/app/one/"));
        Assert.assertTrue(notifications.containsKey("/releases/com.example/app/two/"));
        for (String json : notifications.values()) {
            Assert.assertEquals(event.toJson(), json);
        }
    }

    @Test
    public void notifyMultipleWithoutInheritance() throws Exception {
        for (Field field : plugin.getClass().getDeclaredFields()) {
            if ("configuration".equals(field.getName())) {
                field.setAccessible(true);
                Properties configuration = (Properties) field.get(plugin);
                configuration.setProperty("webhooks.inherited", "false");
                field.set(plugin, configuration);
            }
        }

        ArtifactStoredEvent event = new ArtifactStoredEvent();
        event.setRepository(new Repository("releases", "Releases"));
        event.setArtifact(new Gav("com.example", "app", "1.0.0"));
        event.setTimestamp(new Date().getTime());
        event.setUser("robert");

        notifier.notify(event);

        await().atMost(5, SECONDS).pollInterval(10, MILLISECONDS).untilCall(to(notifications).size(), equalTo(2));

        Assert.assertEquals(2, notifications.size());
        Assert.assertTrue(notifications.containsKey("/releases/com.example/app/one/"));
        Assert.assertTrue(notifications.containsKey("/releases/com.example/app/two/"));
        for (String json : notifications.values()) {
            Assert.assertEquals(event.toJson(), json);
        }
    }

    @Before
    public void setUp() throws Exception {
        notifications = new ConcurrentHashMap<String, String>();

        notifier = new WebHookNotifier();

        plugin = new WebHookPlugin();
        Properties configuration = new Properties();
        configuration.setProperty("releases.com.example.app", "http://localhost:" + HTTP_PORT
                                                              + "/releases/com.example/app/one/," + "http://localhost:"
                                                              + HTTP_PORT + "/releases/com.example/app/two/");
        configuration.setProperty("releases.com.example", "http://localhost:" + HTTP_PORT + "/releases/com.example/");
        configuration.setProperty("releases", "http://localhost:" + HTTP_PORT + "/releases/");
        configuration.setProperty("webhooks.default", "http://localhost:" + HTTP_PORT + "/");
        configuration.setProperty("webhooks.inherited", "true");
        for (Field field : plugin.getClass().getDeclaredFields()) {
            if ("configuration".equals(field.getName())) {
                field.setAccessible(true);
                field.set(plugin, configuration);
            }
        }

        for (Field field : notifier.getClass().getDeclaredFields()) {
            if ("logger".equals(field.getName())) {
                field.setAccessible(true);
                field.set(notifier, new ConsoleLogger(Logger.LEVEL_DEBUG, "console"));
            } else if ("webHookPlugin".equals(field.getName())) {
                field.setAccessible(true);
                field.set(notifier, plugin);
            }
        }

        httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 50);
        httpServer.createContext("/", new HttpHandler() {

            public void handle(HttpExchange httpExchange) throws IOException {
                String json = IOUtils.toString(httpExchange.getRequestBody());
                notifications.put(httpExchange.getRequestURI().toString(), json);
                IOUtils.closeQuietly(httpExchange.getRequestBody());

                if (StringUtils.contains(json, "error")) {
                    httpExchange.sendResponseHeaders(500, 4);
                    IOUtils.write("Oops", httpExchange.getResponseBody());
                } else {
                    httpExchange.sendResponseHeaders(200, 6);
                    IOUtils.write("Thanks", httpExchange.getResponseBody());
                }
                IOUtils.closeQuietly(httpExchange.getResponseBody());
                httpExchange.close();
            }
        });
        httpServer.setExecutor(null);
        httpServer.start();
    }

    @After
    public void tearDown() throws Exception {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

}
