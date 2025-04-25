/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import org.yaml.snakeyaml.Yaml;
import com.vmware.vcfa.util.ConfigReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class SettingsLoader {
    public static final String TM = "provider";
    public static Map<String, String> providerConfig;

    private SettingsLoader() {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream(Constants.SETTINGS_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + Constants.SETTINGS_FILE);
            }
            final Yaml yaml = new Yaml();
            final Map<String, Object> data = yaml.load(input);
            Map<String, Object> vcfa = (Map<String, Object>) data.get("vcfa");
            Map<String, Object> auth = (Map<String, Object>) vcfa.get("auth");

            providerConfig = (Map<String, String>) auth.get(TM);
            providerConfig.put(Constants.SERVER_URL, String.format("%s/api", (String) vcfa.get(Constants.SERVER_URL)));

            Map<String, Object> ssl = (Map<String, Object>) vcfa.get("ssl");
            Map<String, Object> trustStore = (Map<String, Object>) ssl.get(Constants.TRUSTSTORE);

            providerConfig.put(Constants.TRUSTSTORE_ALIAS, (String) trustStore.get(Constants.TRUSTSTORE_ALIAS));
            providerConfig.put(Constants.TRUSTSTORE_TYPE, (String) trustStore.get(Constants.TRUSTSTORE_TYPE));

        } catch (IOException e) {
            throw new RuntimeException("Failed to load settings file", e);
        }
    }

    public static Map<String, String> getProviderConfig() {
        if (providerConfig == null) {
            new SettingsLoader();
        }
        return providerConfig;
    }
}

