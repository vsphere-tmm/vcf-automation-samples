/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.BadRequestException;
import javax.xml.bind.JAXBElement;

import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcfa.util.CertificateUtil;
import com.vmware.vcloud.api.rest.client.OpenApiClient;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;
import com.vmware.vcloud.api.rest.client.VcdUtils;
import com.vmware.vcloud.api.rest.constants.RelationType;
import com.vmware.vcloud.api.rest.constants.RestAdminConstants;
import com.vmware.vcloud.api.rest.schema_v1_5.AdminOrgType;
import com.vmware.vcloud.api.rest.schema_v1_5.CustomOrgLdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.LinkType;
import com.vmware.vcloud.api.rest.schema_v1_5.ObjectFactory;
import com.vmware.vcloud.api.rest.schema_v1_5.OpenIdProviderConfigurationType;
import com.vmware.vcloud.api.rest.schema_v1_5.OpenIdProviderInfoType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgFederationSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapGroupAttributesType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapUserAttributesType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgOAuthSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgType;
import com.vmware.vcloud.api.rest.schema_v1_5.ReferenceType;
import com.vmware.vcloud.api.rest.schema_v1_5.TaskType;
import com.vmware.vcloud.rest.openapi.api.OrgApi;
import com.vmware.vcloud.rest.openapi.api.RolesApi;
import com.vmware.vcloud.rest.openapi.api.UserApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.Org;
import com.vmware.vcloud.rest.openapi.model.Role;
import com.vmware.vcloud.rest.openapi.model.Roles;
import com.vmware.vcloud.rest.openapi.model.VcdUser;

/**
 * Tenant Manager IDP user import example that details how to import users from LDAP, OIDC, and SAML.
 */
public class TmImportIdpUserExample {

    // LDAP CONFIG VARS
    public static final String LDAP_SERVER_BASE_DN = "dc=vsphere,dc=local";
    private static final URI LDAP_SERVER_URI = URI.create("lvn-epc-dev-181.lvn.broadcom.net");
    private static final String LDAP_SERVER_USER_DN = "cn=Administrator,cn=Users,dc=vsphere,dc=local";
    private static final String LDAP_SERVER_PASSWORD = "Welcome@123";
    private static final String LDAP_TYPE_SIMPLE = "SIMPLE";
    private static final String ACTIVE_DIRECTORY_CONNECTOR_TYPE = "ACTIVE_DIRECTORY";
    private static final String LDAP_IMPORT_USERNAME = "Administrator";

    // OIDC CONFIG VARS
    private static final String OIDC_IMPORT_USERNAME = "brianh1_test";
    private static final URI OIDC_PROVIDER_CONFIG_ENDPOINT = URI.create("https://ip-205.net-101.vm.sof-mbu.broadcom.net/SAAS/auth/.well-known/openid-configuration");
    private static final String OIDC_CLIENT_ID = "brianh1_oidc_client";
    private static final String OIDC_CLIENT_SECRET = "w3biMyWpuL01EKV27duWEHQzFNUNLU6b0Wpd0EdKqGzNeeyF";

    // SAML CONFIG VARS
    private static final String SAML_IMPORT_USERNAME = "Administrator";
    private static final String SAML_METADATA_URL_STRING = "https://172.20.32.194/websso/SAML2/Metadata/vsphere.local";

    // ORG + ROLE VARS
    private static final int ORG_TASK_TIMEOUT_MILLIS = 10_000;
    private static final String SYSTEM_ORG_ID = "urn:vcloud:org:a93c9db9-7471-3192-8d09-a8f7eeda85f9";
    private static final String EXAMPLE_ORG_NAME = "exampleOrg";
    private static final String EXAMPLE_ORG_DESC = "An example organization.";
    private static final String EXAMPLE_ORG_DISPLAY_NAME = "EXAMPLE_ORG";
    private static final String ORG_ADMIN_ROLE_NAME = "Organization Administrator";

    // APIS + SECURITY CONTEXT
    private static VcdClientImpl vcdClient;
    private static OpenApiClient openApiClient;
    private static OrgApi orgsApi = null;
    private static RolesApi rolesApi = null;
    private static UserApi userApi = null;
    private static CxfClientSecurityContext securityContext;

    public static void main(String[] args) throws Exception {
        // Sets up the keystore, VCD Client, and API Clients
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
        configureLdapInOrg(adminOrg);  // See "LDAP CONFIG VARS" [Line 50] for LDAP configuration values.
        configureOidcInOrg(adminOrg);  // See "OIDC CONFIG VARS" [Line 70] for OIDC configuration values.
        configureSamlInOrg(adminOrg);  // See "SAML CONFIG VARS" [Line 76] for SAML configuration values.

        final VcdUser ldapImportedUser = importIdpUser(LDAP_IMPORT_USERNAME, orgAdminRole, "LDAP");

        final VcdUser oidcImportedUser = importIdpUser(OIDC_IMPORT_USERNAME, orgAdminRole, "OIDC");
        final VcdUser samlImportedUser = importIdpUser(SAML_IMPORT_USERNAME, orgAdminRole, "SAML");

        System.out.printf("Imported LDAP user %s in org %s: %s%n", ldapImportedUser.getUsername(), createdOrg.getName(), ldapImportedUser);
        System.out.printf("Imported OIDC user %s in org %s: %s%n", oidcImportedUser.getUsername(), createdOrg.getName(), oidcImportedUser);
        System.out.printf("Imported SAML user %s in org %s: %s%n", samlImportedUser.getUsername(), createdOrg.getName(), samlImportedUser);

        // Reset tenant context to the System org.
        openApiClient.setTenantContextHeader(SYSTEM_ORG_ID);

        // Confirm the new org and user can be fetched
        final Org foundOrg = orgsApi.getOrg(createdOrg.getId());
        System.out.println("Found org: " + foundOrg);

        final VcdUser foundLdapUser = userApi.getUser(ldapImportedUser.getId());
        System.out.println("Found LDAP user: " + foundLdapUser);
        final VcdUser foundOidcUser = userApi.getUser(oidcImportedUser.getId());
        System.out.println("Found OIDC user: " + foundOidcUser);
        final VcdUser foundSamlUser = userApi.getUser(samlImportedUser.getId());
        System.out.println("Found SAML user: " + foundSamlUser);
    }

    // SETUP
    // --------------------------------------------------
    private static void setup() throws Exception{
        final KeyStore truststore = getKeyStore();
        securityContext = CxfClientSecurityContext.getCxfClientSecurityContext(null, null, truststore, null, false);

        System.out.println("Using rest-api-client-1.0.0...");

        openApiClient = getVcdClient().getOpenApiClient();
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

    private static VcdClient getVcdClient() {
        if (vcdClient != null) {
            return vcdClient;
        }

        final String serverUrl = SettingsLoader.getProviderConfig().get(Constants.SERVER_URL);
        final String serverVersion = SettingsLoader.getProviderConfig().get(Constants.SERVER_VERSION);
        final String username = SettingsLoader.getProviderConfig().get(Constants.AUTH_USERNAME);
        final String tenant = SettingsLoader.getProviderConfig().get(Constants.AUTH_TENANT);
        final String password = SettingsLoader.getProviderConfig().get(Constants.AUTH_PASSWORD);

        vcdClient = new VcdClientImpl(URI.create(serverUrl), serverVersion, securityContext);
        vcdClient.setCredentials(new VcdBasicLoginCredentials(username, tenant, password));

        return vcdClient;
    }

    // ORGANIZATIONS
    // --------------------------------------------------
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
            final TaskType taskType = vcdClient.getTaskMonitor().waitForSuccess(updateTaskLink, ORG_TASK_TIMEOUT_MILLIS);
            return orgsApi.getOrg(taskType.getOwner().getId());
        } catch (TimeoutException e) {
            throw new RuntimeException("Failed to wait for result of org update task.", e);
        }
    }

    public static AdminOrgType getAdminOrgFromOrgName(String orgName) {
        final List<ReferenceType> orgRefList = vcdClient.getOrganizations();
        for (ReferenceType orgRef : orgRefList) {
            if (orgRef.getName().equals(orgName)) {
                OrgType orgType = vcdClient.getResource(orgRef, OrgType.class);
                return vcdClient.getResource(orgType, RelationType.ALTERNATE,
                        RestAdminConstants.MediaType.ORGANIZATIONM, AdminOrgType.class);
            }
        }
        return null;
    }

    // USERS AND ROLES
    // --------------------------------------------------
    public static VcdUser importIdpUser(String username, Role role, String providerType) {
        final VcdUser user = buildVcdUser(username, providerType, role);
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

    public static Role getRoleWithName(String roleName) {
        final String roleNameFilter = "name==" + roleName;
        final Roles foundRoles = rolesApi.queryTenantRoles(1, 1, roleNameFilter,null, null);
        if (foundRoles.getResultTotal() == 0) {
            throw new IllegalStateException("Unable to fetch role " + roleName);
        }
        return foundRoles.getValues().get(0);
    }

    // LDAP CONFIGURATION
    // --------------------------------------------------

    /**
     * Configures the desired LDAP server in VCFA.
     * See "LDAP Server VARS" [Line 50] for variable definitions.
     */
    public static void configureLdapInOrg(AdminOrgType adminOrg) {
        // Set connection variables for LDAP server
        CustomOrgLdapSettingsType customOrgLdapSettings = vcdClient.getVCloudObjectFactory().createCustomOrgLdapSettingsType();
        customOrgLdapSettings.setHostName(LDAP_SERVER_URI.getHost());
        customOrgLdapSettings.setPort(LDAP_SERVER_URI.getPort());
        customOrgLdapSettings.setSearchBase(LDAP_SERVER_BASE_DN);
        customOrgLdapSettings.setConnectorType(ACTIVE_DIRECTORY_CONNECTOR_TYPE);
        customOrgLdapSettings.setIsSsl(false);
        customOrgLdapSettings.setAuthenticationMechanism(LDAP_TYPE_SIMPLE);
        customOrgLdapSettings.setUserName(LDAP_SERVER_USER_DN);  // Set username and password to blank for anonymous read
        customOrgLdapSettings.setPassword(LDAP_SERVER_PASSWORD); // (As above)

        // Set user and group attributes
        customOrgLdapSettings.setUserAttributes(getDefaultUserAttributes());
        customOrgLdapSettings.setGroupAttributes(getDefaultGroupAttributes());

        // Configure org settings to use custom LDAP settings.
        final OrgLdapSettingsType orgLdapSettings = adminOrg.getSettings().getOrgLdapSettings();
        orgLdapSettings.setOrgLdapMode("CUSTOM");
        orgLdapSettings.setCustomOrgLdapSettings(customOrgLdapSettings);
        orgLdapSettings.setCustomUsersOu(LDAP_SERVER_BASE_DN);

        // Update the org LDAP settings with custom configuration.
        final OrgLdapSettingsType updatedLdapSettings = vcdClient.putResource(
                RestAdminConstants.MediaType.ORGANIZATION_LDAP_SETTINGSM, vcdClient
                        .getVCloudObjectFactory().createOrgLdapSettings(orgLdapSettings),
                OrgLdapSettingsType.class);
        System.out.printf("Configured LDAP for org %s: %s", adminOrg.getName(), updatedLdapSettings);

    }

    /**
     * Sets default user attributes. Substitute as necessary.
     */
    private static OrgLdapUserAttributesType getDefaultUserAttributes() {
        final OrgLdapUserAttributesType orgLdapUserAttributesType = new OrgLdapUserAttributesType();
        orgLdapUserAttributesType.setObjectClass("user");
        orgLdapUserAttributesType.setObjectIdentifier("objectGuid");
        orgLdapUserAttributesType.setUserName("sAMAccountName");
        orgLdapUserAttributesType.setEmail("mail");
        orgLdapUserAttributesType.setFullName("displayName");
        orgLdapUserAttributesType.setGivenName("givenName");
        orgLdapUserAttributesType.setTelephone("telephoneNumber");
        orgLdapUserAttributesType.setSurname("sn");
        orgLdapUserAttributesType.setGroupMembershipIdentifier("dn");
        orgLdapUserAttributesType.setGroupBackLinkIdentifier(null); // Optional
        return orgLdapUserAttributesType;
    }

    /**
     * Sets default group attributes. Substitute as necessary.
     */
    private static OrgLdapGroupAttributesType getDefaultGroupAttributes() {
        final OrgLdapGroupAttributesType orgLdapGroupAttributesType = new OrgLdapGroupAttributesType();
        orgLdapGroupAttributesType.setObjectClass("group");
        orgLdapGroupAttributesType.setObjectIdentifier("objectGuid");
        orgLdapGroupAttributesType.setGroupName("cn");
        orgLdapGroupAttributesType.setMembership("member");
        orgLdapGroupAttributesType.setMembershipIdentifier("dn");
        orgLdapGroupAttributesType.setBackLinkIdentifier("objectSid");
        orgLdapGroupAttributesType.setBackLinkIdentifier(null); // Optional
        return orgLdapGroupAttributesType;
    }

    // OIDC CONFIGURATION
    // --------------------------------------------------
    public static void configureOidcInOrg(AdminOrgType adminOrg) {
        // Retrieve org OAuth settings.
        final OrgSettingsType orgSettings = adminOrg.getSettings();
        final OrgOAuthSettingsType oAuthSettingsType = orgSettings.getOrgOAuthSettings();
        final LinkType providerConfigLink =
                VcdUtils.findLink(oAuthSettingsType.getLink(), RestAdminConstants.MediaType.OPENID_PROVIDER_CONFIG);

        // Configure OpenID Provider configuration using a well-known configuration endpoint.
        final OpenIdProviderInfoType providerInfo = new OpenIdProviderInfoType();
        providerInfo.setOpenIdProviderConfigurationEndpoint(OIDC_PROVIDER_CONFIG_ENDPOINT.toString());
        final ObjectFactory objectFactory = vcdClient.getVCloudObjectFactory();
        final OpenIdProviderConfigurationType providerConfig = vcdClient.postResource(
                URI.create(providerConfigLink.getHref()), RestAdminConstants.MediaType.OPENID_PROVIDER_INFO,
                objectFactory.createOpenIdProviderInfo(providerInfo),
                OpenIdProviderConfigurationType.class);

        // Configure the OIDC client.
        final OrgOAuthSettingsType orgOAuthSettings = providerConfig.getOrgOAuthSettings();
        orgOAuthSettings.setClientId(OIDC_CLIENT_ID);
        orgOAuthSettings.setClientSecret(OIDC_CLIENT_SECRET);
        orgOAuthSettings.setEnabled(true);
        orgOAuthSettings.setMaxClockSkew(120);

        // Configure OIDC attribute mappings.
        orgOAuthSettings.getOIDCAttributeMapping().setGroupsAttributeName("groups");
        orgOAuthSettings.getOIDCAttributeMapping().setRolesAttributeName("roles");
        orgOAuthSettings.getOIDCAttributeMapping().setSubjectAttributeName("email");
        orgOAuthSettings.getOIDCAttributeMapping().setNameInSourceAttributeName("email");

        // Update org OAuth settings with configured values.
        updateOAuthSettings(adminOrg, orgOAuthSettings);
    }

    private static void updateOAuthSettings(AdminOrgType adminOrg, OrgOAuthSettingsType settings) {
        // Retrieve VCFA OAuth settings.
        final JAXBElement<OrgOAuthSettingsType> jabxOrgOAuthSettings =
                vcdClient.getVCloudObjectFactory().createOrgOAuthSettings(settings);

        // Update the OAuth settings with custom settings.
        final String settingsHref = settings.getHref();
        final OrgOAuthSettingsType updatedOauthSettings;
        if (settingsHref != null) {
            updatedOauthSettings = vcdClient.putResource(URI.create(settingsHref), RestAdminConstants.MediaType.ORGANIZATION_OAUTH_SETTINGSM,
                    jabxOrgOAuthSettings, OrgOAuthSettingsType.class);
        } else {
            updatedOauthSettings = vcdClient.putResource(adminOrg.getSettings(), RelationType.DOWN,
                    RestAdminConstants.MediaType.ORGANIZATION_OAUTH_SETTINGSM, jabxOrgOAuthSettings, OrgOAuthSettingsType.class);
        }
        System.out.printf("Configured OIDC for org %s: %s", adminOrg.getName(), updatedOauthSettings);
    }

    // SAML CONFIGURATION
    // --------------------------------------------------
    public static void configureSamlInOrg(final AdminOrgType adminOrg) {
        try {
            // Download SAML IDP metadata to configure within VCFA.
            // ### SHOULD WE BE DOWNLOADING THE METADATA OR ASSUME THE USER HAS IT LOCALLY? THIS ADDS MORE COMPLEXITY TO THE EXAMPLE ###
            final URL samlMetadataUri = new URL(SAML_METADATA_URL_STRING);
            final String ipSamlMetadata =
                    downloadMetadata(samlMetadataUri);

            // Update SAML settings with downloaded metadata.
            updateOrgFederationSettings(ipSamlMetadata, true, adminOrg);

        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private static void updateOrgFederationSettings(final String ipSamlMetadata,
                                             final boolean enableFederation,
                                             final AdminOrgType adminOrg) {
        final OrgFederationSettingsType existingOrgFederationSettings =
                adminOrg.getSettings().getOrgFederationSettings();

        // Setup basic SAML settings for the org.
        final ObjectFactory objectFactory = vcdClient.getVCloudObjectFactory();
        final OrgFederationSettingsType federationSettingsType =
                objectFactory.createOrgFederationSettingsType();
        federationSettingsType.setEnabled(enableFederation);
        federationSettingsType.setSAMLMetadata(ipSamlMetadata);

        // Configure certificates.
        federationSettingsType
                .setSigningCertLibraryItemId(existingOrgFederationSettings.getSigningCertLibraryItemId());
        federationSettingsType.setEncryptionCertLibraryItemId(
                existingOrgFederationSettings.getEncryptionCertLibraryItemId());

        final JAXBElement<OrgFederationSettingsType> federationSettings =
                objectFactory.createOrgFederationSettings(federationSettingsType);

        // Update the SAML settings.
        final OrgFederationSettingsType updatedFederationSettings =
                vcdClient.putResource(existingOrgFederationSettings, RelationType.EDIT,
                        RestAdminConstants.MediaType.ORGANIZATION_FEDERATION_SETTINGSM,
                        federationSettings, OrgFederationSettingsType.class);
        System.out.printf("Configured SAML for org %s: %s", adminOrg.getName(), updatedFederationSettings);
    }

    /**
     * UNSURE IF DOWNLOADING THE METADATA IS STRICTLY NECESSARY FOR THE BASE EXAMPLE.
     * PERHAPS THE EXAMPLE SHOULD ASSUME THE USER HAS THEIR SAML METADATA XML LOCALLY DOWNLOADED.
     * --------------------------------------------------------------------------------------------------------
     */
    private static String downloadMetadata(final URL samlMetadataUri) throws Exception {
        final HttpsURLConnection connection = (HttpsURLConnection)samlMetadataUri.openConnection();
        connection.setHostnameVerifier((arg0, arg1) -> true);
        connection.setSSLSocketFactory(getPermissiveSSLSocketFactory());
        connection.connect();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        final StringWriter sw = new StringWriter();
        for ( ; ; ) {
            final int character = reader.read();
            if (character == -1) {
                break;
            }
            sw.write(character);
        }

        sw.close();
        reader.close();
        connection.disconnect();

        return sw.toString();
    }

    private static SSLSocketFactory getPermissiveSSLSocketFactory() throws Exception {
        final SSLContext permissiveContext = SSLContext.getInstance("TLS");
        permissiveContext.init(null,
                new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
                        // Do nothing
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
                        // Do nothing
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }},
                new SecureRandom());

        return permissiveContext.getSocketFactory();
    }
    /**
     * --------------------------------------------------------------------------------------------------------
     * UNSURE IF DOWNLOADING THE METADATA IS STRICTLY NECESSARY FOR THE BASE EXAMPLE.
     * PERHAPS THE EXAMPLE SHOULD ASSUME THE USER HAS THEIR SAML METADATA XML LOCALLY DOWNLOADED.
     */
}
