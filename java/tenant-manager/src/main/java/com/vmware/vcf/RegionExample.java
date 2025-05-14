/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.Client;

import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.TaskStatus;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.rest.openapi.api.NsxManagersApi;
import com.vmware.vcloud.rest.openapi.api.RegionsApi;
import com.vmware.vcloud.rest.openapi.api.SupervisorClustersApi;
import com.vmware.vcloud.rest.openapi.api.VirtualCenterApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.NsxManager;
import com.vmware.vcloud.rest.openapi.model.Region;
import com.vmware.vcloud.rest.openapi.model.Supervisor;
import com.vmware.vcloud.rest.openapi.model.VCenterServer;

public class RegionExample {
	
	private static VcdClient vcdClient;
	private static OpenApiClient openApiClient;
	private static VCenterServer vc;
	public static final long LARGE_TASK_TIMEOUT = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);
	public static final int POLL_INTERVAL = 2000;
	
	public static void main(String[] args) throws Exception {		
        createRegion();
        getRegionDetails();
        updateRegion();
    }

	/**
	 * Example to create Region
	 * @throws Exception
	 */
	public static void createRegion() throws Exception {		
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		openApiClient = VcfUtils.getClient().getOpenApiClient();
		final String storageClassName = SettingsLoader.getProviderConfig().get(Constants.STORAGE_CLASS);
		final Region testRegion = new Region();
		testRegion.setName("test-region");
		testRegion.setDescription("Test region description");
		vc = getVc(openApiClient);
		final Supervisor supervisorsForVc = getSupervisorsForVc(vc.getVcId(), openApiClient).get(0);
		testRegion.setSupervisors(Arrays
				.asList(new EntityReference().id(supervisorsForVc.getSupervisorId()).name(supervisorsForVc.getName())));
		final NsxManager nsxManager = getNsxManager(openApiClient);
		testRegion.setNsxManager(new EntityReference().id(nsxManager.getId()).name(nsxManager.getName()));
		testRegion.setStoragePolicies(Arrays.asList(storageClassName));
		final RegionsApi regionsApi = openApiClient.createProxy(RegionsApi.class);
		final TaskType task = waitForApiProxyTaskCompletion(regionsApi, () -> {
            regionsApi.createRegion(testRegion);
            return null;
		});
		vcdClient.getTaskMonitor().waitForSuccess(task, LARGE_TASK_TIMEOUT);
	}
	
	/**
	 * Example to fetch details of Region
	 * @throws Exception
	 */
	public static void getRegionDetails() throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		openApiClient = VcfUtils.getClient().getOpenApiClient();
		final RegionsApi regionsApi = openApiClient.createProxy(RegionsApi.class);
		final Region region = regionsApi.queryRegions(1, 5, null, null, null).getValues().get(0);
		if (region == null) {
			createRegion();
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

	/**
	 * Update Region description, supervisor and storage policy. 
	 * @throws Exception 
	 **/
	public static void updateRegion() throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
		openApiClient = VcfUtils.getClient().getOpenApiClient();
		final RegionsApi regionsApi = openApiClient.createProxy(RegionsApi.class);
		final Region region = regionsApi.queryRegions(1, 5, null, null, null).getValues().get(0);
		final List<String> regionSupervisor = region.getSupervisors().stream().map(sp -> sp.getId()).collect(Collectors.toList());
		if (vc == null) {
			getVc(openApiClient);
		}
		final Supervisor supervisor = getSupervisorsForVc(vc.getVcId(), openApiClient).stream().filter(sup -> !regionSupervisor.contains(sup.getSupervisorId())).findAny().orElse(null);
		region.setDescription("new-region-description");
		region.getStoragePolicies().add("VM Encryption Policy");
		if (supervisor != null) {
			region.getSupervisors().add(new EntityReference().id(supervisor.getSupervisorId()).name(supervisor.getName()));
		}		
		final TaskType task = waitForApiProxyTaskCompletion(regionsApi, () -> {
            regionsApi.updateRegion(region, region.getId());
            return null;
		});
		vcdClient.getTaskMonitor().waitForSuccess(task, LARGE_TASK_TIMEOUT);
		Region updatedRegion = regionsApi.getRegion(region.getId());
		System.out.println("Region name is: " + updatedRegion.getName());
		System.out.println("Region description is: " + updatedRegion.getDescription());
		System.out.println("Storage policies available to this Region: " + updatedRegion.getStoragePolicies().stream().collect(Collectors.joining(",")));
		System.out.println("Supervisors available to this Region: " + updatedRegion.getSupervisors().stream().map(sup -> sup.getName()).collect(Collectors.joining(", ")));
	}

	public static List<Supervisor> getSupervisorsForVc(final String vcId, final OpenApiClient client) {
		final SupervisorClustersApi supervisorApiProxy = client.createProxy(SupervisorClustersApi.class);
		return supervisorApiProxy.getSupervisors(1, 5, "virtualCenter.id==" + vcId, null, null).getValues();
	}

	public static VCenterServer getVc(final OpenApiClient client) {
		final VirtualCenterApi vcApiProxy = client.createProxy(VirtualCenterApi.class);
		return vcApiProxy.queryVirtualCenters(1, 5, null, null, null).getValues().get(0);
	}

	public static NsxManager getNsxManager(final OpenApiClient client) {
		final NsxManagersApi nsxApiProxy = client.createProxy(NsxManagersApi.class);
		return nsxApiProxy.getNsxManagers(1, 5, null, null, null).getValues().get(0);
	}
    
    public static TaskType waitForTaskWithSuccessStatus(Client rawClient) throws Exception {
		if (vcdClient == null) {
			vcdClient = VcfUtils.getClient();
		}
        final String responseLocation = getResponseLocation(rawClient);
        try {
            return vcdClient.getTaskMonitor().waitForStatus(responseLocation, LARGE_TASK_TIMEOUT,
                    POLL_INTERVAL, null, TaskStatus.SUCCESS);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected static TaskType waitForApiProxyTaskCompletion(final Object apiProxyInterface,
            final Supplier<Void> regionApiMethodSupplier) throws Exception {
        final Client rawClient = openApiClient
                .getWebClientForNextCall(apiProxyInterface);
        regionApiMethodSupplier.get();
        return waitForTaskWithSuccessStatus(rawClient);
    }
    
    private static String getResponseLocation(Client rawClient) {
        Objects.requireNonNull(rawClient.getResponse(), "Response is null");
        final Response response = rawClient.getResponse();
        if (response.getLocation() == null) {
            throw new IllegalStateException("Location is not found in the response");
        }
        return response.getLocation().toString();
    }

}
