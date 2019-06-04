package com.megaease.easeagent.core.mbean;

public class CallTraceSetting implements CallTraceSettingMBean{

    public static volatile boolean enabled = true;

    @Override
    public void enable() {
        enabled = true;
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

