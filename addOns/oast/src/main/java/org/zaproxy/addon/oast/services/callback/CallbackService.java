/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
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
package org.zaproxy.addon.oast.services.callback;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.proxy.ProxyServer;
import org.parosproxy.paros.extension.OptionsChangedListener;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.OptionsParam;
import org.zaproxy.addon.oast.OastService;

public class CallbackService extends OastService implements OptionsChangedListener {

    private final ProxyServer proxyServer;
    private org.zaproxy.addon.oast.services.callback.CallbackParam callbackParam;

    private final Map<String, String> handlers = new HashMap<>();
    private int actualPort;
    private String currentConfigLocalAddress;
    private int currentConfigPort;

    private static final Logger LOGGER = LogManager.getLogger(CallbackService.class);

    public CallbackService(OastRequestFactory oastRequestFactory) {
        Objects.requireNonNull(oastRequestFactory);

        proxyServer = new ProxyServer("ZAP-CallbackService");
        proxyServer.addOverrideMessageProxyListener(
                new CallbackProxyListener(this, oastRequestFactory));
    }

    @Override
    public String getName() {
        return "Callback";
    }

    @Override
    public void startService() {
        this.restartServer(this.getParam().getPort());
    }

    @Override
    public void stopService() {
        this.proxyServer.stopServer();
    }

    public void optionsLoaded() {
        proxyServer.setConnectionParam(Model.getSingleton().getOptionsParam().getConnectionParam());
        currentConfigLocalAddress = this.getParam().getLocalAddress();
        currentConfigPort = this.getParam().getPort();
    }

    @Override
    public void optionsChanged(OptionsParam optionsParam) {
        if (!currentConfigLocalAddress.equals(this.getParam().getLocalAddress())
                || currentConfigPort != this.getParam().getPort()) {
            // Something's changed, reuse the port if it's still a random one
            int port = actualPort;
            if (currentConfigPort != this.getParam().getPort()) {
                port = this.getParam().getPort();
            }
            this.restartServer(port);

            // Save the new ones for next time
            currentConfigLocalAddress = this.getParam().getLocalAddress();
            currentConfigPort = this.getParam().getPort();
        }
    }

    private void restartServer(int port) {
        // this will close the previous listener (if there was one)
        actualPort = proxyServer.startServer(this.getParam().getLocalAddress(), port, true);
        LOGGER.info(
                "Started callback service on {}:{}", this.getParam().getLocalAddress(), actualPort);
    }

    public String getCallbackAddress() {
        return getAddress(
                this.getParam().getRemoteAddress(), actualPort, this.getParam().isSecure());
    }

    @Override
    public boolean isRegistered() {
        return true;
    }

    @Override
    public String getNewPayload() {
        return getNewPayload(null);
    }

    public String getNewPayload(String handler) {
        if (handler == null || handler.isEmpty()) {
            handler = Constant.messages.getString("oast.callback.handler.none.name");
        }
        String uuid = UUID.randomUUID().toString();
        handlers.put(uuid, handler);
        return getCallbackAddress() + uuid;
    }

    public String getAddress(String address, int port, boolean isSecure) {
        boolean ipv6 = address.contains(":");
        String hostname = ipv6 ? "[" + address + "]" : address;
        String scheme = isSecure ? "https" : "http";
        return scheme + "://" + hostname + ":" + port + "/";
    }

    protected int getPort() {
        return actualPort;
    }

    public CallbackParam getParam() {
        if (this.callbackParam == null) {
            this.callbackParam = new CallbackParam();
        }
        return this.callbackParam;
    }

    Map<String, String> getHandlers() {
        return handlers;
    }
}
