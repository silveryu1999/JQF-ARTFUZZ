#!/bin/bash

# Figure out script absolute path
pushd `dirname $0` > /dev/null
CURR_DIR=`pwd`
popd > /dev/null

cp=""

for jar in "$CURR_DIR"/*.jar; do
  cp="$cp:$jar"
done

echo $cp
