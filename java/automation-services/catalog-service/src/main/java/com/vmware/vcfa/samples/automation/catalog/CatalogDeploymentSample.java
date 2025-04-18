package com.vmware.vcfa.samples.automation.catalog;

import com.vmware.vcfa.samples.automation.catalog.util.DeploymentInputReader;
import com.vmware.vcfa.catalog.ApiClient;
import com.vmware.vcfa.catalog.ApiException;
import com.vmware.vcfa.catalog.CatalogItemsApi;
import com.vmware.vcfa.catalog.model.CatalogItemRequest;
import com.vmware.vcfa.catalog.model.CatalogItemRequestResponse;
import com.vmware.vcfa.util.ConfigReader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;

public class CatalogDeploymentSample {

    ApiClient apiClient;
    CatalogItemsApi catalogItemsApi;
    DeploymentInputReader deploymentInputReader;

    public static void main(String[] args) throws ApiException {
        CatalogDeploymentSample service = new CatalogDeploymentSample();
        service.createDeployment();
    }


    public CatalogDeploymentSample() {
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
        catalogItemsApi = new CatalogItemsApi(apiClient);
    }

    public void createDeployment() {
        CatalogItemRequest catalogItemRequest = new CatalogItemRequest();
        catalogItemRequest.setDeploymentName(deploymentInputReader.getDeploymentName());
        catalogItemRequest.setProjectId(deploymentInputReader.getProjectId());
        UUID id = UUID.fromString(deploymentInputReader.getCatalogItemId());
        try {
            List<CatalogItemRequestResponse> responseList = catalogItemsApi.requestCatalogItemInstances1(id, catalogItemRequest);
            if (!responseList.isEmpty()) {
                System.out.println(responseList.get(0).getDeploymentName() + "deployed successfully..");
            }

        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}

