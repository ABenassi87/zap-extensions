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
package org.zaproxy.addon.soosspa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.parosproxy.paros.network.HttpResponseHeader;
import org.parosproxy.paros.network.HttpSender;
import org.zaproxy.addon.soosspa.processors.ISOOSZAPProcessor;
import org.zaproxy.addon.soosspa.processors.splash.SplashProcessor;
import org.zaproxy.addon.soosspa.utils.SOOSSPAUtils;
import org.zaproxy.zap.extension.selenium.SeleniumOptions;
import org.zaproxy.zap.network.HttpResponseBody;
import org.zaproxy.zap.network.HttpSenderListener;

public class SOOSSPAHttpSenderListener implements HttpSenderListener {
    private static final Logger LOGGER = LogManager.getLogger(SOOSSPAHttpSenderListener.class);
    private final ISOOSZAPProcessor processor;
    private final HashSet<Integer> validInitiators;
    private static final Map<String, String> pages = new HashMap<>();

    public SOOSSPAHttpSenderListener() {
        System.setProperty(
                SeleniumOptions.FIREFOX_DRIVER_SYSTEM_PROPERTY,
                "C:/Instaladores/Geckodriver/geckodriver.exe");
        this.processor = new SplashProcessor("http://localhost:8050");
        this.validInitiators = new HashSet<>();
        this.validInitiators.add(HttpSender.ACTIVE_SCANNER_INITIATOR);
        this.validInitiators.add(HttpSender.AJAX_SPIDER_INITIATOR);
        this.validInitiators.add(HttpSender.SPIDER_INITIATOR);
    }

    @Override
    public int getListenerOrder() {
        return 0;
    }

    @Override
    public void onHttpRequestSend(HttpMessage msg, int initiator, HttpSender sender) {
        // Nothing to do for now
    }

    @Override
    public void onHttpResponseReceive(HttpMessage msg, int initiator, HttpSender sender) {
        try {
            HttpRequestHeader requestHeader = msg.getRequestHeader();
            String httpMethod = requestHeader.getMethod();
            HttpResponseHeader responseHeader = msg.getResponseHeader();
            int statusCode = responseHeader.getStatusCode();
            if (httpMethod.equals("GET")
                    && this.validInitiators.contains(initiator)
                    && responseHeader.getContentLength() > 0
                    && statusCode >= 200
                    && statusCode < 300) {
                HttpResponseBody responseBody = msg.getResponseBody();
                String content = responseBody.toString();
                if (content != null
                        && !content.isEmpty()
                        && content.toLowerCase().contains("<html")
                        && SOOSSPAUtils.isModernWebApp(content)) {
                    String targetURL = requestHeader.getURI().getURI();
                    String newContent;
                    if (pages.containsKey(targetURL)) {
                        newContent = pages.get(targetURL);
                    } else {
                        newContent = this.processor.getHtmlSourceCode(targetURL);
                        pages.put(targetURL, newContent);
                    }

                    if (newContent != null) {
                        LOGGER.info("New Content" + newContent);
                        msg.setResponseBody(newContent);
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.error(exception);
        }
    }
}
