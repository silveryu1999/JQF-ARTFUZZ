#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=50"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=1000000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=15"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):ant-1.10.1.jar:ant-launcher-1.10.1.jar:plexus-utils-3.1.0.jar:commons-lang3-3.12.0.jar -l -b ProjectBuilderTest testWithGenerator rank-50/