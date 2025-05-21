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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.TaskStatus;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.rest.openapi.api.NsxManagersApi;
import com.vmware.vcloud.rest.openapi.api.SupervisorClustersApi;
import com.vmware.vcloud.rest.openapi.api.VirtualCenterApi;
import com.vmware.vcloud.rest.openapi.model.NsxManager;
import com.vmware.vcloud.rest.openapi.model.Supervisor;
import com.vmware.vcloud.rest.openapi.model.VCenterServer;

public class VcfUtils {

	private static CxfClientSecurityContext securityContext;
	private static VcdClient vcdClient;
	public static final long LARGE_TASK_TIMEOUT = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);
	public static final int POLL_INTERVAL = 2000;
	private static int PAGE = 1;
	private static int PAGE_SIZE = 5;

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
		X509Certificate[] cert = CertificateUtil
				.getVcfCert(URI.create(SettingsLoader.getProviderConfig().get(Constants.SERVER_URL)));
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

	public static TaskType waitForTaskWithSuccessStatus(URI taskType) throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		try {
			return vcdClient.getTaskMonitor().waitForStatus(taskType.toString(), VcfUtils.LARGE_TASK_TIMEOUT,
					VcfUtils.POLL_INTERVAL, null, TaskStatus.SUCCESS);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<Supervisor> getSupervisorsForVc(final String vcId, final OpenApiClient client) {
		final SupervisorClustersApi supervisorApiProxy = client.createProxy(SupervisorClustersApi.class);
		return supervisorApiProxy.getSupervisors(PAGE, PAGE_SIZE, "virtualCenter.id==" + vcId /* filter */,
				null /* sortAsc */, null /* sortDesc */).getValues();
	}

	public static VCenterServer getVc(final OpenApiClient client) {
		final VirtualCenterApi vcApiProxy = client.createProxy(VirtualCenterApi.class);
		return vcApiProxy
				.queryVirtualCenters(PAGE, PAGE_SIZE, null /* filter */, null /* sortAsc */, null /* sortDesc */)
				.getValues().get(0);
	}

	public static NsxManager getNsxManager(final OpenApiClient client) {
		final NsxManagersApi nsxApiProxy = client.createProxy(NsxManagersApi.class);
		return nsxApiProxy.getNsxManagers(PAGE, PAGE_SIZE, null /* filter */, null /* sortAsc */, null /* sortDesc */)
				.getValues().get(0);
	}
}
