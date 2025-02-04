# Sample project creation with java

This repository contains the sample code to create a project [Sample project creation with Project SDK](https://<<vcfa_url>>/project-service/api/swagger/v3/api-docs/2019-01-15).

## Quickstart

Try out the Sample project creation in this repository.

### Steps

#### 1. configure target vRA instance and auth details
```
Identify a vRA instance that is reachable from your machine and provide the following details of the instance in src/main/resources/application.yaml:
   - server.url
   - server.access_token
```

#### 2. Steps to download and generate project-service client sdk
**Downloading vRA specs and generating client bindings for vRA specs using Open API**
- Navigate to `automation-services/src/main/build` of the repo
- Ensure script has execute permission by executing `chmod +x generate_sdk.sh`
- Run the script `./generate_sdk.sh` from the same directory
- This script would
  - download all the OpenAPI specs of vRA to `downloaded_vra_specs` directory
  - generate the service code in `openapi_generated` directory
  - build and install `openapi-java-client` jar into the maven home
- Notice that once the execution is complete, the failed generations could be found in `openapi_sdk_failed_modules.log` in the base directory of the repo


#### 3. Create the sample project
**Run the main method in ProjectCRUDSample.java**
- Navigate to automation-services/src/main/java/com/vmware/vcfa/samples/automation/project/ProjectCRUDSample.java
- Run the main() method

