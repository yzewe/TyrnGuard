//go:build windows

package main

import (
	"net"

	"golang.zx2c4.com/wireguard/ipc"
)

func listenUAPI(ifaceName string) (net.Listener, error) {
	return ipc.UAPIListen(ifaceName)
}
