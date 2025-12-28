#!/bin/bash
# Wrapper script to launch two Sparrow instances for testing
# This script is in the project root and calls the actual script in .scripts/

exec .scripts/run-two-instances.sh "$@"
