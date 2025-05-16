package com.vmware.vcf;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.BadRequestException;

import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.rest.openapi.api.OrgApi;
import com.vmware.vcloud.rest.openapi.api.RolesApi;
import com.vmware.vcloud.rest.openapi.api.UserApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.EntityReferences;
import com.vmware.vcloud.rest.openapi.model.Org;
import com.vmware.vcloud.rest.openapi.model.Orgs;
import com.vmware.vcloud.rest.openapi.model.Role;
import com.vmware.vcloud.rest.openapi.model.Roles;
import com.vmware.vcloud.rest.openapi.model.VcdUser;

/**
 * Tenant Manager org creation example that creates an organization and an initial user.
 */
public class TmOrgCreationExample {

    private static final int ORG_TASK_TIMEOUT_MILLIS = 10_000;
    private static final String SYSTEM_ORG_ID = "urn:vcloud:org:a93c9db9-7471-3192-8d09-a8f7eeda85f9";
    private static final String EXAMPLE_ORG_NAME = "exampleOrg";
    private static final String EXAMPLE_ORG_DESC = "An example organization.";
    private static final String EXAMPLE_ORG_DISPLAY_NAME = "EXAMPLE_ORG";
    private static final String EXAMPLE_USERNAME = "exampleFirstUser";
    private static final String EXAMPLE_PASSWORD = "password";
    private static final String LOCAL_PROVIDER_TYPE = "LOCAL";
    private static final String ORG_ADMIN_ROLE_NAME = "Organization Administrator";
    private static VcdClientImpl client;
    private static OpenApiClient openApiClient;
    private static OrgApi orgsApi = null;
    private static RolesApi rolesApi = null;
    private static UserApi userApi = null;
    private static CxfClientSecurityContext securityContext;


    public static void main(String[] args) throws Exception {
        setup();

        final Org createdOrg = createOrg(EXAMPLE_ORG_NAME, EXAMPLE_ORG_DESC, EXAMPLE_ORG_DISPLAY_NAME, true);

        System.out.printf("Created org %s: %s%n", createdOrg.getName(), createdOrg);

        // Set the openAPI client tenant context to the created org ID to perform actions in that org.
        openApiClient.setTenantContextHeader(createdOrg.getId());
        final Role orgAdminRole = getRoleWithName(ORG_ADMIN_ROLE_NAME);
        final VcdUser firstUser = createLocalUser(EXAMPLE_USERNAME, EXAMPLE_PASSWORD, orgAdminRole);

        System.out.printf("Created user %s in org %s: %s%n", firstUser.getUsername(), createdOrg.getName(), firstUser);

        // Reset tenant context to the System org.
        openApiClient.setTenantContextHeader(SYSTEM_ORG_ID);
    }

    private static void setup() throws Exception{
        final KeyStore truststore = getKeyStore();
        securityContext = CxfClientSecurityContext.getCxfClientSecurityContext(null, null, truststore, null, false);

        System.out.println("Using rest-api-client-1.0.0...");

        openApiClient = getClient().getOpenApiClient();
        orgsApi = openApiClient.createProxy(OrgApi.class);
        rolesApi = openApiClient.createProxy(RolesApi.class);
        userApi = openApiClient.createProxy(UserApi.class);
    }

    public static Orgs getOrgs() {
        final Orgs orgs = orgsApi.queryOrgs(1, 2, null, null, null);
        return orgs;
    }

    private static VcdClient getClient() {
        if (client != null) {
            return client;
        }

        final String serverUrl = SettingsLoader.getProviderConfig().get(Constants.SERVER_URL);
        final String serverVersion = SettingsLoader.getProviderConfig().get(Constants.SERVER_VERSION);
        final String username = SettingsLoader.getProviderConfig().get(Constants.AUTH_USERNAME);
        final String tenant = SettingsLoader.getProviderConfig().get(Constants.AUTH_TENANT);
        final String password = SettingsLoader.getProviderConfig().get(Constants.AUTH_PASSWORD);

        client = new VcdClientImpl(URI.create(serverUrl), serverVersion, securityContext);
        client.setCredentials(new VcdBasicLoginCredentials(username, tenant, password));

        return client;
    }

    /*
     * Only needed if your VCD instance is not using a well-signed certificate.
     */
    private static KeyStore getKeyStore() throws Exception {
        final String alias = SettingsLoader.getProviderConfig().get(Constants.TRUSTSTORE_ALIAS);
        final String truststoreType = SettingsLoader.getProviderConfig().get(Constants.TRUSTSTORE_TYPE);

        // Initialize KeyStore
        final KeyStore keyStore = KeyStore.getInstance(truststoreType);
        keyStore.load(null, null);

        // Check if the certificate is already present
        if (keyStore.containsAlias(alias)) {
            System.out.println("Certificate already exists in the KeyStore with alias: " + alias);
            return keyStore;
        }

        // Fetch and add the certificate if not already present
        final X509Certificate[] cert = CertificateUtil.getVcfCert(URI.create(SettingsLoader.getProviderConfig().get(Constants.SERVER_URL)));
        if (cert != null && cert.length > 0) {
            keyStore.setCertificateEntry(alias, cert[cert.length - 1]);
            System.out.println("Added new certificate to KeyStore with alias: " + alias);
        } else {
            System.out.println("No certificate found to add to KeyStore.");
        }

        return keyStore;
    }

    public static Org createOrg(final String name, final String description, final String displayName,
                                final Boolean isEnabled) throws BadRequestException {
        final Org newOrg = new Org()
                .name(name)
                .description(description)
                .displayName(displayName)
                .isEnabled(isEnabled);
        return createOrg(newOrg);
    }

    private static Org createOrg(final Org newOrg) throws BadRequestException {
        Org createdOrg = orgsApi.createOrg(newOrg);
        if (createdOrg == null) {
            createdOrg = waitForOrgCreateTask();
        }
        return createdOrg;
    }

    private static Org waitForOrgCreateTask() {
        try {
            final String updateTaskLink = openApiClient.getLastTaskUri(orgsApi).toString();
            final TaskType taskType = client.getTaskMonitor().waitForSuccess(updateTaskLink, ORG_TASK_TIMEOUT_MILLIS);
            return orgsApi.getOrg(taskType.getOwner().getId());
        } catch (TimeoutException e) {
            throw new RuntimeException("Failed to wait for result of org update task.", e);
        }
    }

    private static VcdUser createLocalUser(String username, String password, Role role) {
        final VcdUser user = buildVcdUser(username, password, LOCAL_PROVIDER_TYPE, role);
        return userApi.createUser(user);
    }

    private static VcdUser buildVcdUser(String name, String password, String providerType,
                                Role role) {
        final EntityReference roleEntityReference = new EntityReference().name(role.getName()).id(role.getId());
        final List<EntityReference> roleEntityRefList =
                roleEntityReference == null ? Collections.emptyList() : Collections.singletonList(roleEntityReference);
        return new VcdUser()
                .username(name)
                .password(password)
                .providerType(providerType)
                .roleEntityRefs(roleEntityRefList);
    }

    private static Role getRoleWithName(String roleName) {
        final String roleNameFilter = "name==" + roleName;
        final Roles foundRoles = rolesApi.queryTenantRoles(1, 1, roleNameFilter,null, null);
        if (foundRoles.getResultTotal() == 0) {
            throw new IllegalStateException("Unable to fetch role " + roleName);
        }
        return foundRoles.getValues().get(0);
    }
}
