import org.zaproxy.gradle.addon.AddOnStatus

version = "18"
description = "Imports and spiders OpenAPI definitions."

zapAddOn {
    addOnName.set("OpenAPI Support")
    addOnStatus.set(AddOnStatus.BETA)
    zapVersion.set("2.9.0")

    manifest {
        author.set("ZAP Dev Team plus Joanna Bona, Nathalie Bouchahine, Artur Grzesica, Mohammad Kamar, Markus Kiss, Michal Materniak, Marcin Spiewak, and SDA SE Open Industry Solutions")
        url.set("https://www.zaproxy.org/docs/desktop/addons/openapi-support/")
        notBeforeVersion.set("2.10.0")
        extensions {
            register("org.zaproxy.zap.extension.openapi.automation.ExtensionOpenApiAutomation") {
                classnames {
                    allowed.set(listOf("org.zaproxy.zap.extension.openapi.automation"))
                }
                dependencies {
                    addOns {
                        register("automation") {
                            version.set("0.*")
                        }
                    }
                }
            }
        }
    }

    apiClientGen {
        api.set("org.zaproxy.zap.extension.openapi.OpenApiAPI")
        messages.set(file("src/main/resources/org/zaproxy/zap/extension/openapi/resources/Messages.properties"))
    }
}

configurations {
    "implementation" {
        // Not needed:
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "org.slf4j", module = "slf4j-ext")
    }
}

dependencies {
    compileOnly(parent!!.childProjects.get("automation")!!)
    implementation("io.swagger.parser.v3:swagger-parser:2.0.24")
    implementation("io.swagger:swagger-compat-spec-parser:1.0.54") {
        // Not needed:
        exclude(group = "com.github.java-json-tools", module = "json-schema-validator")
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("org.slf4j:slf4j-log4j12:1.7.30") {
        // Provided by ZAP.
        exclude(group = "log4j")
    }

    testImplementation(project(":testutils"))
}
