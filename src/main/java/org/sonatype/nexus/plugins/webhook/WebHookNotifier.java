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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.json.JSONException;
import org.sonatype.nexus.configuration.application.GlobalHttpProxySettings;
import org.sonatype.nexus.proxy.repository.UsernamePasswordRemoteAuthenticationSettings;

/**
 * Class responsible for doing the actual work of making the HTTP requests to the registered webhook listeners.
 * 
 * @author Vincent Behar
 */
@Component(role = WebHookNotifier.class)
public class WebHookNotifier {

    @Requirement
    private WebHookPlugin webHookPlugin;

    @Requirement(role = GlobalHttpProxySettings.class, optional = true)
    private GlobalHttpProxySettings proxySettings;

    @Requirement
    private Logger logger;

    private final ExecutorService executorService;

    public WebHookNotifier() {
        super();
        executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Notify the registered webhook listeners that the given event has occurred.<br>
     * The notifications are sent asynchronously.
     * 
     * @param event
     */
    public void notify(ArtifactStoredEvent event) {
        Collection<String> urls = webHookPlugin.getWebHooks(event.getRepository().getId(),
                                                            event.getArtifact().getGroupId(),
                                                            event.getArtifact().getArtifactId());

        String jsonTmp = null;
        try {
            jsonTmp = event.toJson();
        } catch (JSONException e) {
            logger.error("Failed to prepare JSON for event " + event, e);
            return;
        }
        final String json = jsonTmp;

        if (logger.isDebugEnabled()) {
            logger.debug("Sending WebHook JSON notification (" + json + ") to " + urls);
        }

        for (final String url : urls) {
            executorService.execute(new Runnable() {

                public void run() {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Sending WebHook HTTP POST request to " + url);
                    }

                    HttpClient httpClient = instantiateHttpClient();

                    try {
                        HttpPost post = new HttpPost(url);
                        try {
                            post.setEntity(new StringEntity(json));
                        } catch (UnsupportedEncodingException e) {
                            logger.error("Failed to prepare POST request to " + url, e);
                            return;
                        }

                        HttpResponse response = null;
                        try {
                            response = httpClient.execute(post);
                        } catch (IOException e) {
                            logger.error("Failed to POST request to " + url, e);
                            return;
                        }

                        if (response.getStatusLine().getStatusCode() >= 400
                            && response.getStatusLine().getStatusCode() < 600) {
                            // either a 4xx or 5xx response from the server, not good
                            logger.warn("Got a bad HTTP response '" + response.getStatusLine() + "' for " + url);
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Response from " + url + " is : " + response.getStatusLine());
                            }
                        }

                        try {
                            EntityUtils.consume(response.getEntity());
                        } catch (IOException e) {
                            logger.warn("Failed to consume entity (release connection)", e);
                        }
                    } finally {
                        httpClient.getConnectionManager().shutdown();
                    }
                }
            });
        }
    }

    /**
     * Instantiate a new {@link HttpClient} instance, configured to accept all SSL certificates, and use proxy settings
     * from Nexus.
     * 
     * @return an {@link HttpClient} instance - won't be null
     */
    private HttpClient instantiateHttpClient() {
        DefaultHttpClient httpClient = new DefaultHttpClient();

        // configure user-agent
        HttpProtocolParams.setUserAgent(httpClient.getParams(), "Nexus WebHook Plugin");

        // configure SSL
        SSLSocketFactory socketFactory = null;
        try {
            socketFactory = new SSLSocketFactory(new TrustStrategy() {

                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        httpClient.getConnectionManager().getSchemeRegistry().register(new Scheme("https", 443, socketFactory));

        // configure proxy
        if (proxySettings != null && proxySettings.isEnabled()) {
            HttpHost proxy = new HttpHost(proxySettings.getHostname(), proxySettings.getPort());
            if (UsernamePasswordRemoteAuthenticationSettings.class.isInstance(proxySettings.getProxyAuthentication())) {
                UsernamePasswordRemoteAuthenticationSettings proxyAuthentication = (UsernamePasswordRemoteAuthenticationSettings) proxySettings.getProxyAuthentication();
                httpClient.getCredentialsProvider()
                          .setCredentials(new AuthScope(proxySettings.getHostname(), proxySettings.getPort()),
                                          new UsernamePasswordCredentials(proxyAuthentication.getUsername(),
                                                                          proxyAuthentication.getPassword()));
            }
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        return httpClient;
    }
}
