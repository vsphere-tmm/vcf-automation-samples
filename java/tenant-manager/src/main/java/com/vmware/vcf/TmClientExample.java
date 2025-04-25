/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;
import com.vmware.vcloud.rest.openapi.api.OrgApi;
import com.vmware.vcloud.rest.openapi.model.Orgs;

/**
 * Tenant Manager client example that sets up certs, logs the user in and queries the orgs.
 */
public class TmClientExample {
    private static VcdClientImpl client;
    private static OrgApi orgsApi = null;
    private static CxfClientSecurityContext securityContext;

    public static void main(String[] args) throws Exception {
        KeyStore truststore = getKeyStore();
        securityContext = CxfClientSecurityContext.getCxfClientSecurityContext(null, null, truststore, null, false);

        System.out.println("Using rest-api-client-1.0.0...");

        final OpenApiClient client = getClient().getOpenApiClient();
        orgsApi = client.createProxy(OrgApi.class);

        final Orgs orgs = getOrgs();
        System.out.println("Querying orgs result: " + orgs.getValues());
    }

    public static Orgs getOrgs() {
        final Orgs orgs = orgsApi.queryOrgs(1, 2, null, null, null);
        return orgs;
    }

    private static VcdClient getClient() throws GeneralSecurityException {
        if (client != null) {
            return client;
        }

        String serverUrl = SettingsLoader.getProviderConfig().get(Constants.SERVER_URL);
        String serverVersion = SettingsLoader.getProviderConfig().get(Constants.SERVER_VERSION);
        String username = SettingsLoader.getProviderConfig().get(Constants.AUTH_USERNAME);
        String tenant = SettingsLoader.getProviderConfig().get(Constants.AUTH_TENANT);
        String password = SettingsLoader.getProviderConfig().get(Constants.AUTH_PASSWORD);

        client = new VcdClientImpl(URI.create(serverUrl), serverVersion, securityContext);
        client.setCredentials(new VcdBasicLoginCredentials(username, tenant, password));

        return client;
    }

    /*
     * Only needed if your VCD instance is not using a well-signed certificate.
     */
    private static KeyStore getKeyStore() throws Exception {
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


}
