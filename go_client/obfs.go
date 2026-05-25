// SPDX-License-Identifier: MIT
// obfs.go — WebRTC SRTP-like obfuscation for DTLS traffic
// Each UDP packet is wrapped in an RTP header making it indistinguishable
// from a real WebRTC OPUS audio stream to DPI systems.
//
// Packet format:
//   [RTP Header 12 bytes][ChaCha20-Poly1305 payload+tag][Padding 0-N bytes][PadLen 1 byte]
//
// The RTP header fields (SSRC + SeqNum + Timestamp) form the 12-byte AEAD nonce,
// so no separate nonce prefix is needed.

package main

import (
	"crypto/cipher"
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"sync"

	"golang.org/x/crypto/chacha20poly1305"
)

// ─── Configuration ───

// ObfsConfig holds per-session obfuscation parameters.
type ObfsConfig struct {
	SSRC        uint32 // Synchronization Source — random per session
	PayloadType uint8  // RTP payload type (111 = OPUS dynamic)
	PaddingMax  int    // Max random padding bytes appended
}

// NewObfsConfig creates a config with random SSRC and sane defaults.
func NewObfsConfig() *ObfsConfig {
	var buf [4]byte
	rand.Read(buf[:])
	return &ObfsConfig{
		SSRC:        binary.BigEndian.Uint32(buf[:]),
		PayloadType: 111, // dynamic PT for OPUS
		PaddingMax:  24,
	}
}

// ─── Per-direction state (sequence + timestamp counters) ───

// ObfsState tracks monotonically increasing RTP sequence number and timestamp.
type ObfsState struct {
	mu  sync.Mutex
	seq uint16
	ts  uint32
	rng uint64
}

// NewObfsState creates a state with random initial seq/ts.
func NewObfsState() *ObfsState {
	var buf [14]byte
	rand.Read(buf[:])
	rng := binary.BigEndian.Uint64(buf[6:14])
	if rng == 0 {
		rng = 0x9e3779b97f4a7c15
	}
	return &ObfsState{
		seq: binary.BigEndian.Uint16(buf[0:2]),
		ts:  binary.BigEndian.Uint32(buf[2:6]),
		rng: rng,
	}
}

// ─── Nonce derivation ───

// obfsBuildNonce deterministically builds a 12-byte AEAD nonce from RTP fields.
//
//	[SSRC 4B][SeqNum 2B][0x00 0x00][Timestamp 4B]
func newObfsAEAD(key []byte) (cipher.AEAD, error) {
	if len(key) != wrapKeyLen {
		return nil, fmt.Errorf("obfs: key must be %d bytes (got %d)", wrapKeyLen, len(key))
	}
	aead, err := chacha20poly1305.New(key)
	if err != nil {
		return nil, fmt.Errorf("obfs: cipher init: %w", err)
	}
	return aead, nil
}

func obfsBuildNonce(dst *[12]byte, ssrc uint32, seq uint16, ts uint32) []byte {
	*dst = [12]byte{}
	binary.BigEndian.PutUint32(dst[0:4], ssrc)
	binary.BigEndian.PutUint16(dst[4:6], seq)
	binary.BigEndian.PutUint32(dst[8:12], ts)
	return dst[:]
}

func obfsMaxWireLen(payloadLen int, cfg *ObfsConfig) int {
	paddingMax := 1
	if cfg != nil && cfg.PaddingMax > paddingMax {
		paddingMax = cfg.PaddingMax
	}
	return 12 + payloadLen + chacha20poly1305.Overhead + paddingMax
}

func obfsNextRand(seed uint64) uint64 {
	seed ^= seed << 7
	seed ^= seed >> 9
	seed ^= seed << 8
	if seed == 0 {
		return 0x9e3779b97f4a7c15
	}
	return seed
}

func obfsFillPadding(dst []byte, seed uint64) {
	for i := range dst {
		seed = obfsNextRand(seed)
		dst[i] = byte(seed)
	}
}

// ─── Wrap (encrypt + add RTP header) ───

// obfsWrapPacket wraps a plaintext payload into an RTP-like packet with authenticated encryption.
// The output looks like:
//
//	[V=2,P=1,X=0,CC=0 | PT | SeqNum | Timestamp | SSRC | encrypted_payload | padding | padLen]
func obfsWrapPacket(aead cipher.AEAD, payload []byte, cfg *ObfsConfig, state *ObfsState) ([]byte, error) {
	out := make([]byte, obfsMaxWireLen(len(payload), cfg))
	return obfsWrapPacketTo(aead, payload, cfg, state, out)
}

func obfsWrapPacketTo(aead cipher.AEAD, payload []byte, cfg *ObfsConfig, state *ObfsState, out []byte) ([]byte, error) {
	if aead == nil {
		return nil, errors.New("obfs: nil cipher")
	}
	if len(payload) == 0 {
		return nil, errors.New("obfs: empty payload")
	}
	if cfg == nil || state == nil {
		return nil, errors.New("obfs: nil config/state")
	}

	state.mu.Lock()
	seq := state.seq
	ts := state.ts
	state.seq++
	state.ts += 960 // 20ms frame @ 48kHz (OPUS standard)
	padRand := 0
	padSeed := state.rng
	if cfg.PaddingMax > 0 {
		state.rng = obfsNextRand(state.rng)
		padSeed = state.rng
		padRand = int(byte(padSeed)) % cfg.PaddingMax
	}
	state.mu.Unlock()

	// Build nonce from RTP fields
	var nonce [12]byte
	nonceBytes := obfsBuildNonce(&nonce, cfg.SSRC, seq, ts)

	padTotal := padRand + 1 // +1 for the length byte itself

	// Output: 12 (header) + payload + AEAD tag + padTotal
	outLen := 12 + len(payload) + chacha20poly1305.Overhead + padTotal
	if len(out) < outLen {
		return nil, fmt.Errorf("obfs: output buffer too small (%d < %d)", len(out), outLen)
	}
	out = out[:outLen]

	// RTP Header (12 bytes)
	out[0] = 0x80 | 0x20 // V=2, P=1 (padding present)
	out[1] = cfg.PayloadType & 0x7F
	binary.BigEndian.PutUint16(out[2:4], seq)
	binary.BigEndian.PutUint32(out[4:8], ts)
	binary.BigEndian.PutUint32(out[8:12], cfg.SSRC)

	sealed := aead.Seal(out[12:12], nonceBytes, payload, out[:12])

	// Random padding bytes
	padStart := 12 + len(sealed)
	if padRand > 0 {
		obfsFillPadding(out[padStart:padStart+padRand], padSeed)
	}

	// Last byte = total padding count (RFC 3550 §5.1)
	out[outLen-1] = byte(padTotal)

	return out, nil
}

// ─── Unwrap (strip RTP header + decrypt) ───

// obfsUnwrapPacket strips the RTP header, removes padding, and decrypts the payload.
// Returns number of plaintext bytes written to dst.
func obfsUnwrapPacket(aead cipher.AEAD, wire, dst []byte) (int, error) {
	if aead == nil {
		return 0, errors.New("obfs: nil cipher")
	}
	if len(wire) < 13 { // 12 header + at least 1 byte
		return 0, errors.New("obfs: packet too short")
	}

	// Validate RTP version
	if (wire[0] >> 6) != 2 {
		return 0, errors.New("obfs: not RTP v2")
	}

	// Extract RTP fields for nonce
	seq := binary.BigEndian.Uint16(wire[2:4])
	ts := binary.BigEndian.Uint32(wire[4:8])
	ssrc := binary.BigEndian.Uint32(wire[8:12])

	// Handle padding (P bit)
	payloadEnd := len(wire)
	if wire[0]&0x20 != 0 {
		padLen := int(wire[len(wire)-1])
		if padLen == 0 || padLen > payloadEnd-12 {
			return 0, fmt.Errorf("obfs: invalid padding length %d", padLen)
		}
		payloadEnd -= padLen
	}

	ciphertextLen := payloadEnd - 12
	if ciphertextLen <= chacha20poly1305.Overhead {
		return 0, errors.New("obfs: no payload after stripping header/padding")
	}
	if ciphertextLen-chacha20poly1305.Overhead > len(dst) {
		return 0, errors.New("obfs: dst buffer too small")
	}

	// Build nonce and decrypt
	var nonce [12]byte
	nonceBytes := obfsBuildNonce(&nonce, ssrc, seq, ts)
	plain, err := aead.Open(dst[:0], nonceBytes, wire[12:payloadEnd], wire[:12])
	if err != nil {
		return 0, fmt.Errorf("obfs: auth: %w", err)
	}

	return len(plain), nil
}

// ─── Detection ───

// obfsIsRTPPacket checks if a raw UDP packet looks like our obfuscated RTP.
// Used by the server and client to reject non-obfuscated packets.
func obfsIsRTPPacket(wire []byte) bool {
	if len(wire) < 13 {
		return false
	}
	// RTP version must be 2
	if (wire[0] >> 6) != 2 {
		return false
	}
	// Our payload type = 111
	pt := wire[1] & 0x7F
	return pt == 111
}
