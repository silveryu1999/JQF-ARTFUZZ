#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_MUTATION_TIME_AND_SIZE=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_CROSSOVER=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.VALID_SEED_FIRST=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_ENERGY=false"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CROSSOVER_PROBABILITY=25"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=200000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=17"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b CompilerTest testWithGenerator rank-MC25-6/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b CompilerTest testWithGenerator rank-MC25-7/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b CompilerTest testWithGenerator rank-MC25-8/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b CompilerTest testWithGenerator rank-MC25-9/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b CompilerTest testWithGenerator rank-MC25-10/