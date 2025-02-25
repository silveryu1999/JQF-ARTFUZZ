#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.WEIGHT_OF_TO_FAILURE_DISTANCE=75"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=100000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=15"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):bcel-6.2.jar:hamcrest-library-1.3.jar -l -b ParserTest testWithGenerator rank-75/