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
    public static final String TM = "tm";
    public static Map<String, String> serverConfig;

    private SettingsLoader() {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream(Constants.SETTINGS_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + Constants.SETTINGS_FILE);
            }
            final Yaml yaml = new Yaml();
            final Map<String, Object> data = yaml.load(input);
            serverConfig = (Map<String, String>) data.get(TM);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load settings file", e);
        }
    }

    public static Map<String, String> getServerConfig() {
        if (serverConfig == null) {
            new SettingsLoader();
        }
        return serverConfig;
    }
}

