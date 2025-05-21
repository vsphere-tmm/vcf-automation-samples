/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.BadRequestException;
import javax.xml.bind.JAXBElement;

import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcf.util.OrgLdapConfigUtil;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;
import com.vmware.vcloud.api.rest.constants.RelationType;
import com.vmware.vcloud.api.rest.constants.RestAdminConstants;
import com.vmware.vcloud.api.rest.constants.RestConstants;
import com.vmware.vcloud.api.rest.schema_v1_5.AdminOrgType;
import com.vmware.vcloud.api.rest.schema_v1_5.CustomOrgLdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgType;
import com.vmware.vcloud.api.rest.schema_v1_5.ReferenceType;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.api.rest.schema_v1_5.UserType;
import com.vmware.vcloud.rest.openapi.api.OrgApi;
import com.vmware.vcloud.rest.openapi.api.RolesApi;
import com.vmware.vcloud.rest.openapi.api.UserApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.Org;
import com.vmware.vcloud.rest.openapi.model.Role;
import com.vmware.vcloud.rest.openapi.model.Roles;
import com.vmware.vcloud.rest.openapi.model.VcdUser;

import static com.vmware.vcloud.api.rest.constants.RelationType.ADD;
import static com.vmware.vcloud.api.rest.constants.RestAdminConstants.MediaType.USERM;

/**
 * Tenant Manager IDP user import example that details how to import users from LDAP, OIDC, and SAML.
 */
public class TmImportIdpUserExample {

    // LDAP Server VARS
    public static final String LDAP_SERVER_BASE_DN = "dc=vsphere,dc=local";
    private static final URI LDAP_SERVER_URI = URI.create("lvn-epc-dev-181.lvn.broadcom.net");
    private static final String LDAP_SERVER_USER_DN = "cn=Administrator,cn=Users,dc=vsphere,dc=local";
    private static final String LDAP_SERVER_PASSWORD = "Welcome@123";
    private static final String LDAP_TYPE_SIMPLE = "SIMPLE";
    private static final String ACTIVE_DIRECTORY_CONNECTOR_TYPE = "ACTIVE_DIRECTORY";
    private static final String LDAP_IMPORT_USERNAME = "Administrator";


    private static final int ORG_TASK_TIMEOUT_MILLIS = 10_000;
    private static final String SYSTEM_ORG_ID = "urn:vcloud:org:a93c9db9-7471-3192-8d09-a8f7eeda85f9";
    private static final String EXAMPLE_ORG_NAME = "exampleOrg";
    private static final String EXAMPLE_ORG_DESC = "An example organization.";
    private static final String EXAMPLE_ORG_DISPLAY_NAME = "EXAMPLE_ORG";
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

        // A separate org type is used to configure IDPs.
        final AdminOrgType adminOrg = getAdminOrgFromOrgName(createdOrg.getName());


        /*
         * Configure the IDP in VCFA.
         * --------------------------
         * This example will configure all three IDPs to demonstrate user import from each.
         * Implement the methods below as needed.
         */
        configureLdap(adminOrg);  // See LDAP VARS for LDAP configuration example values.
//        configureOidc();            // See OIDC VARS for LDAP configuration example values.
//        configureSaml();            // See SAML VARS for LDAP configuration example values.

        final VcdUser ldapImportedUser = importIdpUser(LDAP_IMPORT_USERNAME, orgAdminRole, "LDAP");
        // final VcdUser oidcImportedUser = importIdpUser(LDAP_IMPORT_USERNAME, orgAdminRole, "OIDC");
        // final VcdUser samlImportedUser = importIdpUser(LDAP_IMPORT_USERNAME, orgAdminRole, "SAML");

        System.out.printf("Imported LDAP user %s in org %s: %s%n", ldapImportedUser.getUsername(), createdOrg.getName(), ldapImportedUser);
        // System.out.printf("Imported OIDC user %s in org %s: %s%n", oidcImportedUser.getUsername(), createdOrg.getName(), oidcImportedUser);
        // System.out.printf("Imported SAML user %s in org %s: %s%n", samlImportedUser.getUsername(), createdOrg.getName(), samlImportedUser);

        // Reset tenant context to the System org.
        openApiClient.setTenantContextHeader(SYSTEM_ORG_ID);

        // Confirm the new org and user can be fetched
        final Org foundOrg = orgsApi.getOrg(createdOrg.getId());
        System.out.println("Found org: " + foundOrg);

        final VcdUser foundLdapUser = userApi.getUser(ldapImportedUser.getId());
        System.out.println("Found LDAP user: " + foundLdapUser);
        // final VcdUser foundOidcUser = userApi.getUser(oidcImportedUser.getId());
        // System.out.println("Found OIDC user: " + foundOidcUser);
        // final VcdUser foundSamlUser = userApi.getUser(samlImportedUser.getId());
        // System.out.println("Found SAML user: " + foundSamlUser);
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

    private static Role getRoleWithName(String roleName) {
        final String roleNameFilter = "name==" + roleName;
        final Roles foundRoles = rolesApi.queryTenantRoles(1, 1, roleNameFilter,null, null);
        if (foundRoles.getResultTotal() == 0) {
            throw new IllegalStateException("Unable to fetch role " + roleName);
        }
        return foundRoles.getValues().get(0);
    }

    private static VcdUser importIdpUser(String name, String password, String providerType,
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

    public static void configureLdap(OrgType orgType) {
        AdminOrgType adminOrg =
                orgType instanceof AdminOrgType ? (AdminOrgType) orgType : client.getResource(
                        orgType, RelationType.ALTERNATE,
                        RestConstants.MediaType.ADMIN_ORGANIZATION, AdminOrgType.class);
        OrgLdapSettingsType orgLdapSettings = adminOrg.getSettings().getOrgLdapSettings();
        orgLdapSettings.setOrgLdapMode("CUSTOM");
        CustomOrgLdapSettingsType customOrgLdapSettings =
                client.getVCloudObjectFactory().createCustomOrgLdapSettingsType();
        customOrgLdapSettings.setHostName(LDAP_SERVER_URI.getHost());
        customOrgLdapSettings.setPort(LDAP_SERVER_URI.getPort());
        customOrgLdapSettings.setSearchBase(LDAP_SERVER_BASE_DN);
        customOrgLdapSettings.setUserName(LDAP_SERVER_USER_DN);
        customOrgLdapSettings.setPassword(LDAP_SERVER_PASSWORD);
        orgLdapSettings.setCustomOrgLdapSettings(customOrgLdapSettings);
        customOrgLdapSettings.setAuthenticationMechanism(LDAP_TYPE_SIMPLE);

        customOrgLdapSettings.setConnectorType(ACTIVE_DIRECTORY_CONNECTOR_TYPE);

        customOrgLdapSettings.setUserAttributes(OrgLdapConfigUtil.getDefaultUserAttributes());
        customOrgLdapSettings.setGroupAttributes(OrgLdapConfigUtil.getDefaultGroupAttributes());

        orgLdapSettings.setCustomOrgLdapSettings(customOrgLdapSettings);
        orgLdapSettings.setCustomUsersOu(LDAP_SERVER_BASE_DN);
        client.putResource(
                RestAdminConstants.MediaType.ORGANIZATION_LDAP_SETTINGSM, client
                        .getVCloudObjectFactory().createOrgLdapSettings(orgLdapSettings),
                OrgLdapSettingsType.class);
    }

    public static AdminOrgType getAdminOrgFromOrgName(String orgName) {
        final List<ReferenceType> orgRefList = client.getOrganizations();
        for (ReferenceType orgRef : orgRefList) {
            if (orgRef.getName().equals(orgName)) {
                OrgType orgType = client.getResource(orgRef, OrgType.class);
                return client.getResource(orgType, RelationType.ALTERNATE,
                        RestAdminConstants.MediaType.ORGANIZATIONM, AdminOrgType.class);
            }
        }
        return null;
    }

    private static VcdUser importIdpUser(String username, Role role, String providerType) {
        final VcdUser user = buildVcdUser(username, LOCAL_PROVIDER_TYPE, role);
        return userApi.createUser(user);
    }

    private static VcdUser buildVcdUser(String username, String providerType, Role role) {
        final EntityReference roleEntityReference = new EntityReference().name(role.getName()).id(role.getId());
        final List<EntityReference> roleEntityRefList =
                roleEntityReference == null ? Collections.emptyList() : Collections.singletonList(roleEntityReference);
        return new VcdUser()
                .username(username)
                .providerType(providerType)
                .roleEntityRefs(roleEntityRefList);
    }
}
