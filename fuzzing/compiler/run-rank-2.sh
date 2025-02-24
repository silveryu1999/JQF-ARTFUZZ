#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.TO_FAILED_DISTANCE_WEIGHT=2"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=100000"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):closure-compiler-v20180204.jar -l -b CompilerTest testWithGenerator rank-2/