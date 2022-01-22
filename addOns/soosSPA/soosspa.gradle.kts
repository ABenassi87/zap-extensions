description = "A simple extension example."

zapAddOn {
    addOnName.set("SOOS SPA")
    zapVersion.set("2.11.1")

    manifest {
        author.set("Alfredo L. Benassi <abenassi@soos.io")
        url.set("https://soos.io/")
        extensions {
            register("org.zaproxy.zap.addon.soosspa.ExtensionSOOSSPA")
        }
        dependencies {
            addOns {
                register("selenium") {
                    version.set("15.*")
                }
                register("commonlib") {
                    version.set(">= 1.6.0 & < 2.0.0")
                }
            }
        }
    }
}

dependencies {
    compileOnly(parent!!.childProjects.get("commonlib")!!)
    compileOnly(parent!!.childProjects.get("selenium")!!)
    testImplementation(parent!!.childProjects.get("commonlib")!!)
    testImplementation(parent!!.childProjects.get("selenium")!!)
    testImplementation("io.github.bonigarcia:webdrivermanager:5.0.3")
    testImplementation(project(":testutils"))
}

tasks.withType<Test>().configureEach {
    systemProperties.putAll(mapOf(
            "wdm.chromeDriverVersion" to "83.0.4103.39",
            "wdm.geckoDriverVersion" to "0.29.0",
            "wdm.forceCache" to "true"))
}

crowdin {
    configuration {
        val resourcesPath = "org/zaproxy/addon/${zapAddOn.addOnId.get()}/resources/"
        tokens.put("%messagesPath%", resourcesPath)
        tokens.put("%helpPath%", resourcesPath)
    }
}
