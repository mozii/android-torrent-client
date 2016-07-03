#!/bin/bash
#
# https://groups.google.com/forum/#!topic/go-mobile/ZstjAiIFrWY
#

go get -u github.com/axet/libtorrent || exit 1

gomobile bind -o libtorrent.aar github.com/axet/libtorrent || exit 1
