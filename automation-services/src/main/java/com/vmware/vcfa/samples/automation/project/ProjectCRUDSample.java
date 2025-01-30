package com.vmware.vcfa.samples.automation.project;

import com.vmware.project_service.ApiException;
import com.vmware.project_service.ProjectApi;
import com.vmware.project_service.ApiClient;
import com.vmware.project_service.model.Principal;
import com.vmware.project_service.model.ProjectSpecification;
import com.vmware.vcfa.samples.automation.util.ConfigReader;

import java.util.List;

public class ProjectCRUDSample {

    public static void main(String[] args) throws ApiException {
        ProjectCRUDSample service = new ProjectCRUDSample();
        service.createProject();
    }

    /**
     * Create a project named sampleproject.This example assumes that sampleproject does not exist.
     * The different roles added to the sample project are not validated.
     * @throws ApiException
     */
    public  void createProject() throws ApiException {
        String api_version="2019-01-15";
        ConfigReader config = new ConfigReader();
        String accessToken = config.getAccessToken();
        String basePath = config.getServerUrl();

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(basePath);
        apiClient.setVerifyingSsl(false);
        apiClient.setApiKeyPrefix("Bearer");
        apiClient.setApiKey(accessToken);

        ProjectApi projectApi = new ProjectApi(apiClient);
        ProjectSpecification projectSpecification = buildProjectSpecification("sampleproject",
                "fritz@coke.sqa-local.com", "user");

        projectApi.create(projectSpecification, false, api_version);
    }


    /**
     * Create a project specification for sample project with administrators, members, and viewers at company coke.com.
     * @return
     */
    private static ProjectSpecification buildProjectSpecification(String projectName, String email, String role) {
        ProjectSpecification projectSpecification = new ProjectSpecification();
        projectSpecification.name(projectName);
        projectSpecification.setDescription("Sample project");

        Principal admin = new Principal();
        admin.setEmail(email);
        admin.setType(role);
        projectSpecification.setAdministrators(List.of(admin));

        Principal member = new Principal();
        member.setEmail(email);
        member.setType(role);
        projectSpecification.setAuditors(List.of(member));

        Principal viewer = new Principal();
        viewer.setEmail(email);
        viewer.setType(role);
        projectSpecification.setViewers(List.of(viewer));

        return projectSpecification;
    }
}