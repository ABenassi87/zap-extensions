package org.zaproxy.addon.soosspa.processors.splash;

public enum RenderMode {
    HTML("/render.html"),
    JSON("/render.json");

    private String urlPath;

    RenderMode(String urlPath) {
        this.urlPath = urlPath;
    }

    @Override
    public String toString() {
        return urlPath;
    }
}
