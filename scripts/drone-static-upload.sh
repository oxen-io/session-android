#!/usr/bin/env bash

# Script used with Drone CI to upload build artifacts (because specifying all this in
# .drone.jsonnet is too painful).

set -o errexit

if [ -z "$SSH_KEY" ]; then
    echo -e "\n\n\n\e[31;1mUnable to upload artifact: SSH_KEY not set\e[0m"
    # Just warn but don't fail, so that this doesn't trigger a build failure for untrusted builds
    exit 0
fi

echo "$SSH_KEY" >ssh_key

set -o xtrace  # Don't start tracing until *after* we write the ssh key

chmod 600 ssh_key

# Define the output paths
build_dir="app/build/outputs/apk/play/debug"
target_path="${build_dir}/$(ls ${build_dir} | grep -o 'session-[^[:space:]]*-universal.apk')"

# Validate the paths exist
if [ ! -d $build_path ]; then
    echo -e "\n\n\n\e[31;1mExpected a file to upload, found none\e[0m" >&2
    exit 1
fi

if [ -n "$DRONE_TAG" ]; then
    # For a tag build use something like `session-android-v1.2.3-universal`
    base="session-android-$DRONE_TAG-universal"
else
    # Otherwise build a length name from the datetime and commit hash, such as:
    # session-android-20200522T212342Z-04d7dcc54-universal
    base="session-android-$(date --date=@$DRONE_BUILD_CREATED +%Y%m%dT%H%M%SZ)-${DRONE_COMMIT:0:9}-universal"
fi

# Copy over the build products
mkdir -vp "$base"
cp -av $target_path "$base"

# tar dat shiz up yo
archive="$base.tar.xz"
tar cJvf "$archive" "$base"

upload_to="oxen.rocks/${DRONE_REPO// /_}/${DRONE_BRANCH// /_}"

# sftp doesn't have any equivalent to mkdir -p, so we have to split the above up into a chain of
# -mkdir a/, -mkdir a/b/, -mkdir a/b/c/, ... commands.  The leading `-` allows the command to fail
# without error.
upload_dirs=(${upload_to//\// })
put_debug=
mkdirs=
dir_tmp=""
for p in "${upload_dirs[@]}"; do
    dir_tmp="$dir_tmp$p/"
    mkdirs="$mkdirs
-mkdir $dir_tmp"
done

sftp -i ssh_key -b - -o StrictHostKeyChecking=off drone@oxen.rocks <<SFTP
$mkdirs
put $archive $upload_to
$put_debug
SFTP

set +o xtrace

echo -e "\n\n\n\n\e[32;1mUploaded to https://${upload_to}/${archive}\e[0m\n\n\n"
