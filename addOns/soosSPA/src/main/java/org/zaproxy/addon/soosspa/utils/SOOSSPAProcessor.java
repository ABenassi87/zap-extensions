package org.zaproxy.addon.soosspa.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.parosproxy.paros.core.proxy.OverrideMessageProxyListener;
import org.parosproxy.paros.core.proxy.ProxyServer;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.soosspa.ExtensionSOOSSPA;
import org.zaproxy.zap.extension.selenium.Browser;
import org.zaproxy.zap.extension.selenium.ExtensionSelenium;


import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class SOOSSPAProcessor {
    private static final Logger LOGGER = LogManager.getLogger(ExtensionSOOSSPA.class);

    private static final Map<Browser, Stack<WebDriverWrapper>> freeDrivers = new HashMap<>();
    private static final List<WebDriverWrapper> takenDrivers = new ArrayList<>();

    private static Thread reaperThread = null;
    private static final Object reaperThreadSync = new Object();

    private static ProxyServer proxy = null;
    private static int proxyPort = -1;

    private final Browser browser;

    public SOOSSPAProcessor(Browser browser) {
        this.browser = browser;
        getProxy();
        WebDriverWrapper driver = new WebDriverWrapper(createWebDriver(), this.browser);
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
            proxyPort = proxy.startServer("127.0.0.1", 0, true);
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
            driver = new WebDriverWrapper(createWebDriver(), browser);
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
                                                        wrapper.setDriver(createWebDriver());
                                                        LOGGER.debug(
                                                                "New driver {}",
                                                                wrapper.getDriver().hashCode());
                                                    }
                                                }
                                            }
                                        } while (takenDrivers.size() > 0);
                                        LOGGER.info("Reaper thread exiting {}", takenDrivers.size());
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

    public String getHtmlSourceCode(String url) {
        WebDriverWrapper driverWrapper = getWebDriver();
        driverWrapper.getDriver().get(url);
        String htmlContent = driverWrapper.getDriver().getPageSource();
        returnDriver(driverWrapper);
        return htmlContent;
    }
}
