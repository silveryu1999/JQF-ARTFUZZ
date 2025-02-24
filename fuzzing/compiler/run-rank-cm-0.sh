#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=0"
export JVM_OPTS="$JVM_OPTS -Djqf.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=100000"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):closure-compiler-v20180204.jar -l -b CompilerTest testWithGenerator rank-cm-0/