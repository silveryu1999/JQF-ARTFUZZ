#!/bin/bash

scripts=(

)

for script in "${scripts[@]}"; do
  echo "Running $script in a sub-shell..."

  (
    ./"$script"
  )

  if [ $? -eq 0 ]; then
    echo "$script executed successfully."
  else
    echo "$script failed to execute."
  fi
done

echo "All scripts have been executed."