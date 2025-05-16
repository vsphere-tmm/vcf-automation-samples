package com.vmware.vcfa.samples.automation.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vcfa.catalog.ApiClient;
import com.vmware.vcfa.catalog.ApiException;
import com.vmware.vcfa.catalog.PoliciesApi;
import com.vmware.vcfa.catalog.model.CatalogItemRequest;
import com.vmware.vcfa.catalog.model.Policy;
import com.vmware.vcfa.samples.automation.catalog.util.DeploymentInputReader;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcfa.util.ConfigReader;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class CreateDeploymentLimitPolicySample {
    private ApiClient apiClient;
    private PoliciesApi policiesApi;
    private DeploymentInputReader deploymentInputReader;

    public static void main(String[] args) throws ApiException {
        CreateDeploymentLimitPolicySample service = new CreateDeploymentLimitPolicySample();
        service.createDeploymentLimitPolicy();
    }

    private CreateDeploymentLimitPolicySample() {
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
        policiesApi = new PoliciesApi(apiClient);
    }

    /**
     * Create a Deployment Limit Policy. This policy sets the limit at the deployment level and at the individual machine level.
     */
    public void createDeploymentLimitPolicy() {
        CatalogItemRequest catalogItemRequest = new CatalogItemRequest();
        catalogItemRequest.setDeploymentName(deploymentInputReader.getDeploymentName());
        catalogItemRequest.setProjectId(deploymentInputReader.getProjectId());
        try {
            Policy policy = new Policy();
            policy.name("Deployment_level_policy");
            policy.enforcementType(Policy.EnforcementTypeEnum.HARD);
            policy.typeId("com.vmware.policy.deployment.limit");
            Map<String, Object> def = buildSampleDefinition();

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            policy.definition(def);
            Object response = policiesApi.createPolicy1(policy, null, false);
            if (response instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) response;
                Object id = map.get("id");
                System.out.println("The deployment limit policy is created with ID: " + id);
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }


    public static Map<String, Object> buildSampleDefinition() {

        System.out.println("Defining deployment limit of 6 CPU's, 20 instances, 50GB memory and 500GB storage");
        Map<String, Object> definition = new HashMap<>();

        // deploymentLimits
        Map<String, Object> deploymentLimits = new HashMap<>();

        deploymentLimits.put("cpu", Map.of("value", 6));
        deploymentLimits.put("instances", Map.of("value", 20));
        deploymentLimits.put("memory", Map.of("unit", "GB", "value", 50));
        deploymentLimits.put("storage", Map.of("unit", "GB", "value", 500));

        // deploymentResourceLimits -> resources -> [ { name, limits } ]
        System.out.println("Defining Machine limit of 2 CPU's, 16GB memory and 100GB storage");

        Map<String, Object> resource = new HashMap<>();
        resource.put("name", "vSphere-Machine-Limits");

        Map<String, Object> limits = new HashMap<>();
        limits.put("cpu", Map.of("value", 2));
        limits.put("memory", Map.of("unit", "GB", "value", 16));
        limits.put("storage", Map.of("unit", "GB", "value", 100));

        resource.put("limits", limits);

        Map<String, Object> deploymentResourceLimits = new HashMap<>();
        deploymentResourceLimits.put("resources", Collections.singletonList(resource));

        // Final structure
        definition.put("deploymentLimits", deploymentLimits);
        definition.put("deploymentResourceLimits", deploymentResourceLimits);

        return definition;
    }
}
