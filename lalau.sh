#!/bin/bash

# Directory to start searching from
SEARCH_DIR="$1"

# Check if search directory is provided
if [[ -z "$SEARCH_DIR" ]]; then
    echo "Usage: $0 <directory>"
    exit 1
fi

# Temporary file to store all extracted memory and cpu values
TEMP_FILE=$(mktemp)

# Find all docker-compose.yml files and process them
find "$SEARCH_DIR" -type f \( -name 'docker-compose.yml' -o -name 'docker-compose.yaml' \) -print0 | while IFS= read -r -d $'\0' file; do
    # Extract service names, memory and cpu limits using yq
    yq e '.services | to_entries[] | .key as $k | .value.deploy?.resources?.limits? | select(. != null) | {service: $k, memory: .memory, cpu: .cpus}' "$file" >> "$TEMP_FILE"
done

# Use awk to sum and count memory and cpu for each service, then calculate average
awk '
    function convertToBytes(s) {
        gsub(/[a-zA-Z]/, "", s);
        unit = substr($0, length, 1);
        if (unit == "g" || unit == "G") return s * 1024 ^ 3;
        if (unit == "m" || unit == "M") return s * 1024 ^ 2;
        if (unit == "k" || unit == "K") return s * 1024;
        return s; # bytes
    }
    /service:/ {
        service = $2;
    }
    /memory:/ {
        memSum[service] += convertToBytes($2);
        memCount[service]++;
    }
    /cpu:/ {
        cpuSum[service] += $2;
        cpuCount[service]++;
    }
    END {
        for (service in memSum) {
            avgMem = memSum[service] / memCount[service];
            avgCpu = cpuSum[service] / cpuCount[service];
            printf "Service: %s, Average Memory: %f GB, Average CPU: %f cores\n", service, avgMem / (1024 ^ 3), avgCpu;
        }
    }
' "$TEMP_FILE"

# Clean up
rm "$TEMP_FILE"
