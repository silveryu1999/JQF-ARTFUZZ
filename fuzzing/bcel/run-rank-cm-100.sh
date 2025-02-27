#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=100"
export JVM_OPTS="$JVM_OPTS -Djqf.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=1000000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=15"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -l -b ParserTest testWithGenerator rank-cm-100/