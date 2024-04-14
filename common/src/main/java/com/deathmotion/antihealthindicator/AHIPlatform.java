package com.deathmotion.antihealthindicator;

import com.deathmotion.antihealthindicator.enums.ConfigOption;
import com.deathmotion.antihealthindicator.managers.CacheManager;
import com.deathmotion.antihealthindicator.managers.PacketManager;
import com.deathmotion.antihealthindicator.managers.UpdateManager;
import com.deathmotion.antihealthindicator.wrappers.PlatformLoggerWrapperImpl;
import com.deathmotion.antihealthindicator.wrappers.interfaces.Scheduler;
import com.github.retrooper.packetevents.PacketEvents;
import lombok.Getter;

import java.util.UUID;

@Getter
public abstract class AHIPlatform<P> {

    private final PlatformLoggerWrapperImpl loggerWrapper = new PlatformLoggerWrapperImpl();
    protected Scheduler scheduler;
    private CacheManager cacheManager;

    public void commonOnLoad() {
        // Load common stuff
    }

    public void commonOnEnable() {
        cacheManager = new CacheManager();

        new UpdateManager<>(this);
        new PacketManager<>(this);
    }

    public void commonOnDisable() {
        PacketEvents.getAPI().terminate();
    }

    public abstract P getPlatform();

    public abstract boolean hasPermission(UUID sender, String permission);

    public abstract boolean getConfigurationOption(ConfigOption option);

    public abstract String getPluginVersion();
}