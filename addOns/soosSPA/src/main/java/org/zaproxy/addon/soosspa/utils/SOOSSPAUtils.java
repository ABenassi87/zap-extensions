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
package org.zaproxy.addon.soosspa.utils;

import java.util.List;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;

public class SOOSSPAUtils {

    public static boolean isModernWebApp(String htmlContent) {
        boolean isModernWebApp = false;
        Source source = new Source(htmlContent);

        List<Element> noScripts = source.getAllElements(HTMLElementName.NOSCRIPT);

        if (!noScripts.isEmpty()) {
            for (Element noScript : noScripts) {
                isModernWebApp =
                        noScript.getContent().toString().toLowerCase().contains("javascript");
            }
        }

        if (isModernWebApp) {
            return true;
        }

        List<Element> anchors = source.getAllElements(HTMLElementName.A);

        if (!anchors.isEmpty()) {
            for (Element link : anchors) {
                String href = link.getAttributeValue("href");
                if (href == null || href.length() == 0 || href.equals("#")) {
                    isModernWebApp = true;
                    break;
                }
                String target = link.getAttributeValue("target");
                if (target != null && target.equals("_self")) {
                    isModernWebApp = true;
                    break;
                }
            }
        }

        if (isModernWebApp) {
            return true;
        }

        List<Element> links = source.getAllElements(HTMLElementName.LINK);

        if (!links.isEmpty()) {
            for (Element link : links) {
                String href = link.getAttributeValue("href");
                if (href != null && href.contains("manifest.json")) {
                    isModernWebApp = true;
                    break;
                }
            }
        }

        if (isModernWebApp) {
            return true;
        }

        isModernWebApp = htmlContent.contains(".chunk.");

        return isModernWebApp;
    }
}
