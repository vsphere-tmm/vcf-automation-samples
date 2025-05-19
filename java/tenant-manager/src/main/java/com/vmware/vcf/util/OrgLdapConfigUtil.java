/*
 * ******************************************************************
 * Copyright (c) 2015-2024 Broadcom. All Rights Reserved.
 * Broadcom Confidential. The term "Broadcom" refers to Broadcom Inc.
 * and/or its subsidiaries.
 * ******************************************************************
 */

package com.vmware.vcf.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapName;
import javax.xml.bind.JAXBElement;

import com.vmware.cloud.clientlibrary.Credentials;
import com.vmware.cloud.systemtests.SiteEnvironment;
import com.vmware.cloud.systemtests.infrastructure.Facade;
import com.vmware.cloud.systemtests.infrastructure.LdapServerImpl;
import com.vmware.cloud.systemtests.provisioner.infrastructure.LdapServer;
import com.vmware.cloud.systemtests.provisioner.infrastructure.LdapServer.LdapUser;
import com.vmware.cloud.systemtests.provisioner.infrastructure.Site;
import com.vmware.cloud.systemtests.provisioner.infrastructure.VcServer;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.constants.RelationType;
import com.vmware.vcloud.api.rest.constants.RestAdminConstants;
import com.vmware.vcloud.api.rest.constants.RestConstants;
import com.vmware.vcloud.api.rest.enums.IdentityProviderSourceType;
import com.vmware.vcloud.api.rest.enums.LdapAuthenticationMechanismType;
import com.vmware.vcloud.api.rest.enums.LdapConnectorType;
import com.vmware.vcloud.api.rest.enums.LdapModeType;
import com.vmware.vcloud.api.rest.schema_v1_5.AdminOrgType;
import com.vmware.vcloud.api.rest.schema_v1_5.CustomOrgLdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.GroupType;
import com.vmware.vcloud.api.rest.schema_v1_5.GroupsListType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapGroupAttributesType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgLdapUserAttributesType;
import com.vmware.vcloud.api.rest.schema_v1_5.OrgType;
import com.vmware.vcloud.api.rest.schema_v1_5.ReferenceType;
import com.vmware.vcloud.api.rest.schema_v1_5.UserType;
import com.vmware.vcloud.api.rest.schema_v1_5.extension.LdapSettingsType;
import com.vmware.vcloud.api.rest.schema_v1_5.extension.ObjectFactory;
import com.vmware.vcloud.api.rest.schema_v1_5.extension.SystemSettingsType;
import com.vmware.vcloud.rest.openapi.api.LDAPApi;
import com.vmware.vcloud.rest.openapi.model.Role;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapEncoder;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.DefaultDirObjectFactory;
import org.springframework.ldap.core.support.LdapContextSource;
import org.testng.Assert;

import static com.vmware.vcloud.api.rest.constants.RelationType.ADD;
import static com.vmware.vcloud.api.rest.constants.RestAdminConstants.MediaType.USERM;

/**
 * Provides utility methods for performing both VCD LDAP operations and also interacting directly
 * with the configured LDAP server
 */
public class OrgLdapConfigUtil {

    // Constants used for interacting with vcenter LDAP server
    public static final String BASE_DN = "dc=vsphere,dc=local";
    public static final String ADMIN_USER_KEY = "cn=Administrator,cn=Users," + BASE_DN;
    public static final int PORT = 389;
    public static final String DEFAULT_SERVER_ADMIN_NAME = "administrator";
    public static final String OU_DN_TEMPLATE = "ou=%s," + BASE_DN;
    private static final String USER_DN_TEMPLATE = "cn=%s,cn=Users," + BASE_DN;
    private static final String GROUP_DN_TEMPLATE = "cn=%s," + BASE_DN;
    private static final String ORG_USER_DN_TEMPLATE = "cn=%s,ou=%s," + BASE_DN;
    private static final String ATTRIBUTE_KEY_OBJECT_CLASS = "objectclass";
    private static final Object[] USER_OBJECT_CLASS = new String[] { "top", "person", "organizationalPerson", "user" };
    private static final Object[] GROUP_OBJECT_CLASS = new String[] { "top", "group" };
    private static final Object[] ORG_UNIT_OBJECT_CLASS = new String[] { "top", "organizationalUnit" };
    private static final Object[] ORG_PERSON_OBJECT_CLASS =
            new String[] { "top", "person", "organizationalPerson", "inetOrgPerson" };
    private static final String ATTRIBUTE_KEY_OU = "ou";
    private static final String ATTRIBUTE_KEY_SN = "sn";
    private static final String ATTRIBUTE_KEY_CN = "cn";
    private static final String ATTRIBUTE_VALUE_SN = "vsphere.local";
    public static final String ATTRIBUTE_KEY_GIVEN_NAME = "givenname";
    public static final String ATTRIBUTE_KEY_SAM_ACCOUNT_NAME = "samaccountname";
    private static final String ATTRIBUTE_KEY_ACCOUNT_CONTROL = "useraccountcontrol";
    private static final String ATTRIBUTE_ACCOUNT_CONTROL_DISABLED = "514";
    private static final String ATTRIBUTE_KEY_USER_PASSWORD = "userPassword";
    private static final String USER_SEARCH_FILTER = "(&(objectClass=user)((|(sAMAccountName=%s))))";
    private static final String GROUP_SEARCH_FILTER = "(&(objectClass=group)(|(cn=%s)))";
    private static final String ORG_SEARCH_FILTER = "(&(objectClass=organizationalUnit)(|(ou=%s)))";
    private static final String ORG_USER_SEARCH_FILTER = "(&(objectClass=inetOrgPerson)(|(cn=%s)))";

    private final VcdClient client;
    private final SiteEnvironment environment;
    private final ObjectFactory vCloudObjectFactory;
    private final LdapTemplate ldapServerTemplate;
    public final LdapServer testbedLdapServerConfig;

    private final List<String> createdGroupNames = new ArrayList<>();
    private final MultiValuedMap<AdminOrgType, String> importedGroups = new ArrayListValuedHashMap<>();
    private final List<String> createUserNames = new ArrayList<>();

    public OrgLdapConfigUtil(SiteEnvironment environment, VcdClient client) {
        this.environment = environment;
        this.client = client;
        this.vCloudObjectFactory = client.getVCloudExtensionObjectFactory();
        this.testbedLdapServerConfig = buildLdapServerConfig(environment);
        this.ldapServerTemplate = createLdapServerTemplate(testbedLdapServerConfig);
    }

    public void cleanUpCreatedGroups() throws Exception {
        for (AdminOrgType org : importedGroups.keySet()) {
            importedGroups.get(org).forEach(group -> deleteGroup(org, group));
        }
        for (String groupName : createdGroupNames) {
            removeGroupFromLdapServer(groupName);
        }
    }

    public void cleanUpCreatedUsers() throws Exception {
        for (String userName : createUserNames) {
            removeUserFromLdapServer(userName);
        }
    }

    /**
     * Build a {@link LdapServer} to be used for LDAP testing
     */
    private LdapServer buildLdapServerConfig(SiteEnvironment environment) {

        final Site site = environment.getSite();
        // If there are multiple VCenters, the first one presented gets used as the test LDAP server
        final VcServer vcenter = site.getVcServers().iterator().next();
        final LdapUser adminUser = new LdapUser(ADMIN_USER_KEY, environment.getUserPassword());
        final LdapUser vappUser = new LdapUser(ADMIN_USER_KEY, environment.getUserPassword());
        final Credentials ldapAdministratorCredentials = new Credentials(ADMIN_USER_KEY, vcenter.getSsoCredentials().getPassword());
        String ldapUri = System.getProperty(Facade.PREFIX_PROPERTY_KEY + "ldap-host-override");
        if (StringUtils.isBlank(ldapUri)) {
            ldapUri = vcenter.getHostname() + ":" + PORT;
        }
        final URI ldapURI = URI.create("ldap://" + ldapUri);
        return new LdapServerImpl(vcenter.getHostname(), ldapAdministratorCredentials, ldapURI, BASE_DN, adminUser, vappUser);
    }

    /**
     * Creates a Spring {@link LdapTemplate} for interacting directly with the configured LDAP
     * server
     */
    private LdapTemplate createLdapServerTemplate(final LdapServer testbedLdapServerConfig) {
        final LdapContextSource ldapContextSource = new LdapContextSource();
        ldapContextSource.setUrl(testbedLdapServerConfig.getLdapURI().toString());
        ldapContextSource.setUserDn(testbedLdapServerConfig.getCredentials().getUsername());
        ldapContextSource.setPassword(testbedLdapServerConfig.getCredentials().getPassword());
        ldapContextSource.setDirObjectFactory(DefaultDirObjectFactory.class);
        try {
            ldapContextSource.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception creating LDAP Spring Template", e);
        }
        return new LdapTemplate(ldapContextSource);
    }

    public LdapServer getTestbedLdapServerConfig() {
        return testbedLdapServerConfig;
    }

    public void configureSystemForLDAP() throws Exception {
        configureSystemForLDAP(this.testbedLdapServerConfig.getLdapURI().getHost(), this.testbedLdapServerConfig.getLdapURI()
                .getPort(), this.testbedLdapServerConfig.getBaseDN(), this.testbedLdapServerConfig.getCredentials().getUsername(),
                this.testbedLdapServerConfig.getCredentials().getPassword());
    }

    public void unconfigureSystemForLDAP() throws Exception {
        final SystemSettingsUtils utils = new SystemSettingsUtils(client);
        final SystemSettingsType systemSettings = utils.getSystemSettings();
        final LdapSettingsType ldapSettings = systemSettings.getLdapSettings();
        if (ldapSettings == null) {
            return;
        }
        client.deleteResource(URI.create(ldapSettings.getHref()));
    }

    public void configureSystemForLDAP(String hostname, int port, String baseDN, String username,
            String password) throws Exception {
        SystemSettingsUtils utils = new SystemSettingsUtils(client);
        SystemSettingsType systemSettings = utils.getSystemSettings();

        LdapSettingsType ldapSettings = systemSettings.getLdapSettings();
        if (ldapSettings == null) {
            ldapSettings = vCloudObjectFactory.createLdapSettingsType();
        }
        ldapSettings.setHostName(hostname);
        ldapSettings.setPort(port);
        ldapSettings.setSearchBase(baseDN);
        ldapSettings.setUserName(username);
        ldapSettings.setPassword(password);
        // The default group back link value of 'tokenGroups' needs to be removed to be compatible with vcenter LDAP server
        ldapSettings.getUserAttributes().setGroupBackLinkIdentifier(null);
        client.putResource(URI.create(ldapSettings.getHref()),
                RestAdminConstants.MediaType.LDAP_SETTINGSM,
                vCloudObjectFactory.createLdapSettings(ldapSettings));
    }

    public void updateSystemLDAPGroupBackLink(String value) throws Exception {
        final SystemSettingsUtils utils = new SystemSettingsUtils(client);
        final SystemSettingsType systemSettings = utils.getSystemSettings();
        final LdapSettingsType ldapSettings = systemSettings.getLdapSettings();
        ldapSettings.getUserAttributes().setGroupBackLinkIdentifier(value);
        systemSettings.setLdapSettings(ldapSettings);
        utils.setSystemSettings(systemSettings);
    }

    public boolean isConfiguredForLDAP(AdminOrgType orgType) {
        OrgLdapSettingsType orgLdapSettings = orgType.getSettings().getOrgLdapSettings();
        if (orgLdapSettings.getOrgLdapMode().equals(LdapModeType.SYSTEM.value())) {
            return true;
        }
        if (!orgLdapSettings.getOrgLdapMode().equals(LdapModeType.CUSTOM.value())) {
            return false;
        }
        CustomOrgLdapSettingsType customOrgLdapSettings =
                orgLdapSettings.getCustomOrgLdapSettings();
        return customOrgLdapSettings != null;
    }

    public void unconfigureOrgForLDAP(AdminOrgType orgType) {
        OrgLdapSettingsType orgLdapSettings = orgType.getSettings().getOrgLdapSettings();
        orgLdapSettings.setOrgLdapMode("NONE");
        client.putResource(orgType, RelationType.EDIT, RestAdminConstants.MediaType.ORGANIZATIONM,
                client.getVCloudObjectFactory().createAdminOrg(orgType), AdminOrgType.class);
    }

    public static OrgLdapUserAttributesType getDefaultUserAttributes() {
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
        return orgLdapUserAttributesType;
    }

    public static OrgLdapGroupAttributesType getDefaultGroupAttributes() {
        final OrgLdapGroupAttributesType orgLdapGroupAttributesType = new OrgLdapGroupAttributesType();
        orgLdapGroupAttributesType.setObjectClass("group");
        orgLdapGroupAttributesType.setObjectIdentifier("objectGuid");
        orgLdapGroupAttributesType.setGroupName("cn");
        orgLdapGroupAttributesType.setMembership("member");
        orgLdapGroupAttributesType.setMembershipIdentifier("dn");
        orgLdapGroupAttributesType.setBackLinkIdentifier("objectSid");
        return orgLdapGroupAttributesType;
    }

    private void configureOrgForLDAP(AdminOrgType orgType, final String customUsersOu) {
        OrgLdapSettingsType orgLdapSettings = orgType.getSettings().getOrgLdapSettings();
        Assert.assertTrue(orgLdapSettings != null);
        orgLdapSettings.setOrgLdapMode(LdapModeType.CUSTOM.value());
        CustomOrgLdapSettingsType customOrgLdapSettings =
                client.getVCloudObjectFactory().createCustomOrgLdapSettingsType();
        customOrgLdapSettings.setHostName(this.testbedLdapServerConfig.getLdapURI().getHost());
        customOrgLdapSettings.setPort(this.testbedLdapServerConfig.getLdapURI().getPort());
        customOrgLdapSettings.setSearchBase(this.testbedLdapServerConfig.getBaseDN());
        customOrgLdapSettings.setUserName(this.testbedLdapServerConfig.getCredentials().getUsername());
        customOrgLdapSettings.setPassword(this.testbedLdapServerConfig.getCredentials().getPassword());
        orgLdapSettings.setCustomOrgLdapSettings(customOrgLdapSettings);
        customOrgLdapSettings.setAuthenticationMechanism(LdapAuthenticationMechanismType.SIMPLE
                .value());

        customOrgLdapSettings.setConnectorType(LdapConnectorType.ACTIVE_DIRECTORY.value());

        customOrgLdapSettings.setUserAttributes(OrgLdapConfigUtil.getDefaultUserAttributes());
        customOrgLdapSettings.setGroupAttributes(OrgLdapConfigUtil.getDefaultGroupAttributes());

        orgLdapSettings.setCustomOrgLdapSettings(customOrgLdapSettings);
        orgLdapSettings.setCustomUsersOu(customUsersOu);
        client.putResource(
                RestAdminConstants.MediaType.ORGANIZATION_LDAP_SETTINGSM, client
                        .getVCloudObjectFactory().createOrgLdapSettings(orgLdapSettings),
                OrgLdapSettingsType.class);
    }

    public void configureOrgForSystemLDAP(AdminOrgType orgType, final String customUsersOu) {
        OrgLdapSettingsType orgLdapSettings = orgType.getSettings().getOrgLdapSettings();
        Assert.assertTrue(orgLdapSettings != null);
        orgLdapSettings.setOrgLdapMode(LdapModeType.SYSTEM.value());
        orgLdapSettings.setCustomUsersOu(customUsersOu);
        orgLdapSettings.setCustomOrgLdapSettings(null);
        client.putResource(RestAdminConstants.MediaType.ORGANIZATION_LDAP_SETTINGSM,
            client.getVCloudObjectFactory().createOrgLdapSettings(orgLdapSettings),
            OrgLdapSettingsType.class);
    }

    public void configureOrgForLDAP(OrgType orgType) {
        AdminOrgType adminOrg =
                orgType instanceof AdminOrgType ? (AdminOrgType) orgType : client.getResource(
                        orgType, RelationType.ALTERNATE,
                        RestConstants.MediaType.ADMIN_ORGANIZATION, AdminOrgType.class);
        configureOrgForLDAP(adminOrg, testbedLdapServerConfig.getBaseDN());
    }

    public void configureOrgForSystemLDAP(OrgType orgType) {
        AdminOrgType adminOrg = orgType instanceof AdminOrgType ? (AdminOrgType) orgType
            : client.getResource(orgType, RelationType.ALTERNATE,
                RestConstants.MediaType.ADMIN_ORGANIZATION, AdminOrgType.class);
        configureOrgForSystemLDAP(adminOrg, testbedLdapServerConfig.getBaseDN());
    }

    public UserType importUser(final AdminOrgType org, final String nameInSource,
            final String userName, final ReferenceType roleRef) {
        final UserType user = this.client.getVCloudObjectFactory().createUserType();
        user.setIsEnabled(true);
        user.setIsExternal(true);
        user.setProviderType("INTEGRATED");
        if (roleRef != null) {
            user.setRole(roleRef);
        }
        user.setNameInSource(nameInSource);
        user.setName(userName);

        final JAXBElement<UserType> u = client.getVCloudObjectFactory().createUser(user);
        return client.postResource(org, ADD, USERM, u, UserType.class);
    }


    public GroupType toGroupType(String groupName, String groupDescription) {
        GroupType groupType = client.getVCloudObjectFactory().createGroupType();
        groupType.setName(groupName);
        groupType.setDescription(groupDescription);
        return groupType;
    }

    public GroupType importGroup(final AdminOrgType org, final String groupName,
            final ReferenceType roleRef) {

        if (!isConfiguredForLDAP(org)) {
            throw new AssertionError(
                    "If you call importGroup on an org not configured for LDAP, the GROUP_IMPORT operation will not be available on the server!");
        }

        GroupType group = toGroupType(groupName, null);
        group.setRole(roleRef);

        /* Refresh the org as it may have been retrieved before being
         * configured for LDAP and thus won't have add group link. */
        final AdminOrgType refreshedOrg = client.getResource(org, AdminOrgType.class);
        client.relogin();
        final JAXBElement<GroupType> g = client.getVCloudObjectFactory().createGroup(group);
        final GroupType importedGroup = client.postResource(refreshedOrg, ADD, RestAdminConstants.MediaType.GROUPM, g, GroupType.class);
        importedGroups.put(org, groupName);
        return importedGroup;
    }

    public GroupType importGroup(final AdminOrgType org, final String groupName, Role role) {
        ReferenceType roleRef = role == null ? null : new RoleRightUtil(environment.getSystemAdminClient()).getRoleInOrg(role.getName(), org.getId());
        return importGroup(org, groupName, roleRef);
    }

    public UserType importUser(final AdminOrgType org, final UserType existingUser)
            throws Exception {
        if (existingUser == null) {
            Assert.fail("unexpected");
        }

        final JAXBElement<UserType> user = client.getVCloudObjectFactory().createUser(existingUser);

        final UserType newUser = client.postResource(org, ADD, USERM, user, UserType.class);

        return newUser;
    }

    public Set<String> getLdapGroups(AdminOrgType org) {
        org = client.getResource(URI.create(org.getHref()), AdminOrgType.class);
        Set<String> result = new HashSet<>();
        for (ReferenceType ref : org.getGroups().getGroupReference()) {
            result.add(ref.getName());
        }
        return result;
    }

    public GroupType getLdapGroup(String groupName) {
        final GroupsListType orgGroups = client.getLoggedInAdminOrg().getGroups();
        return orgGroups.getGroupReference().stream()
                .filter(g -> g.getName().equals(groupName))
                .map(g -> client.getResource(g, GroupType.class))
                .findFirst().orElse(null);
    }

    public void deleteGroup(AdminOrgType org, String groupName) {
        org = client.getResource(URI.create(org.getHref()), AdminOrgType.class);

        for (ReferenceType ref : org.getGroups().getGroupReference()) {
            if (ref.getName().equals(groupName)) {
                GroupType g = client.getResource(URI.create(ref.getHref()), GroupType.class);
                client.removeResource(g);
                return;
            }
        }
    }

    /**
     * Delete any imported LDAP users plus the supplied local username if present
     */
    public void deleteVcdLdapTestUsers(final AdminOrgType org, final String localUserName) {
        final AdminOrgType orgType = client.getResource(URI.create(org.getHref()), AdminOrgType.class);
        for (ReferenceType user : orgType.getUsers().getUserReference()) {
            final UserType userType = client.getResource(user, UserType.class);
            if ((userType.getProviderType().equals(IdentityProviderSourceType.INTEGRATED.value()) && userType.isIsExternal()) ||
                    userType.getName().equals(localUserName)) {
                client.deleteResource(URI.create(userType.getHref()));
            }
        }
    }

    /**
     * Delete any imported LDAP groups
     */
    public void deleteVcdLdapGroups(final AdminOrgType org) {
        final AdminOrgType orgType = client.getResource(URI.create(org.getHref()), AdminOrgType.class);
        for (ReferenceType user : orgType.getGroups().getGroupReference()) {
            final GroupType groupType = client.getResource(user, GroupType.class);
            if (groupType.getProviderType().equals(IdentityProviderSourceType.INTEGRATED.value())) {
                client.deleteResource(URI.create(groupType.getHref()));
            }
        }
    }


    /**
     * Checks if the specified username exists on the LDAP server. Assumes 'vsphere.local' SN
     */
    public boolean userExistsOnLdapServer(final String username) throws Exception {
        try {
            final String escapedUserName = LdapEncoder.filterEncode(username);
            // using LdapName does the necessary escaping for us.
            final LdapName ldapName = new LdapName(String.format(USER_DN_TEMPLATE, username));
            final List<String> searchResults = ldapServerTemplate.search(ldapName,
                    String.format(USER_SEARCH_FILTER, escapedUserName),
                    (AttributesMapper) attrs -> attrs.get("cn").get().toString());
            return !searchResults.isEmpty();
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the specified group exists on the LDAP server. Assumes 'vsphere.local' SN
     */
    public boolean groupExistsOnLdapServer(final String groupName) throws Exception {
        try {
            final String escapedGroupName = LdapEncoder.filterEncode(groupName);
            // using LdapName does the necessary escaping for us.
            final LdapName ldapName = new LdapName(String.format(GROUP_DN_TEMPLATE, groupName));
            final List<String> searchResults = ldapServerTemplate.search(ldapName,
                    String.format(GROUP_SEARCH_FILTER, escapedGroupName),
                    (AttributesMapper) attrs -> attrs.get("cn").get().toString());
            return !searchResults.isEmpty();
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the specified org exists on the LDAP server. Assumes 'vsphere.local' SN
     */
    public boolean orgUnitExistsOnLdapServer(final String orgName) throws Exception {
        try {
            final String escapedOrgName = LdapEncoder.filterEncode(orgName);
            // using LdapName does the necessary escaping for us.
            final LdapName ldapName = new LdapName(String.format(OU_DN_TEMPLATE, orgName));
            final List<String> searchResults = ldapServerTemplate.search(ldapName,
                    String.format(ORG_SEARCH_FILTER, escapedOrgName),
                    (AttributesMapper) attrs -> attrs.get(ATTRIBUTE_KEY_OU).get().toString());
            return !searchResults.isEmpty();
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the specified org user exists on the LDAP server. Assumes 'vsphere.local' SN
     */
    public boolean orgUnitUserExistsOnLdapServer(final String username, final String orgName) throws Exception {
        try {
            final String escapedUsername = LdapEncoder.filterEncode(username);
            // using LdapName does the necessary escaping for us.
            final LdapName ldapName = new LdapName(String.format(ORG_USER_DN_TEMPLATE, username, orgName));
            final List<String> searchResults =
                    ldapServerTemplate.search(ldapName, String.format(ORG_USER_SEARCH_FILTER, escapedUsername),
                            (AttributesMapper) attrs -> attrs.get(ATTRIBUTE_KEY_CN).get().toString());
            return !searchResults.isEmpty();
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Add an LDAP user who's account is disabled
     */
    public void addDisabledUserToLdapServer(final String username) throws Exception {
        if (this.userExistsOnLdapServer(username)) {
            return;
        }
        final Attributes attributes = buildUserAttributes(username);
        addAttribute(attributes, ATTRIBUTE_KEY_ACCOUNT_CONTROL, ATTRIBUTE_ACCOUNT_CONTROL_DISABLED);
        final Name dn = new LdapName(String.format(USER_DN_TEMPLATE, username));
        ldapServerTemplate.bind(dn, null, attributes);
    }

    /**
     * Adds a new user to the LDAP server. Users added here can then be imported by VCD which
     * interacts with the same LDAP server. Uses 'vsphere.local' SN. If user already exists, this is
     * just a noop
     */
    public void addUserToLdapServer(final String username) throws Exception {
        addUserToLdapServer(username, Collections.emptyList());
    }

    /**
     * Add the user to the LDAP server and bind them to the supplied groups name. Assumes groups are
     * already present
     */
    public void addUserToLdapServer(final String username, final String... groupNames) throws Exception {
        addUserToLdapServer(username, List.of(groupNames));
    }

    public void addUserToLdapServer(final String username, final List<String> groupNames) throws Exception {
        final Attributes attributes = buildUserAttributes(username);
        addUserToLdapServer(username, attributes, groupNames);
    }

    public void addUserToLdapServer(final String username, final Attributes attributes, final List<String> groupNames)
            throws Exception {
        if (this.userExistsOnLdapServer(username)) {
            return;
        }

        final Name dn = new LdapName(String.format(USER_DN_TEMPLATE, username));

        ldapServerTemplate.bind(dn, null, attributes);
        createUserNames.add(username);
        if (CollectionUtils.isNotEmpty(groupNames)) {
            for (String groupName : groupNames) {
                addUserToGroup(username, groupName);
            }
        }
    }

    /**
     * Remove the user from the LDAP server by unbinding.
     */
    public void removeUserFromLdapServer(final String username) throws Exception {
        if (!this.userExistsOnLdapServer(username)) {
            return;
        }
        ldapServerTemplate.unbind(new LdapName(String.format(USER_DN_TEMPLATE, username)));
    }

    /**
     * Remove the group from the LDAP server by unbinding.
     */
    public void removeGroupFromLdapServer(final String groupName) throws Exception {
        if (!groupExistsOnLdapServer(groupName)) {
            return;
        }
        ldapServerTemplate.unbind(new LdapName(String.format(GROUP_DN_TEMPLATE, groupName)));
    }

    /**
     * Remove the organizational unit user from the LDAP server by unbinding.
     */
    public void removeOrgUnitUserFromLdapServer(final String username, final String orgUnitName) throws Exception {
        if (!orgUnitUserExistsOnLdapServer(username, orgUnitName)) {
            return;
        }
        ldapServerTemplate.unbind(new LdapName(String.format(ORG_USER_DN_TEMPLATE, username, orgUnitName)));
    }

    /**
     * Remove the organizational unit from the LDAP server by unbinding.
     */
    public void removeOrgUnitFromLdapServer(final String name) throws Exception {
        if (!orgUnitExistsOnLdapServer(name)) {
            return;
        }
        ldapServerTemplate.unbind(new LdapName(String.format(OU_DN_TEMPLATE, name)));
    }

    public void addUserToGroup(final String username, final String groupName)
        throws InvalidNameException {
        final String encodedUserDn =
                new DistinguishedName(new LdapName(String.format(USER_DN_TEMPLATE, username))).encode();
        final String encodedGroupDn =
                new DistinguishedName(new LdapName(String.format(GROUP_DN_TEMPLATE, groupName))).encode();
        ldapServerTemplate.modifyAttributes(encodedGroupDn, new ModificationItem[] {
                new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute("member", encodedUserDn))
        });
    }

    public void addUserToOrgUnit(final String username, final String orgUnitName) throws Exception {
        if (orgUnitUserExistsOnLdapServer(username, orgUnitName)) {
            return;
        }

        final String encodedUserDn =
                new DistinguishedName(new LdapName(String.format(ORG_USER_DN_TEMPLATE, username, orgUnitName)))
                        .encode();

        final Attributes attributes = buildUserAttributes(username);
        addAttribute(attributes, ATTRIBUTE_KEY_OBJECT_CLASS, ORG_PERSON_OBJECT_CLASS);
        ldapServerTemplate.bind(encodedUserDn, null, attributes);
    }

    /**
     * Create the supplied group name in the configured LDAP server
     */
    public void addGroupToLdapServer(final String groupName) throws Exception {
        if (this.groupExistsOnLdapServer(groupName)) {
            return;
        }

        final Attributes attributes = new BasicAttributes();
        addAttribute(attributes, ATTRIBUTE_KEY_OBJECT_CLASS, GROUP_OBJECT_CLASS);
        addAttribute(attributes, ATTRIBUTE_KEY_CN, groupName);
        ldapServerTemplate.bind(new LdapName(String.format(GROUP_DN_TEMPLATE, groupName)), null, attributes);
        createdGroupNames.add(groupName);
    }

    /**
     * Create the supplied organization unit in the configured LDAP server
     */
    public void addOrgUnitToLdapServer(final String orgUnitName) throws Exception {
        if (orgUnitExistsOnLdapServer(orgUnitName)) {
            return;
        }

        final Attributes attributes = new BasicAttributes();
        addAttribute(attributes, ATTRIBUTE_KEY_OBJECT_CLASS, ORG_UNIT_OBJECT_CLASS);
        addAttribute(attributes, ATTRIBUTE_KEY_OU, orgUnitName);
        ldapServerTemplate.bind(new LdapName(String.format(OU_DN_TEMPLATE, orgUnitName)), null, attributes);
    }

    /**
     * Synchronize VCD organization with the LDAP server.
     */
    public void syncWithLdapServer(String tenantContext, long msWait) throws InterruptedException {
        final String savedTenantContext = client.getTenantContextHeader();
        try {
            client.setTenantContextHeader(tenantContext);
            client.getOpenApiClient().createProxy(LDAPApi.class).syncLdap();
        } finally {
            client.setTenantContextHeader(savedTenantContext);
        }

        // TODO VTEN-6164: Make the LDAP sync endpoint return a task ID (and wait for it to complete, instead of just
        //  sleeping)
        Thread.sleep(msWait);
    }

    public Attributes buildUserAttributes(final String username) {
        final Attributes attributes = new BasicAttributes();
        addAttribute(attributes, ATTRIBUTE_KEY_OBJECT_CLASS, USER_OBJECT_CLASS);
        addAttribute(attributes, ATTRIBUTE_KEY_SN, ATTRIBUTE_VALUE_SN);
        addAttribute(attributes, ATTRIBUTE_KEY_GIVEN_NAME, username);
        addAttribute(attributes, ATTRIBUTE_KEY_SAM_ACCOUNT_NAME, username);
        addAttribute(attributes, ATTRIBUTE_KEY_USER_PASSWORD, environment.getUserPassword());
        return attributes;
    }

    /**
     * Utility method to add LDAP {@link BasicAttribute}s
     */
    public static void addAttribute(final Attributes attributes, final String basicAttributeName,
            final Object... basicAttributeValues) {
        final BasicAttribute basicAttribute = new BasicAttribute(basicAttributeName);
        for (Object attributeValue : basicAttributeValues) {
            basicAttribute.add(attributeValue);
        }
        attributes.put(basicAttribute);
    }
}
