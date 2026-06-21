package com.traffic.wecross.protocol;

public class TrafficNode {
    public int nodeId;
    public String name;
    public String role;

    public TrafficNode(int id, String name, String role) {
        this.nodeId = id;
        this.name = name;
        this.role = role;
    }
}
