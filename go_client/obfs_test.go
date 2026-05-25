package main

import (
	"bytes"
	"testing"
)

func TestObfsWrapPacketToRoundTrip(t *testing.T) {
	key := bytes.Repeat([]byte{0x42}, wrapKeyLen)
	aead, err := newObfsAEAD(key)
	if err != nil {
		t.Fatal(err)
	}

	cfg := NewObfsConfig()
	state := NewObfsState()
	payload := []byte("hello tyrn")
	wireBuf := make([]byte, obfsMaxWireLen(len(payload), cfg))
	plainBuf := make([]byte, len(payload))

	wire, err := obfsWrapPacketTo(aead, payload, cfg, state, wireBuf)
	if err != nil {
		t.Fatal(err)
	}
	n, err := obfsUnwrapPacket(aead, wire, plainBuf)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(plainBuf[:n], payload) {
		t.Fatalf("round trip = %q, want %q", plainBuf[:n], payload)
	}
}

func TestObfsWrapPacketToKeepsPerPacketAllocationsLow(t *testing.T) {
	key := bytes.Repeat([]byte{0x24}, wrapKeyLen)
	aead, err := newObfsAEAD(key)
	if err != nil {
		t.Fatal(err)
	}
	cfg := NewObfsConfig()
	state := NewObfsState()
	payload := bytes.Repeat([]byte{0x7a}, 128)
	wireBuf := make([]byte, obfsMaxWireLen(len(payload), cfg))

	allocs := testing.AllocsPerRun(200, func() {
		if _, err := obfsWrapPacketTo(aead, payload, cfg, state, wireBuf); err != nil {
			t.Fatal(err)
		}
	})
	if allocs > 1 {
		t.Fatalf("obfsWrapPacketTo allocations = %v, want <= 1", allocs)
	}
}
