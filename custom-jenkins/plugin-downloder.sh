#!/bin/bash

cd ./plugins

for text in $(cat ../plugins.txt)
do
  name=$(echo "${text}" | cut -f 1 -d ":")
  version=$(echo "${text}" | cut -f 2 -d ":")
  curl -LO https://updates.jenkins.io/download/plugins/"${name}"/"${version}"/"${name}".hpi
done