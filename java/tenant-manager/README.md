# vcf-automation-tenant-manager-api-samples
This repo is for internally tracking the samples work to be published at https://github.com/vsphere-tmm/vcf-automation-samples 

### Steps

#### 1. Clone this git repository.
```
git clone https://github-vcf.devops.broadcom.net/vcf/vcf-automation-samples
```

#### 2. Prerequisites

- Copy or install "rest-api-client" jar in your local maven repo.
- Point your local maven home repo in `build/maven-settings.xml`
- Java 11 or older
- maven v3.8.4 or older
- Build the project at the tenant-manager root directory `mvn -s build/maven-settings.xml clean install -DskipTests=true`
- Run the "TmClientExample"

### 3. Prerequisites for running Region create/update examples

- Make sure vcfa instance has a VC with supervisor and NSX attached.
- Also include a storage class name in the applications.yaml file for key 'storageClass' which is present in VC and assigned to host datastores. This policy would be used to create Region.
- To update Region with a new storage class make sure that VC has a storage class assigned to host datastores and its name updated in the application.yaml file for key 'updateRegionStorageClass'.
- To get Region details of a specific Region provide region id in the application.yaml file under key 'regionId'. if not provided, sample will create a region and list its details. 
