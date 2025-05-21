/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf;

import java.net.URI;
import java.util.Arrays;
import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.rest.openapi.api.RegionsApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.NsxManager;
import com.vmware.vcloud.rest.openapi.model.Region;
import com.vmware.vcloud.rest.openapi.model.Supervisor;
import com.vmware.vcloud.rest.openapi.model.VCenterServer;

public class RegionCreationExample {

	private static VcdClient vcdClient;
	private static OpenApiClient openApiClient;
	private static VCenterServer vc;
	private static int PAGE = 1;
	private static int PAGE_SIZE = 5;

	public static void main(String[] args) throws Exception {
		createRegion();
	}

	/**
	 * Example to create Region
	 * 
	 * @throws Exception
	 */
	public static Region createRegion() throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		openApiClient = VcfUtils.getClient().getOpenApiClient();
		final String storageClassName = SettingsLoader.getProviderConfig().get(Constants.STORAGE_CLASS);
		final Region testRegion = new Region();
		testRegion.setName("test-region");
		testRegion.setDescription("Test region description");
		vc = VcfUtils.getVc(openApiClient);
		final Supervisor supervisorsForVc = VcfUtils.getSupervisorsForVc(vc.getVcId(), openApiClient).get(0);
		testRegion.setSupervisors(Arrays
				.asList(new EntityReference().id(supervisorsForVc.getSupervisorId()).name(supervisorsForVc.getName())));
		final NsxManager nsxManager = VcfUtils.getNsxManager(openApiClient);
		testRegion.setNsxManager(new EntityReference().id(nsxManager.getId()).name(nsxManager.getName()));
		testRegion.setStoragePolicies(Arrays.asList(storageClassName));
		final RegionsApi regionsApi = openApiClient.createProxy(RegionsApi.class);
		regionsApi.createRegion(testRegion);
		final URI task = openApiClient.getLastTaskUri(regionsApi);
		VcfUtils.waitForTaskWithSuccessStatus(task);
		System.out.println("Region creation successful ");
		return regionsApi.queryRegions(PAGE, PAGE_SIZE, "name==" + testRegion.getName() /*filter*/, null /*sortAsc*/, null /*sortDesc*/).getValues().get(0);
	}
}
