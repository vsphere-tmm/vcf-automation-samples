package com.vmware.vcfa.samples.automation.util;

import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class ConfigReader {

    Map<String, String> serverconfig;

    public  ConfigReader() {
        String fileName = "application.yaml";  // File is under src/main/resources
        InputStream inputStream = ConfigReader.class.getClassLoader().getResourceAsStream(fileName);
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            serverconfig = (Map<String, String>) data.get("server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getAccessToken() {
        return serverconfig.get("access_token");
    }

    public String getServerUrl() {
        return serverconfig.get("url");
    }

    public Boolean getVerifySsl() {
        Object value = serverconfig.get("verify_ssl");

        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        } else {
            return false; // Default value if key is missing or invalid
        }
    }

}
