/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2022 The ZAP Development Team
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
package org.zaproxy.addon.soosspa.processors.splash;

import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zaproxy.addon.soosspa.processors.ISOOSZAPProcessor;

public class SplashProcessor implements ISOOSZAPProcessor {
    private static final Logger LOGGER = LogManager.getLogger(SplashProcessor.class);

    private static final String SPLASH_SERVER_URL_KEY = "SOOS_SPLASH_SERVER_URL";

    private CloseableHttpClient httpClient;
    private String splashServerURL;
    private final RenderMode mode;

    public SplashProcessor() {
        this.splashServerURL = System.getenv(SPLASH_SERVER_URL_KEY);
        this.mode = RenderMode.HTML;
        this.initHttpClient();
    }

    public SplashProcessor(String splashServerURL) {
        this.splashServerURL = splashServerURL;
        this.mode = RenderMode.HTML;
        this.initHttpClient();
    }

    private void initHttpClient() {
        this.httpClient = HttpClients.createDefault();
    }

    public String getSplashServerURL() {
        return splashServerURL;
    }

    public void setSplashServerURL(String splashServerURL) {
        this.splashServerURL = splashServerURL;
    }

    @Override
    public String getHtmlSourceCode(String targetURL) {
        String url = this.generateRequestUrl(targetURL);
        LOGGER.info("URL: " + url);
        HttpGet request = new HttpGet(url);
        String htmlContent = null;
        try (CloseableHttpResponse response = this.httpClient.execute(request)) {
            HttpEntity responseEntity = response.getEntity();
            htmlContent = EntityUtils.toString(responseEntity);
            LOGGER.info("HTML Content: " + htmlContent);
            EntityUtils.consume(responseEntity);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return htmlContent;
    }

    private String generateRequestUrl(String url) {
        StringBuilder builder = new StringBuilder();
        builder.append(splashServerURL);
        builder.append(mode.toString());
        builder.append("?url=");
        builder.append(url);

        return builder.toString();
    }
}
