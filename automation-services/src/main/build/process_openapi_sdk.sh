#!/bin/bash

readonly sdk_dir_location="openapi_sdk_generated"
readonly failed_log="openapi_sdk_failed_modules.log"

function validate_input() {
    # Check if directory argument is provided
    if [ "$#" -ne 1 ]; then
        echo "Usage: $0 path to vRA specs is needed"
        exit 1
    fi
}

function extract_module_name() {
    local str="$1"
    #echo "### str is $str"

    result=`basename $str`
    result=`echo $result | cut -f 1 -d "."`
    echo "$result"

}

function generate_sdk() {
    # Loop through each file in the specified directory
    for FILE in "$1"/*; do
        # Check if it's a file (not a directory)
        if [[ -f "$FILE" ]]; then
            #echo "Processing file: $FILE"

            module_name=$(extract_module_name "$FILE")

            openapi-generator generate -i $FILE -g java -o openapi_generated/$module_name --invoker-package com.vmware.$module_name --api-package com.vmware.$module_name --model-package com.vmware.$module_name.model
            if [[ $? -eq 0 ]]; then
                echo "generation successful for $module_name"
            else
                echo "Generation failed for $module_name" >> $failed_log
            fi

            #echo "Finished processing $FILE"
        fi
    done
}

function create_dir() {
    # check and create directory
    if [ ! -d "$sdk_dir_location" ]; then
        echo "$sdk_dir_location does not exist."
        mkdir -p $sdk_dir_location
    fi
    # clear the contents of log if it exists
    > $failed_log
}


#Main function
function main() {
    validate_input $@
    create_dir $@
    generate_sdk $@
}

################################################################################################
####### starting cmd ./vra/openapi/process_openapi_sdk.sh <path-to-downloaded_vra_specs> #######
################################################################################################


main $@