/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf;

import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;

public class VcfUtils {

	private static CxfClientSecurityContext securityContext;
	private static VcdClient vcdClient;
	
	/*
     * Only needed if your VCD instance is not using a well-signed certificate.
     */
    public static KeyStore getKeyStore() throws Exception {
        String alias = SettingsLoader.getProviderConfig().get(Constants.TRUSTSTORE_ALIAS);
        String truststoreType = SettingsLoader.getProviderConfig().get(Constants.TRUSTSTORE_TYPE);

        // Initialize KeyStore
        KeyStore keyStore = KeyStore.getInstance(truststoreType);
        keyStore.load(null, null);

        // Check if the certificate is already present
        if (keyStore.containsAlias(alias)) {
            System.out.println("Certificate already exists in the KeyStore with alias: " + alias);
            return keyStore;
        }

        // Fetch and add the certificate if not already present
        X509Certificate[] cert = CertificateUtil.getVcfCert(URI.create(SettingsLoader.getProviderConfig().get(Constants.SERVER_URL)));
        if (cert != null && cert.length > 0) {
            keyStore.setCertificateEntry(alias, cert[cert.length - 1]);
            System.out.println("Added new certificate to KeyStore with alias: " + alias);
        } else {
            System.out.println("No certificate found to add to KeyStore.");
        }

        return keyStore;
    }
    
    public static VcdClient getClient() throws Exception {
    	final KeyStore truststore = VcfUtils.getKeyStore();
        securityContext = CxfClientSecurityContext.getCxfClientSecurityContext(null, null, truststore, null, false);
        if (vcdClient != null) {
            return vcdClient;
        }

        String serverUrl = SettingsLoader.getProviderConfig().get(Constants.SERVER_URL);
        String serverVersion = SettingsLoader.getProviderConfig().get(Constants.SERVER_VERSION);
        String username = SettingsLoader.getProviderConfig().get(Constants.AUTH_USERNAME);
        String tenant = SettingsLoader.getProviderConfig().get(Constants.AUTH_TENANT);
        String password = SettingsLoader.getProviderConfig().get(Constants.AUTH_PASSWORD);

        vcdClient = new VcdClientImpl(URI.create(serverUrl), serverVersion, securityContext);
        vcdClient.setCredentials(new VcdBasicLoginCredentials(username, tenant, password));

        return vcdClient;
    }
}
