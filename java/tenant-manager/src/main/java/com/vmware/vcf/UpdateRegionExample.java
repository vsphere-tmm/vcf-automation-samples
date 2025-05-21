/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.rest.openapi.api.RegionsApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.Region;
import com.vmware.vcloud.rest.openapi.model.Supervisor;
import com.vmware.vcloud.rest.openapi.model.VCenterServer;

/**
 * Update Region description, supervisor and storage policy. 
 * @throws Exception 
 **/
public class UpdateRegionExample {

	private static VcdClient vcdClient;
	private static OpenApiClient openApiClient;
	private static VCenterServer vc;
	private static RegionsApi regionsApi;
	private static int PAGE = 1;
	private static int PAGE_SIZE = 5;

	public static void main(String[] args) throws Exception {
		updateRegion();
	}
	
	private static void updateRegion() throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		openApiClient = VcfUtils.getClient().getOpenApiClient();
		regionsApi = openApiClient.createProxy(RegionsApi.class);
		final List<Region> regions = queryRegions(PAGE, PAGE_SIZE, null /*filter*/, null /*sortAsc*/, null /*sortDesc*/);
		final Region region;
		if (regions.isEmpty()) {
			region = RegionCreationExample.createRegion();
		} else {
			region = regions.get(0);
		}
		final List<String> regionSupervisor = region.getSupervisors().stream().map(sp -> sp.getId()).collect(Collectors.toList());
		if (vc == null) {
			vc = VcfUtils.getVc(openApiClient);
		}
		final Supervisor supervisor = VcfUtils.getSupervisorsForVc(vc.getVcId(), openApiClient).stream().filter(sup -> !regionSupervisor.contains(sup.getSupervisorId())).findAny().orElse(null);
		region.setDescription("new-region-description");
		final String storageClass = SettingsLoader.getProviderConfig().get(Constants.STORAGE_CLASS_TO_UPDATE_REGION);
		if (storageClass != null) {
			region.getStoragePolicies().add(storageClass);
		}
		if (supervisor != null) {
			region.getSupervisors().add(new EntityReference().id(supervisor.getSupervisorId()).name(supervisor.getName()));
		}	
		regionsApi.updateRegion(region, region.getId());
		final TaskType task = openApiClient.getLastTask(regionsApi);
		VcfUtils.waitForTaskWithSuccessStatus(URI.create(task.getHref()));
		final Region updatedRegion = regionsApi.getRegion(region.getId());
		System.out.println("Region name is: " + updatedRegion.getName());
		System.out.println("Region description is: " + updatedRegion.getDescription());
		System.out.println("Storage policies available to this Region: " + updatedRegion.getStoragePolicies().stream().collect(Collectors.joining(",")));
		System.out.println("Supervisors available to this Region: " + updatedRegion.getSupervisors().stream().map(sup -> sup.getName()).collect(Collectors.joining(", ")));
	}
	
	private static List<Region> queryRegions(final int page, final int pageSize, final String filter, final String sortAsc, final String sortDesc ) {
		return regionsApi.queryRegions(page, pageSize, filter, sortAsc, sortDesc).getValues();
	}
}
