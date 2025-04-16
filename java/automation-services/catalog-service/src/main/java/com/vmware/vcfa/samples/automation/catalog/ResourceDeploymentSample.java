package com.vmware.vcfa.samples.automation.catalog;

import catalog.util.DeploymentInputReader;
import com.vmware.vcfa.catalog.ApiClient;
import com.vmware.vcfa.catalog.ApiException;
import com.vmware.vcfa.catalog.ResourcesApi;
import com.vmware.vcfa.catalog.model.CatalogItemRequest;
import com.vmware.vcfa.catalog.model.ResourceRequestResponse;
import com.vmware.vcfa.catalog.model.ResourceSpecification;
import com.vmware.vcfa.util.ConfigReader;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class ResourceDeploymentSample {
    ApiClient apiClient;
    ResourcesApi resourcesApi;
    DeploymentInputReader deploymentInputReader;

    public static void main(String[] args) throws ApiException {
        ResourceDeploymentSample service = new ResourceDeploymentSample();
        service.createResource();
    }


    public ResourceDeploymentSample() {
        deploymentInputReader = new DeploymentInputReader();
        ConfigReader config = new ConfigReader();
        String accessToken = config.getAccessToken();
        String basePath = config.getServerUrl();

        apiClient = new ApiClient();
        apiClient.setBasePath(basePath);
        try {
            boolean verifySsl = config.getVerifySsl();
            apiClient.setVerifyingSsl(verifySsl);
            if (verifySsl) {
                apiClient.setSslCaCert(new FileInputStream(config.getSslCertPath()));
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        apiClient.setBearerToken(accessToken);
        resourcesApi = new ResourcesApi(apiClient);
    }

    public void createResource() {
        CatalogItemRequest catalogItemRequest = new CatalogItemRequest();
        catalogItemRequest.setDeploymentName(deploymentInputReader.getDeploymentName());
        catalogItemRequest.setProjectId(deploymentInputReader.getProjectId());

        ResourceSpecification resourceSpecification = getResourceSpecification();
        try {
            ResourceRequestResponse resourceRequestResponse = resourcesApi.createResource(resourceSpecification);
            if (resourceRequestResponse != null && resourceRequestResponse.getDeploymentId() != null) {
                System.out.println("Resource is created as part of the deployment with the Id:" + resourceRequestResponse.getDeploymentId());
            }

        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private ResourceSpecification getResourceSpecification() {
        ResourceSpecification resourceSpecification = new ResourceSpecification();
        resourceSpecification.projectId(deploymentInputReader.getProjectId());
        resourceSpecification.setName(deploymentInputReader.getDeploymentName());
        resourceSpecification.type("Cloud.vSphere.Machine");
        Map<String, Object> properties = new HashMap<>();
        properties.put("imageRef", deploymentInputReader.getImageName());
        properties.put("flavor", deploymentInputReader.getflavorName());
        properties.put("placement", String.format("%s/%s", "/iaas/api/zone", deploymentInputReader.getCloudZoneId()));

        resourceSpecification.properties(properties);
        return resourceSpecification;
    }
}
