/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.rest.openapi.api.ContentLibraryApi;
import com.vmware.vcloud.rest.openapi.api.RegionsApi;
import com.vmware.vcloud.rest.openapi.model.ContentLibrary;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.NsxManager;
import com.vmware.vcloud.rest.openapi.model.Region;
import com.vmware.vcloud.rest.openapi.model.StorageClass;
import com.vmware.vcloud.rest.openapi.model.Supervisor;

public class CreateContentLibraryExample {

    private static VcdClient vcdClient;
    private static OpenApiClient openApiClient;
    private static RegionsApi regionsApi;
    private static ContentLibraryApi contentLibraryApi;
    private static Region region1;
    private static Region region2;
    private static ContentLibrary contentLibrary;
    private static String storageClassName;
    public static final long LARGE_TASK_TIMEOUT = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);


    public static void main(String[] args) throws Exception {
        setup();
        createContentLibrary();
        VcfUtils.printContentLibraryDetails(contentLibrary);
    }

    private static void setup() throws Exception {
        final List<Region> currentRegions = VcfUtils.getRegions(openApiClient);
        if (currentRegions != null && !currentRegions.isEmpty()) {
            System.out.println("Found " + currentRegions.size() + " regions in the VCFA instance");
            final Iterator<Region> regionIterator = currentRegions.iterator();
            region1 = regionIterator.next();
            System.out.println("Using Region: " + region1.getName() + " as the first region");
            if (regionIterator.hasNext()) {
                region2 = regionIterator.next();
                System.out.println("Using Region: " + region2.getName() + " as the second region");
            }
        } else {
            storageClassName = SettingsLoader.getProviderConfig().get(Constants.STORAGE_CLASS);
            final List<NsxManager> nsxManagers = VcfUtils.getNsxManagers(openApiClient);
            final Iterator<NsxManager> nsxManagerIterator = nsxManagers.iterator();
            final NsxManager nsxManager1 = nsxManagerIterator.next();
            final List<Supervisor> supervisorsForNsxManager1 = VcfUtils.getSupervisorsForNsx(nsxManager1.getId(), openApiClient);
            final Iterator<Supervisor> supervisorIterator = supervisorsForNsxManager1.iterator();
            final Supervisor supervisor1 = supervisorIterator.next();
            NsxManager nsxManager2 = null;
            Supervisor supervisor2 = null;
            if (nsxManagerIterator.hasNext()) {
                nsxManager2 = nsxManagerIterator.next();
                final List<Supervisor> supervisorsForNsxManager2 = VcfUtils.getSupervisorsForNsx(nsxManager2.getId(), openApiClient);
                if (supervisorsForNsxManager2 != null && !supervisorsForNsxManager2.isEmpty()) {
                    supervisor2 = supervisorsForNsxManager2.iterator().next();
                }
            }
            region1 = prepareRegionForVcAndNsxManager(nsxManager1, supervisor1, 1);
            System.out.println("Created region "+ region1.getName() + " as region 1");
            if (nsxManager2 != null && supervisor2 != null) {
                region2 = prepareRegionForVcAndNsxManager(nsxManager2, supervisor2, 2);
                System.out.println("Created region "+ region2.getName() + " as region 2");
            }
        }
    }

    private static Region prepareRegionForVcAndNsxManager(final NsxManager nsxManager, final Supervisor supervisor,
                                                          final int regionCt) throws Exception {
        final Region region = new Region();
        region.setName("test-region-" + regionCt);
        region.setDescription("Test region description");
        region.setSupervisors(Collections.singletonList(
                new EntityReference().id(supervisor.getSupervisorId()).name(supervisor.getName())));
        region.setNsxManager(new EntityReference().id(nsxManager.getId()).name(nsxManager.getName()));
        region.setStoragePolicies(Collections.singletonList(storageClassName));
        regionsApi.createRegion(region);
        final TaskType task = openApiClient.getLastTask(regionsApi);
        VcfUtils.waitForTaskWithSuccessStatus(URI.create(task.getHref()));
        vcdClient.getTaskMonitor().waitForSuccess(task, LARGE_TASK_TIMEOUT);
        return VcfUtils.getRegions(openApiClient).stream().filter(reg -> region.getName().equals(reg.getName())).findAny().orElse(null);
    }

    public static void createContentLibrary() throws Exception {
        final List<StorageClass> storageClasses = VcfUtils.getStorageClassesForRegion(openApiClient, region1);
        StorageClass storageClass = storageClasses.iterator().next();
        final ContentLibrary newContentLibrary = new ContentLibrary();
        newContentLibrary.setName("test-content-library");
        newContentLibrary.setDescription("Test content library description");
        newContentLibrary.setStorageClasses(Collections.singletonList(
                new EntityReference().id(storageClass.getId()).name(storageClass.getName())));
        System.out.println("Creating new content library with name: " + newContentLibrary.getName() + " and storage class " + storageClass.getName());
        contentLibraryApi.createContentLibrary(newContentLibrary);
        final TaskType task = openApiClient.getLastTask(contentLibraryApi);
        VcfUtils.waitForTaskWithSuccessStatus(URI.create(task.getHref()));
        contentLibrary = VcfUtils.getContentLibraryWithName(openApiClient, newContentLibrary.getName());
    }
}
