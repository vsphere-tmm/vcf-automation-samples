# vcf-automation-tenant-manager-api-samples
This repo is for internally tracking the samples work to be published at https://github.com/vsphere-tmm/vcf-automation-samples 

### Steps

#### 1. Clone this git repository.
```
git clone https://github.com/vsphere-tmm/vcf-automation-samples 
```

#### 2. Prerequisites

- Use the following Java and Maven versions:
  - Java 17 or older
  - Maven v3.8.4 or older
- The following libraries and their dependencies should be present in your local maven repo:
  - vcf-automation-samples-parent
  - vcfa-samples-commons
  - automation-services
- Build the project at the tenant-manager root directory `mvn clean install` 
- Run the desired example(s); e.g. "TmClientExample", "TmImportIdpUserExample"
  - Note: Some examples, like "TmImportIdpUserExample", will require global variables to be updated to point to user-specific data in order to successfully run.

### 3. Prerequisites for running Region create/update examples

- Make sure vcfa instance has a VC with supervisor and NSX attached.
- Also include a storage class name in the applications.yaml file for key 'storageClass' which is present in VC and assigned to host datastores. This policy would be used to create Region.
- To update Region with a new storage class make sure that VC has a storage class assigned to host datastores and its name updated in the application.yaml file for key 'updateRegionStorageClass'.
- To get Region details of a specific Region provide region id in the application.yaml file under key 'regionId'. if not provided, sample will create a region and list its details. 
