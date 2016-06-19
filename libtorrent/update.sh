#!/bin/bash
#
# https://groups.google.com/forum/#!topic/go-mobile/ZstjAiIFrWY
#

go get -u github.com/anacrolix/torrent || exit 1

gomobile bind -o libtorrent.aar github.com/anacrolix/torrent/libtorrent || exit 1
