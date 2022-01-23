package org.zaproxy.addon.soosspa.processors.splash;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.zaproxy.addon.soosspa.processors.ISOOSZAPProcessor;

public class SplashProcessor implements ISOOSZAPProcessor {

    private static final String SPLASH_SERVER_URL_KEY = "SOOS_SPLASH_SERVER_URL";

    private CloseableHttpClient httpClient;
    private String splashServerURL;
    private RenderMode mode;

    public SplashProcessor() {
        this.splashServerURL = System.getenv(SPLASH_SERVER_URL_KEY);
        this.mode = RenderMode.HTML;
        this.initHttpClient();
    }

    public SplashProcessor(String splashServerURL) {
        this.splashServerURL = splashServerURL;
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
    public String getHtmlSourceCode(String url) {

        return null;
    }

    private String generateRequestUrl(String url) {
        StringBuilder builder = new StringBuilder();
        builder.append(splashServerURL);
        builder.append(mode.toString());

    }
}
