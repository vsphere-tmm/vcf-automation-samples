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
