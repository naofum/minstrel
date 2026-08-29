package com.github.naofum.minstrel.plugins.commander;

public class StorageInfo {

    public String path;
    public boolean readonly;
    public boolean removable;
    public int number;

    public StorageInfo(String path, boolean readonly, boolean removable, int number) {
        this.path = path;
        this.readonly = readonly;
        this.removable = removable;
        this.number = number;
    }
}