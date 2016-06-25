#!/bin/bash

echo "$@" >> env

set >> env

export PATH=$PATH:/opt/local/bin

if [ ! -e ./pkg/gomobile/android-ndk-* ]; then
  go get -u golang.org/x/mobile/cmd/gomobile
  gomobile init
fi

gomobile "$@"