# Sample Deployment creation from published catalog with java

This module contains the sample code to create a deployment from a catalog item [Sample deployment creation with Catalog SDK](https://<<vcfa_url>>/catalog/api-docs/classic-apis/api-doc-public-classic-vra-2020-08-25.yaml).

## Quickstart

Try out the Deployment creation in this module.

### Steps

#### 1. configure target vRA instance and auth details
```
Identify a vRA instance that is reachable from your machine and provide the following details of the instance in src/main/resources/application.yaml:
   - server.url
   - server.access_token
   - server.verify_ssl
   - server.ssl_cert_path
   
 Provide the ssl certificate path in perm format. This is required if verify_ssl is set to true.
```

#### 2. Steps to download and generate catalog-service client sdk
**Downloading vRA specs and generating client bindings for vRA specs using Open API**
- Navigate to `automation-services/build-scripts` of the repo
- Ensure script has execute permission by executing `chmod +x generate_sdk.sh`
- Run the script `./generate_sdk.sh` from the same directory
- This script would
    - download all the OpenAPI specs of vRA to `downloaded_vra_specs` directory
    - generate the service code in `openapi_generated` directory
    - build and install `openapi-java-client` jar into the maven home
- Notice that once the execution is complete, the failed generations could be found in `openapi_sdk_failed_modules.log` in the base directory of the repo

#### 3. Prerequisites
**Creating project and publishing catalog Item**
- Ensure a project and add vsphere cloud zone in it. Note the Id of this project.
- Create a blueprint and publish it as a catalog-item. Note the Id of this catalog item.

#### 5. Configure the input information
- Add the deployment name, project Id and catalog Id from the above step into src/main/resources/catalog.yaml

#### 6. Create a deployment from the catalog Item
**Run the main method in DeploymentSample.java**
- Navigate to catalog-service/src/main/java/catalog/DeploymentSample.java
- Run the main() method
