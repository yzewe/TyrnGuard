package anet

import (
	"errors"
	"net"
)

var errInvalidInterface = errors.New("invalid network interface")

func Interfaces() ([]net.Interface, error) {
	ifs, err := net.Interfaces()
	if err != nil {
		return nil, nil
	}
	return ifs, nil
}

func InterfaceAddrs() ([]net.Addr, error) {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return nil, nil
	}
	return addrs, nil
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
	addrs, err := ifi.Addrs()
	if err != nil {
		return nil, nil
	}
	return addrs, nil
}

func SetAndroidVersion(version uint) {}
