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
package org.zaproxy.addon.soosspa.processors.selenium;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.parosproxy.paros.core.proxy.OverrideMessageProxyListener;
import org.parosproxy.paros.core.proxy.ProxyServer;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.soosspa.processors.ISOOSZAPProcessor;
import org.zaproxy.addon.soosspa.utils.WebDriverWrapper;
import org.zaproxy.zap.extension.selenium.Browser;
import org.zaproxy.zap.extension.selenium.ExtensionSelenium;

public class SeleniumProcessor implements ISOOSZAPProcessor {
    private static final Logger LOGGER = LogManager.getLogger(SeleniumProcessor.class);

    private static final Map<Browser, Stack<WebDriverWrapper>> freeDrivers = new HashMap<>();
    private static final List<WebDriverWrapper> takenDrivers = new ArrayList<>();

    private static Thread reaperThread = null;
    private static final Object reaperThreadSync = new Object();

    private static ProxyServer proxy = null;
    private static int proxyPort = -1;
    private static final String proxyAddress = "127.0.0.1";

    private final Browser browser;

    public SeleniumProcessor(Browser browser) {
        this.browser = browser;
        getProxy();
        WebDriverWrapper driver = new WebDriverWrapper(createWebDriverCustom(), this.browser);
        freeDrivers.computeIfAbsent(driver.getBrowser(), k -> new Stack<>()).push(driver);
    }
    /*
     * We use a separate port so that we dont polute the sites tree
     * and show the requests in the Active Scan tab
     */
    private void getProxy() {
        if (proxy == null) {
            proxy = new ProxyServer();
            proxy.setConnectionParam(Model.getSingleton().getOptionsParam().getConnectionParam());
            proxy.addOverrideMessageProxyListener(
                    new OverrideMessageProxyListener() {

                        @Override
                        public int getArrangeableListenerOrder() {
                            return 0;
                        }

                        @Override
                        public boolean onHttpRequestSend(HttpMessage msg) {
                            return true;
                        }

                        @Override
                        public boolean onHttpResponseReceived(HttpMessage arg0) {
                            // Shouldn't be called, since the messages are being overridden
                            return true;
                        }
                    });
            proxyPort = proxy.startServer(proxyAddress, 0, true);
        }
    }

    private WebDriver createWebDriver() {
        WebDriver webDriver =
                ExtensionSelenium.getWebDriver(
                        browser,
                        "127.0.0.1",
                        proxyPort,
                        capabilities ->
                                capabilities.setCapability(
                                        CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR,
                                        UnexpectedAlertBehaviour.IGNORE));

        webDriver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);
        webDriver.manage().timeouts().setScriptTimeout(10, TimeUnit.SECONDS);

        return webDriver;
    }

    private WebDriver createWebDriverCustom() {
        FirefoxOptions firefoxOptions = new FirefoxOptions();
        firefoxOptions.setHeadless(true);

        return new FirefoxDriver(firefoxOptions);
    }

    private WebDriverWrapper getWebDriver() {
        WebDriverWrapper driver = null;
        try {
            driver = freeDrivers.get(browser).pop();
            if (!driver.getBrowser().equals(browser)) {
                driver.getDriver().quit();
                driver = null;
            }
        } catch (Exception e) {
            // Ignore
        }
        if (driver == null) {
            driver = new WebDriverWrapper(createWebDriverCustom(), browser);
        }
        synchronized (takenDrivers) {
            takenDrivers.add(driver);
        }

        if (reaperThread == null) {
            synchronized (reaperThreadSync) {
                if (reaperThread == null) {
                    reaperThread =
                            new Thread(
                                    () -> {
                                        LOGGER.info("Reaper thread starting");
                                        reaperThread.setName("ZAP-SOOSSPAReaper");
                                        do {
                                            try {
                                                Thread.sleep(5000);
                                            } catch (InterruptedException e) {
                                                // Ignore
                                            }
                                            Date now = new Date();
                                            // concurrent modification exception :(
                                            synchronized (takenDrivers) {
                                                Iterator<WebDriverWrapper> iter =
                                                        takenDrivers.iterator();
                                                while (iter.hasNext()) {
                                                    WebDriverWrapper wrapper = iter.next();
                                                    if ((now.getTime()
                                                                            - wrapper.getLastAccessed()
                                                                                    .getTime())
                                                                    / 1000
                                                            > 10) {
                                                        LOGGER.debug(
                                                                "Driver hung {}",
                                                                wrapper.getDriver().hashCode());
                                                        wrapper.getDriver().quit();
                                                        wrapper.setDriver(createWebDriverCustom());
                                                        LOGGER.debug(
                                                                "New driver {}",
                                                                wrapper.getDriver().hashCode());
                                                    }
                                                }
                                            }
                                        } while (takenDrivers.size() > 0);
                                        LOGGER.info(
                                                "Reaper thread exiting {}", takenDrivers.size());
                                        reaperThread = null;
                                    });
                    reaperThread.start();
                }
            }
        }
        return driver;
    }

    private void returnDriver(WebDriverWrapper driver) {
        synchronized (takenDrivers) {
            try {
                driver.getDriver().switchTo().alert().accept();
            } catch (Exception e) {
                // ignore
            }
            driver.getDriver().get("about:blank");
            if (takenDrivers.remove(driver)) {
                freeDrivers.computeIfAbsent(driver.getBrowser(), k -> new Stack<>()).push(driver);

            } else {
                LOGGER.debug("Driver not in 'taken' list");
            }
        }
    }

    @Override
    public String getHtmlSourceCode(String url) {
        WebDriverWrapper driverWrapper = getWebDriver();
        driverWrapper.getDriver().get(url);
        String htmlContent = driverWrapper.getDriver().getPageSource();
        returnDriver(driverWrapper);
        return htmlContent;
    }
}
