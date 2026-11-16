package com.v.island;

interface IShellService {
    String execute(String command);
    String setHotspot(boolean isOn);
    boolean setBrightness(float brightness, boolean isFinal);
}
