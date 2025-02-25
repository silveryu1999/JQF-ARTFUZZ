#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=25"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_TO_FAILURE_IS_POSITIVE=false"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=500000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=16"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):rhino-1.7.7.2.jar:commons-io-2.18.0.jar -l -b CompilerTest testWithGenerator rank-25-/