#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.TOTALLY_RANDOM=true"
#export JVM_OPTS="$JVM_OPTS -Djqf.getARTInput=true"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.FSCS_K=10"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.EXECUTED_INPUTS_TO_BE_IGNORE_PERCENTAGE=0"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=5000000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=15"
../../bin/jqf-art -c .:$(../../scripts/classpath.sh):maven-model-3.5.2.jar:maven-model-builder-3.5.2.jar:plexus-utils-3.1.0.jar:commons-lang3-3.12.0.jar -l -b ModelReaderTest testWithGenerator jqf-random/