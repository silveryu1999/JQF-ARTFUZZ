#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=50"
export JVM_OPTS="$JVM_OPTS -Djqf.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=5000000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=15"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):maven-model-3.5.2.jar:maven-model-builder-3.5.2.jar:plexus-utils-3.1.0.jar:commons-lang3-3.12.0.jar -l -b ModelReaderTest testWithGenerator rank-cm-50/