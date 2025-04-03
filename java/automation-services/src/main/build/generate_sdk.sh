#!/bin/bash

PROJECT_REPO_PATH=$1

if [ -z "$PROJECT_REPO_PATH" ]; then
  echo "Usage: [optional] Provide Project path as argument. example: ./generarate_sdk.sh <PROJECT_ROOT_PATH>"
  PROJECT_REPO_PATH="../../../../"
  echo $PROJECT_REPO_PATH
fi

CONFIG_FILE="$PROJECT_REPO_PATH/vcfa-samples-commons/src/main/resources/application.yaml"

#extract .server.url from the CONFIG_FILE
VCFA_URL=$(sed -n 's/^[[:space:]]*url:[[:space:]]*"\(.*\)"/\1/p' "$CONFIG_FILE")
echo "$VCFA_URL"

chmod u+x ./vra_downloader.sh
chmod u+x ./process_openapi_sdk.sh

./vra_downloader.sh $VCFA_URL

download_path="downloaded_vra_specs"
if [ -d "$download_path" ]; then
  ./process_openapi_sdk.sh downloaded_vra_specs
  service_repo_parent="openapi_generated"
  if [ -d "$service_repo_parent" ]; then
    cd $service_repo_parent/project-service && mvn clean install
  else
    echo "service code is not generated from the openAPI spec in $download_path"
  fi
else
  echo "vRA spec download folder is not created."
fi




