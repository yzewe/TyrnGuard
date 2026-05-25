package anet

import (
	"errors"
	"net"
)

var errInvalidInterface = errors.New("invalid network interface")

func Interfaces() ([]net.Interface, error) {
	return net.Interfaces()
}

func InterfaceAddrs() ([]net.Addr, error) {
	return net.InterfaceAddrs()
}

func InterfaceByIndex(index int) (*net.Interface, error) {
	return net.InterfaceByIndex(index)
}

func InterfaceByName(name string) (*net.Interface, error) {
	return net.InterfaceByName(name)
}

func InterfaceAddrsByInterface(ifi *net.Interface) ([]net.Addr, error) {
	if ifi == nil {
		return nil, &net.OpError{Op: "route", Net: "ip+net", Err: errInvalidInterface}
	}
	return ifi.Addrs()
}

func SetAndroidVersion(version uint) {}
