#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=200000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=17"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -l -b CompilerTest testWithGenerator rank-cm-1/

../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -l -b CompilerTest testWithGenerator rank-cm-2/

../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -l -b CompilerTest testWithGenerator rank-cm-3/

../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -l -b CompilerTest testWithGenerator rank-cm-4/

../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -l -b CompilerTest testWithGenerator rank-cm-5/