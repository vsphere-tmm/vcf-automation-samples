/*
 * ******************************************************************
 * Copyright (c) 2025 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */
package com.vmware.vcf;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

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
import com.vmware.vcloud.rest.openapi.api.OrgApi;
import com.vmware.vcloud.rest.openapi.api.RolesApi;
import com.vmware.vcloud.rest.openapi.api.TrustedCertificatesApi;
import com.vmware.vcloud.rest.openapi.api.UserApi;
import com.vmware.vcloud.rest.openapi.model.EntityReference;
import com.vmware.vcloud.rest.openapi.model.Org;
import com.vmware.vcloud.rest.openapi.model.Role;
import com.vmware.vcloud.rest.openapi.model.Roles;
import com.vmware.vcloud.rest.openapi.model.TrustedCertificate;
import com.vmware.vcloud.rest.openapi.model.VcdUser;

/**
 * Tenant Manager IDP user import example that details how to import users from LDAP, OIDC, and SAML.
 * --------------------------------------------------
 * This example contains the necessary code for configuring LDAP, OIDC, and SAML, as well as importing
 * a user from each. This can be extended as needed for specific use-cases.
 * The sample code assumes an org has already been created.
 * The SAML case specifically requires the SAML IDP to be set up to the point that a metadata XML file
 * can be configured inside VCFA.
 * The process for trusting IDP certificates is also detailed in the event that certs are not
 * well-signed or have yet to be trusted in VCFA.
 */
public class TmImportIdpUserExample {

    /*
     * Many of these variables, especially relating to specific IDP config values and
     * certs will need to be filled in with relevant data. This example will not function
     * properly without relevant configuration information.
     */

    // LDAP CONFIG VARS
    public static final String LDAP_SERVER_BASE_DN = "dc=vsphere,dc=local";
    private static final String LDAP_SERVER_HOST = "examplLdapServer.ldap.com";
    private static final int LDAP_SERVER_PORT = 389;
    private static final String LDAP_SERVER_USER_DN = "cn=Administrator,cn=Users,dc=vsphere,dc=local";
    private static final String LDAP_SERVER_PASSWORD = "examplePassword";
    private static final String LDAP_TYPE_SIMPLE = "SIMPLE";
    private static final String ACTIVE_DIRECTORY_CONNECTOR_TYPE = "ACTIVE_DIRECTORY";
    private static final String LDAP_IMPORT_USERNAME = "exampleLdapUser";

    // Use the below two vars if the IDP cert is not well-signed or already trusted in VCFA.
    private static final String LDAP_CERT_ALIAS = "ldapExampleAlias";
    private static final String LDAP_CERT_VALUE =
            "-----BEGIN CERTIFICATE-----\n" +
                    "...\n" +
                    "-----END CERTIFICATE-----";

    // OIDC CONFIG VARS
    private static final String OIDC_IMPORT_USERNAME = "exampleOidcUser";
    private static final String OIDC_PROVIDER_CONFIG_ENDPOINT = "https://example-url/provider-config/openid-configuration";
    private static final String OIDC_CLIENT_ID = "exampleOidcClient";
    private static final String OIDC_CLIENT_SECRET = "abcdefghijklmnopqrstuvwxyz1234567890";

    // Use the below two vars if the IDP cert is not well-signed or already trusted in VCFA.
    private static final String OIDC_CERT_ALIAS = "oidcExampleAlias";
    private static final String OIDC_CERT_VALUE =
            "-----BEGIN CERTIFICATE-----\n" +
                    "...\n" +
                    "-----END CERTIFICATE-----";

    // SAML CONFIG VARS
    private static final String SAML_IMPORT_USERNAME = "exampleSamlUser";
    private static final String SAML_CONFIG_ENTITY_ID = "exampleEntityId";
    private static final String SAML_METADATA_XML_PATH = "/path/to/saml_metadata.xml";
    // Use the below two vars if the IDP cert is not well-signed or already trusted in VCFA.
    private static final String SAML_CERT_ALIAS = "samlExampleAlias";
    private static final String SAML_CERT_VALUE =
            "-----BEGIN CERTIFICATE-----\n" +
                    "...\n" +
                    "-----END CERTIFICATE-----";

    // ORG + ROLE VARS
    private static final String SYSTEM_ORG_ID = "urn:vcloud:org:a93c9db9-7471-3192-8d09-a8f7eeda85f9";
    private static final String EXAMPLE_ORG_URN = "urn:vcloud:org:ba12d5cb-07ae-4050-b462-6fb3e38c8861";
    private static final String ORG_ADMIN_ROLE_NAME = "Organization Administrator";

    // APIS + SECURITY CONTEXT
    private static KeyStore truststore;
    private static VcdClientImpl vcdClient;
    private static OpenApiClient openApiClient;
    private static OrgApi orgsApi = null;
    private static RolesApi rolesApi = null;
    private static UserApi userApi = null;
    private static TrustedCertificatesApi trustedCertificateApi = null;
    private static CxfClientSecurityContext securityContext;

    public static void main(String[] args) throws Exception {
        // Sets up the keystore, VCD Client, and API Clients
        setup();

        /*
         * This example assumes you have already created an organization in which the IDPs will be configured.
         * Please follow the relevant org creation example to create an org and first local user.
         */
        final Org org = orgsApi.getOrg(EXAMPLE_ORG_URN);

        System.out.printf("Retrieved org %s: %s%n", org.getName(), org);

        // Set the openAPI client tenant context to the created org ID to perform actions in that org.
        openApiClient.setTenantContextHeader(org.getId());
        final Role orgAdminRole = getRoleWithName(ORG_ADMIN_ROLE_NAME);

        // A separate org type is used to configure IDPs.
        final AdminOrgType adminOrg = getAdminOrgFromOrgName(org.getName());
        /*
         * Configure the IDP in VCFA.
         * --------------------------
         * This example will configure all three IDPs to demonstrate user import from each.
         * Implement the methods below as needed.
         *
         * Specific information is contained in the relevant configuration methods for each IDP type
         */
        configureLdapInOrg(adminOrg);  // See "LDAP CONFIG VARS" [Line 68] for LDAP configuration values.
        configureOidcInOrg(adminOrg);  // See "OIDC CONFIG VARS" [Line 85] for OIDC configuration values.
        configureSamlInOrg(adminOrg);  // See "SAML CONFIG VARS" [Line 98] for SAML configuration values.

        final VcdUser ldapImportedUser = importIdpUser(LDAP_IMPORT_USERNAME, orgAdminRole, "LDAP");
        final VcdUser oidcImportedUser = importIdpUser(OIDC_IMPORT_USERNAME, orgAdminRole, "OAUTH");
        final VcdUser samlImportedUser = importIdpUser(SAML_IMPORT_USERNAME, orgAdminRole, "SAML");

        System.out.printf("Imported LDAP user %s in org %s: %s%n", ldapImportedUser.getUsername(), org.getName(), ldapImportedUser);
        System.out.printf("Imported OIDC user %s in org %s: %s%n", oidcImportedUser.getUsername(), org.getName(), oidcImportedUser);
        System.out.printf("Imported SAML user %s in org %s: %s%n", samlImportedUser.getUsername(), org.getName(), samlImportedUser);

        // Reset tenant context to the System org.
        openApiClient.setTenantContextHeader(SYSTEM_ORG_ID);

        final VcdUser foundLdapUser = userApi.getUser(ldapImportedUser.getId());
        System.out.println("Found LDAP user: " + foundLdapUser);
        final VcdUser foundOidcUser = userApi.getUser(oidcImportedUser.getId());
        System.out.println("Found OIDC user: " + foundOidcUser);
        final VcdUser foundSamlUser = userApi.getUser(samlImportedUser.getId());
        System.out.println("Found SAML user: " + foundSamlUser);
    }

    // SETUP
    // --------------------------------------------------
    private static void setup() throws Exception {
        truststore = getKeyStore();
        securityContext = CxfClientSecurityContext.getCxfClientSecurityContext(null, null, truststore, null, false);

        System.out.println("Using rest-api-client-1.0.0...");

        openApiClient = getVcdClient().getOpenApiClient();
        orgsApi = openApiClient.createProxy(OrgApi.class);
        rolesApi = openApiClient.createProxy(RolesApi.class);
        userApi = openApiClient.createProxy(UserApi.class);
        trustedCertificateApi = openApiClient.createProxy(TrustedCertificatesApi.class);
    }

    /*
     * Only needed if your TM instance is not using a well-signed certificate.
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
        final Roles foundRoles = rolesApi.queryTenantRoles(1, 1, roleNameFilter, null, null);
        if (foundRoles.getResultTotal() == 0) {
            throw new IllegalStateException("Unable to fetch role " + roleName);
        }
        return foundRoles.getValues().get(0);
    }

    // IDP CERTS
    // --------------------------------------------------
    public static void trustIdpCertificate(final String alias, final String cert) {
        final TrustedCertificate trustedCertificate = new TrustedCertificate();
        trustedCertificate.alias(alias).certificate(cert);
        trustedCertificateApi.trustCertificate(trustedCertificate);
    }

    // LDAP CONFIGURATION
    // --------------------------------------------------

    /**
     * Configures the desired LDAP server in VCFA.
     * See "LDAP CONFIG VARS" [Line 68] for variable definitions.
     */
    public static void configureLdapInOrg(AdminOrgType adminOrg) {
        // Trust the IDP certificate. This is only necessary if the
        // LDAP instance does not have a well-signed cert.
        trustIdpCertificate(LDAP_CERT_ALIAS, LDAP_CERT_VALUE);

        // Set connection variables for LDAP server
        CustomOrgLdapSettingsType customOrgLdapSettings = vcdClient.getVCloudObjectFactory().createCustomOrgLdapSettingsType();
        customOrgLdapSettings.setHostName(LDAP_SERVER_HOST);
        customOrgLdapSettings.setPort(LDAP_SERVER_PORT);
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
    /**
     * Configures the desired OIDC server in VCFA.
     * See "OIDC CONFIG VARS" [Line 85] for variable definitions.
     */
    public static void configureOidcInOrg(AdminOrgType adminOrg) {
        // Trust the IDP certificate. This is only necessary if the
        // OIDC instance does not have a well-signed cert.
        trustIdpCertificate(OIDC_CERT_ALIAS, OIDC_CERT_VALUE);

        // Retrieve org OAuth settings.
        final OrgSettingsType orgSettings = adminOrg.getSettings();
        final OrgOAuthSettingsType oAuthSettingsType = orgSettings.getOrgOAuthSettings();
        final LinkType providerConfigLink =
                VcdUtils.findLink(oAuthSettingsType.getLink(), RestAdminConstants.MediaType.OPENID_PROVIDER_CONFIG);

        // Configure OpenID Provider configuration using a well-known configuration endpoint.
        final OpenIdProviderInfoType providerInfo = new OpenIdProviderInfoType();
        providerInfo.setOpenIdProviderConfigurationEndpoint(OIDC_PROVIDER_CONFIG_ENDPOINT);
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

    /**
     * Updates VCFA OAuth Settings
     */
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
    /**
     * Configures the desired SAML server in VCFA.
     * See "SAML CONFIG VARS" [Line 98] for variable definitions.
     * --------------------------------------------------
     * NOTE: Configuring SAML assumes that the IDP has been configured (both in the org and on the IDP itself)
     * to the point that SAML metadata from the IDP has been successfully stored locally for retrieval.
     * This must be done before attempting the remaining configuration steps necessary to import a SAML user.
     */
    public static void configureSamlInOrg(final AdminOrgType adminOrg) throws IOException {
        // Trust the IDP certificate. This is only necessary if the
        // SAML instance does not have a well-signed cert.
        trustIdpCertificate(SAML_CERT_ALIAS, SAML_CERT_VALUE);

        // Retrieve SAML metadata XML from local file.
        final String ipSamlMetadata = Files.readString(Paths.get(SAML_METADATA_XML_PATH));

        // Update SAML settings with downloaded metadata.
        updateOrgFederationSettings(ipSamlMetadata, true, adminOrg);
    }

    /**
     * Updates VCFA SAML settings.
     */
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
        final OrgFederationSettingsType federationSettingsValue = federationSettings.getValue();
        federationSettingsValue.setSamlSPEntityId(SAML_CONFIG_ENTITY_ID);
        federationSettings.getValue().setEnabled(true);

        // Update the SAML settings.
        final OrgFederationSettingsType updatedFederationSettings =
                vcdClient.putResource(existingOrgFederationSettings, RelationType.EDIT,
                        RestAdminConstants.MediaType.ORGANIZATION_FEDERATION_SETTINGSM,
                        federationSettings, OrgFederationSettingsType.class);
        System.out.printf("Configured SAML for org %s: %s", adminOrg.getName(), updatedFederationSettings);
    }
}