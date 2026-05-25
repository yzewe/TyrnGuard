package main

import "testing"

func TestDrainWorkerQueueReturnsPendingPackets(t *testing.T) {
	slot := &WorkerSlot{
		ID:     1,
		SendCh: make(chan []byte, 3),
	}
	slot.SendCh <- getPacketBuffer(32)
	slot.SendCh <- getPacketBuffer(64)

	if drained := drainWorkerQueue(slot); drained != 2 {
		t.Fatalf("drainWorkerQueue() = %d, want 2", drained)
	}
	if drained := drainWorkerQueue(slot); drained != 0 {
		t.Fatalf("second drainWorkerQueue() = %d, want 0", drained)
	}
}
