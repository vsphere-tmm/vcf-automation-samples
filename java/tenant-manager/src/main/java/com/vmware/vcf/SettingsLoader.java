/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SettingsLoader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = SettingsLoader.class.getClassLoader().getResourceAsStream(Constants.SETTINGS_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + Constants.SETTINGS_FILE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load settings file", e);
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
}

