#!/bin/bash

# Exit on error
set -e

# Replace RELEASE with CURRENT_VERSION in jbang-catalog.json
sed -i "s/:RELEASE@/:${CURRENT_VERSION}@/g" jbang-catalog.json

