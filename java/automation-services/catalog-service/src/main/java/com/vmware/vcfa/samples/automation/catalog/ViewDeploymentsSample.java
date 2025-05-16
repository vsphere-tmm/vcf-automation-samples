package com.vmware.vcfa.samples.automation.catalog;

import com.vmware.vcfa.catalog.ApiClient;
import com.vmware.vcfa.catalog.ApiException;
import com.vmware.vcfa.catalog.DeploymentsApi;
import com.vmware.vcfa.catalog.model.CatalogItemRequest;
import com.vmware.vcfa.catalog.model.Deployment;
import com.vmware.vcfa.samples.automation.catalog.util.DeploymentInputReader;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcfa.util.ConfigReader;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

public class ViewDeploymentsSample {
    private ApiClient apiClient;
    private DeploymentsApi deploymentsApi;
    private DeploymentInputReader deploymentInputReader;

    public static void main(String[] args) throws ApiException {
        ViewDeploymentsSample service = new ViewDeploymentsSample();
        service.getDeploymentProperties();
    }


    private ViewDeploymentsSample() {
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
                apiClient.setSslCaCert(config.getSslCaCert(verifySsl));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        apiClient.setBearerToken(accessToken);
        deploymentsApi = new DeploymentsApi(apiClient);
    }

    public void getDeploymentProperties() {
        CatalogItemRequest catalogItemRequest = new CatalogItemRequest();
        catalogItemRequest.setDeploymentName(deploymentInputReader.getDeploymentName());
        catalogItemRequest.setProjectId(deploymentInputReader.getProjectId());
        try {
            System.out.println("Fetching the details of the deployment with the Id:" + deploymentInputReader.getDeploymentId());
            Deployment deployment = deploymentsApi.getDeploymentById1(UUID.fromString(deploymentInputReader.getDeploymentId()), true, true, false, Set.of(), false);
            if (deployment.getId() != null) {
                System.out.println(deployment.getName() + "with Id:" + deployment.getId() + " retrieved successfully.");
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}
