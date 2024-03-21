#!/usr/bin/env bash
#
# Script used with Drone CI to check for the existence of a build artifact.

if [[ -z ${DRONE_REPO} || -z ${DRONE_PULL_REQUEST} ]]; then
	echo -e "\n\n\n\n\e[31;1mRequired env variables not specified, likely a tag build so just failing\e[0m\n\n\n"
	exit 1
fi

# This file info MUST match the structure of `base` in the `drone-static-upload.sh` script in
# order to function correctly
prefix="session-android-"
suffix="-${DRONE_COMMIT:0:9}-universal.tar.xz"

# Extracting head.label using string manipulation
echo "Extracting repo information for 'https://api.github.com/repos/${DRONE_REPO}/pulls/${DRONE_PULL_REQUEST}'"
pr_info=$(curl -s https://api.github.com/repos/${DRONE_REPO}/pulls/${DRONE_PULL_REQUEST})
pr_info_clean=$(echo "$pr_info" | tr -d '[:space:]')
head_info=$(echo "$pr_info_clean" | sed -n 's/.*"head"\(.*\)"base".*/\1/p')
fork_repo=$(echo "$head_info" | grep -o '"full_name":"[^"]*' | sed 's/"full_name":"//')
fork_branch=$(echo "$head_info" | grep -o '"ref":"[^"]*' | sed 's/"ref":"//')
upload_dir="https://oxen.rocks/${fork_repo}/${fork_branch}"

echo "Starting to poll ${upload_dir}/ every 10s to check for a build matching '${prefix}.*${suffix}'"

# Loop indefinitely the CI can timeout the script if it takes too long
total_poll_duration=0
max_poll_duration=$((30 * 60))	# Poll for a maximum of 30 mins

while true; do
	# Need to add the trailing '/' or else we get a '301' response
	build_artifacts_html=$(curl -s "${upload_dir}/")

	if [ $? != 0 ]; then
		echo -e "\n\n\n\n\e[31;1mFailed to retrieve build artifact list\e[0m\n\n\n"
		exit 1
	fi

	# Extract 'session-ios...' titles using grep and awk then look for the target file
	current_build_artifacts=$(echo "$build_artifacts_html" | grep -o "href=\"${prefix}[^\"]*" | sed 's/href="//')
	target_file=$(echo "$current_build_artifacts" | grep -o "${prefix}.*${suffix}" | tail -n 1)

	if [ -n "$target_file" ]; then
		echo -e "\n\n\n\n\e[32;1mExisting build artifact at ${upload_dir}/${target_file}\e[0m\n\n\n"
	    exit 0
	fi

	# Sleep for 10 seconds before checking again
	sleep 10
	total_poll_duration=$((total_poll_duration + 10))

	if [ $total_poll_duration -gt $max_poll_duration ]; then
		echo -e "\n\n\n\n\e[31;1mCould not find existing build artifact after polling for 30 minutes\e[0m\n\n\n"
		exit 1
	fi
done
