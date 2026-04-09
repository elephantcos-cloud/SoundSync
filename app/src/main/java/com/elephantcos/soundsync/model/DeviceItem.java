package com.elephantcos.soundsync.model;

public class DeviceItem {
    public String name;
    public String address;
    public int status; // 0=available 1=connecting 2=connected

    public DeviceItem(String name, String address, int status) {
        this.name    = name;
        this.address = address;
        this.status  = status;
    }
}
