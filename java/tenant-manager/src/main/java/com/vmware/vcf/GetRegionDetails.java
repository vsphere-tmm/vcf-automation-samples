/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf;

import java.util.stream.Collectors;

import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdClient;

import com.vmware.vcloud.rest.openapi.api.RegionsApi;
import com.vmware.vcloud.rest.openapi.model.Region;

/**
 * Example to fetch details of Region
 * @throws Exception
 */
public class GetRegionDetails {
	
	private static VcdClient vcdClient;
	private static OpenApiClient openApiClient;
	private static RegionsApi regionsApi;

	public static void main(String[] args) throws Exception {
		getRegionDetails();

	}
	
	public static void getRegionDetails() throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		openApiClient = VcfUtils.getClient().getOpenApiClient();
		regionsApi = openApiClient.createProxy(RegionsApi.class);
		final Region region;
		final String regionId = SettingsLoader.getProviderConfig().get(Constants.REGION_ID);
		if (!regionId.isBlank()) {
			region = regionsApi.getRegion(regionId);
		} else {
			region = RegionCreationExample.createRegion();
		} 
		System.out.println("Region name is: " + region.getName());
		System.out.println("Region description is: " + region.getDescription());
		System.out.println("Loadbalancer type backing the region is: " + region.getLoadBalancerType());
		System.out.println("Region status is: " + region.getStatus());
		System.out.println("Total CPU resources in MHz available to this Region: " + region.getCpuCapacityMHz());
		System.out.println("Total CPU reservation in MHz available to this Region: " + region.getCpuReservationCapacityMHz());
		System.out.println("Total memory resources (in mebibytes) available to this Region: " + region.getMemoryCapacityMiB());
		System.out.println("Total memory reservation (in mebibytes) available to this Region: " + region.getMemoryReservationCapacityMiB());
		System.out.println("NSX Manager backing the region is: " + region.getNsxManager().getName());
		System.out.println("Storage policies available to this Region: " + region.getStoragePolicies().stream().collect(Collectors.joining(",")));
		System.out.println("Supervisors available to this Region: " + region.getSupervisors().stream().map(supervisor -> supervisor.getName()).collect(Collectors.joining(", ")));
	}
}
