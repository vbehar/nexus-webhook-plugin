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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the {@link WebHookPlugin}
 * 
 * @author Vincent Behar
 */
public class WebHookPluginTest {

    private WebHookPlugin plugin;

    @Test
    public void getWebHooksEmpty() throws Exception {
        plugin = new WebHookPlugin();
        Collection<String> urls = plugin.getWebHooks("snapshots", "com.example", "app");

        Assert.assertTrue(urls.isEmpty());
    }

    @Test
    public void getWebHooksDefault() throws Exception {
        Collection<String> urls = plugin.getWebHooks("snapshots", "com.example", "app");

        Assert.assertEquals(1, urls.size());
        Assert.assertTrue(urls.contains("http://localhost/"));
    }

    @Test
    public void getWebHooksWithInheritance() throws Exception {
        Collection<String> urls = plugin.getWebHooks("releases", "com.example", "app");

        Assert.assertEquals(5, urls.size());
        Assert.assertTrue(urls.contains("http://localhost/"));
        Assert.assertTrue(urls.contains("http://localhost/releases/"));
        Assert.assertTrue(urls.contains("http://localhost/releases/com.example/"));
        Assert.assertTrue(urls.contains("http://localhost/releases/com.example/app/one/"));
        Assert.assertTrue(urls.contains("http://localhost/releases/com.example/app/two/"));
    }

    @Test
    public void getWebHooksWithoutInheritance() throws Exception {
        setUp(false);

        Collection<String> urls = plugin.getWebHooks("releases", "com.example", "app");

        Assert.assertEquals(2, urls.size());
        Assert.assertTrue(urls.contains("http://localhost/releases/com.example/app/one/"));
        Assert.assertTrue(urls.contains("http://localhost/releases/com.example/app/two/"));
    }

    @Before
    public void setUp() throws Exception {
        setUp(true);
    }

    private void setUp(boolean withInheritance) throws Exception {
        plugin = new WebHookPlugin();

        Properties configuration = new Properties();
        configuration.setProperty("releases.com.example.app", "http://localhost/releases/com.example/app/one/,"
                                                              + "http://localhost/releases/com.example/app/two/");
        configuration.setProperty("releases.com.example", "http://localhost/releases/com.example/");
        configuration.setProperty("releases", "http://localhost/releases/");
        configuration.setProperty("webhooks.default", "http://localhost/");
        configuration.setProperty("webhooks.inherited", Boolean.toString(withInheritance));

        for (Field field : plugin.getClass().getDeclaredFields()) {
            if ("configuration".equals(field.getName())) {
                field.setAccessible(true);
                field.set(plugin, configuration);
            }
        }
    }

}
