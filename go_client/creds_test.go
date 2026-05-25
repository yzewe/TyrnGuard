package main

import (
	"reflect"
	"testing"
)

func TestParseTURNServerURLsKeepsOnlyUDPRelayCandidates(t *testing.T) {
	raw := []interface{}{
		"turn:relay.example.org:3478?transport=udp",
		"turn:relay.example.org:3478?transport=tcp",
		"turns:relay.example.org:5349?transport=tcp",
		"turn:second.example.org:3478",
		"turn:relay.example.org:3478?transport=udp",
	}

	got := parseTURNServerURLs(raw)
	want := []string{
		"relay.example.org:3478",
		"second.example.org:3478",
	}

	if !reflect.DeepEqual(got, want) {
		t.Fatalf("parseTURNServerURLs() = %#v, want %#v", got, want)
	}
}
