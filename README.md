# vcf-automation-samples
This repo is for internally tracking the samples work to be published at https://github.com/vsphere-tmm/vcf-automation-samples 

# Sample project creation with java

This repository contains the sample code to create a project [Sample project creation with Project SDK](https://<<vcfa_url>>/project-service/api/swagger/v3/api-docs/2019-01-15).

## Quickstart

Try out the Sample project creation in this repository.

### Steps

#### 1. Clone this git repository.
```
git clone https://github-vcf.devops.broadcom.net/vcf/vcf-automation-samples
```

#### 2. Steps to generate project-service client sdk

**Downloading vRA specs**

- Pre-requisite - Identify a vRA instance that is reachable from your machine
- Navigate to <baseDir>/automation-services/src/main/build of the repo
- Ensure script has execute permission by executing `chmod +x ./vra/vra_downloader.sh`
- Execute `./vra/vra_downloader.sh https://sc2-10-185-2-24.eng.vmware.com`
  This would download all the OpenAPI specs of vRA to `downloaded_vra_specs` directory

**Install OpenAPI and Swagger Tools**
- OpenAPI on mac `brew install openapi-generator`
- Swagger on mac `brew install swagger-codegen`
- Install npm on mac `brew install npm`
- Install package through npm `npm install -g openapi-filter`

**Generate Client bindings for vRA specs using Open API**

- Navigate to <baseDir>/automation-services/src/main/build of the repo
- Trigger SDK generation for each of the OpenAPI specs by executing `./vra/openapi/process_openapi_sdk.sh downloaded_vra_specs`
- Notice that once the execution is complete, the failed generations could be found in `openapi_sdk_failed_modules.log` in the base directory of the repo
- Navigate to <baseDir>/automation-services/src/main/build/openapi_generated/project_service
- generate project service client sdk by executing `mvn clean install`

#### 3. configure target vRA instance and auth details
```
You will need to provide the following details in src/main/resources/application.yaml:
  * server.url
  * server.access_token
```

#### 4. Create the sample project

**Run the main method in ProjectCRUDSample.java**

- Navigate to automation-services/src/main/java/com/vmware/vcfa/samples/automation/project/ProjectCRUDSample.java
- Run the main() method

