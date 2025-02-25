#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=50"
export JVM_OPTS="$JVM_OPTS -Djqf.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=200000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=17"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):closure-compiler-v20180204.jar:commons-io-2.18.0.jar -l -b CompilerTest testWithGenerator rank-cm-50/