#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_MUTATION_TIME_AND_SIZE=true" #M
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_WEIGHT_DISTANCE=true" #W
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_ENERGY=true" #E
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_HAMMING_DISTANCE=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_CROSSOVER=true" #C
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CROSSOVER_PROBABILITY=25"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.VALID_SEED_FIRST=true"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=200000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=16"
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-1/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-2/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-3/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-4/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-5/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-6/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-7/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-8/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-9/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b WebXmlTest testWithGenerator rank-EMWC25-10/