#!/bin/bash

# Define an array
readonly vra_spec_locations=("project-service/api/swagger/v3/api-docs/2019-01-15")
readonly vra_download_location="downloaded_vra_specs"


function extract_module_name() {
    local str="$1"
    #echo "### str is $str"

    result=`echo $str | cut -f 1 -d "/"`
    echo "$result"

}

function validate_input() {
    # Check if vra host reference is provided
    # TODO enhance with better checks later
    if [ "$#" -ne 1 ]; then
        echo "Usage: $0 need vRA environment"
        exit 1
    fi
}

function download_vra_specs() {
    # Loop through all provided URLs
    for uri in "${vra_spec_locations[@]}"; do

        local url="$1/$uri"
        echo "### URL is $url"

        outfile=$(extract_module_name "$uri")

        echo "file prefix: $outfile"

        if [[ $uri == *".yaml"* ]]; then
            outfile="${vra_download_location}/${outfile}.yaml"
        else
            outfile="${vra_download_location}/${outfile}.json"
        fi

        echo "### download location: $outfile"

        # Download the file
        curl -o "$outfile" "$url" -k

        # Check if the download was successful
        if [ $? -eq 0 ]; then
            echo "Downloaded: $outfile"
        else
            echo "Failed to download: $outfile"
        fi
    done

}

function create_dir() {
    # check and create directory
    if [ ! -d "$vra_download_location" ]; then
        echo "$vra_download_location does not exist."
        mkdir -p $vra_download_location
    fi
}

#Main function
function main() {
    validate_input $@
    create_dir $@
    download_vra_specs $@
}

################################################################################################
######### starting cmd ./vra/vra_downloader.sh https://sc2-10-185-2-24.eng.vmware.com ##########
################################################################################################

main $@