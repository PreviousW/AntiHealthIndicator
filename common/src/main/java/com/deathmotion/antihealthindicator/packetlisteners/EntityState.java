/*
 * This file is part of AntiHealthIndicator - https://github.com/Bram1903/AntiHealthIndicator
 * Copyright (C) 2024 Bram and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.deathmotion.antihealthindicator.packetlisteners;

import com.deathmotion.antihealthindicator.AHIPlatform;
import com.deathmotion.antihealthindicator.data.RidableEntities;
import com.deathmotion.antihealthindicator.data.cache.LivingEntityData;
import com.deathmotion.antihealthindicator.data.cache.RidableEntityData;
import com.deathmotion.antihealthindicator.data.cache.WolfData;
import com.deathmotion.antihealthindicator.enums.ConfigOption;
import com.deathmotion.antihealthindicator.managers.CacheManager;
import com.deathmotion.antihealthindicator.util.MetadataIndex;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.*;

import java.util.Collections;
import java.util.List;

/**
 * Listens for EntityState events and manages the caching of various entity state details.
 *
 * @param <P> The platform type.
 */
public class EntityState<P> implements PacketListener {
    private final AHIPlatform<P> platform;
    private final CacheManager<P> cacheManager;

    private final boolean playersOnly;
    private final boolean isBypassEnabled;

    /**
     * Constructs a new EntityState with the specified {@link AHIPlatform}.
     *
     * @param platform The platform to use.
     */
    public EntityState(AHIPlatform<P> platform) {
        this.platform = platform;
        this.cacheManager = platform.getCacheManager();

        this.playersOnly = platform.getConfigurationOption(ConfigOption.PLAYER_ONLY);
        this.isBypassEnabled = platform.getConfigurationOption(ConfigOption.ALLOW_BYPASS_ENABLED);

        platform.getLogManager().debug("Entity State listener has been set up.");
    }

    /**
     * This function is called when an {@link PacketSendEvent} is triggered.
     * Manages the state of various entities based on the event triggered.
     *
     * @param event The event that has been triggered.
     */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        final PacketTypeCommon type = event.getPacketType();

        if (playersOnly) {
            if (PacketType.Play.Server.JOIN_GAME == type) {
                handleJoinGame(new WrapperPlayServerJoinGame(event));
            }
        } else {
            if (PacketType.Play.Server.SPAWN_LIVING_ENTITY == type) {
                handleSpawnLivingEntity(new WrapperPlayServerSpawnLivingEntity(event));
            } else if (PacketType.Play.Server.SPAWN_ENTITY == type) {
                handleSpawnEntity(new WrapperPlayServerSpawnEntity(event));
            } else if (PacketType.Play.Server.JOIN_GAME == type) {
                handleJoinGame(new WrapperPlayServerJoinGame(event));
            } else if (PacketType.Play.Server.ENTITY_METADATA == type) {
                handleEntityMetadata(new WrapperPlayServerEntityMetadata(event), event.getUser());
            } else if (PacketType.Play.Server.SET_PASSENGERS == type) {
                handleSetPassengers(new WrapperPlayServerSetPassengers(event), event.getUser());
            } else if (PacketType.Play.Server.ATTACH_ENTITY == type) {
                handleAttachEntity(new WrapperPlayServerAttachEntity(event), event.getUser());
            }
        }
    }

    private void handleSpawnLivingEntity(WrapperPlayServerSpawnLivingEntity packet) {
        int entityId = packet.getEntityId();
        EntityType entityType = packet.getEntityType();

        LivingEntityData entityData = createLivingEntity(entityType);
        cacheManager.addLivingEntity(entityId, entityData);
    }

    private void handleSpawnEntity(WrapperPlayServerSpawnEntity packet) {
        EntityType entityType = packet.getEntityType();

        if (EntityTypes.isTypeInstanceOf(entityType, EntityTypes.LIVINGENTITY)) {
            int entityId = packet.getEntityId();

            LivingEntityData entityData = createLivingEntity(entityType);
            cacheManager.addLivingEntity(entityId, entityData);
        }
    }

    private void handleJoinGame(WrapperPlayServerJoinGame packet) {
        LivingEntityData livingEntityData = new LivingEntityData();
        livingEntityData.setEntityType(EntityTypes.PLAYER);

        cacheManager.addLivingEntity(packet.getEntityId(), livingEntityData);
    }

    private void handleEntityMetadata(WrapperPlayServerEntityMetadata packet, User user) {
        int entityId = packet.getEntityId();

        LivingEntityData entityData = cacheManager.getLivingEntityData(entityId).orElse(null);
        if (entityData == null) return;

        packet.getEntityMetadata().forEach(metaData -> {
            entityData.processMetaData(metaData, user);
        });
    }

    private void handleSetPassengers(WrapperPlayServerSetPassengers packet, User user) {
        int entityId = packet.getEntityId();
        if (entityId == user.getEntityId()) return;

        int[] passengers = packet.getPassengers();

        if (passengers.length > 0) {
            cacheManager.updateVehiclePassenger(entityId, passengers[0]);
            handlePassengerEvent(user, entityId, cacheManager.getVehicleHealth(entityId), true);
        } else {
            int passengerId = cacheManager.getPassengerId(entityId);
            cacheManager.updateVehiclePassenger(entityId, -1);

            if (user.getEntityId() == passengerId) {
                handlePassengerEvent(user, entityId, 0.5F, false);
            }
        }
    }

    private void handleAttachEntity(WrapperPlayServerAttachEntity packet, User user) {
        int entityId = packet.getHoldingId();
        if (entityId == user.getEntityId()) return;

        int passengerId = packet.getAttachedId();

        if (entityId > 0) {
            cacheManager.updateVehiclePassenger(entityId, passengerId);
            handlePassengerEvent(user, entityId, cacheManager.getVehicleHealth(entityId), true);
        } else {
            // With the Entity Attach packet, the entity ID is set to -1 when the entity is detached;
            // Thus we need to retrieve the vehicle we stepped of by using a reverse lookup by passenger ID
            int reversedEntityId = cacheManager.getEntityIdByPassengerId(passengerId);
            cacheManager.updateVehiclePassenger(reversedEntityId, -1);

            if (user.getEntityId() == passengerId) {
                handlePassengerEvent(user, reversedEntityId, 0.5F, false);
            }
        }
    }

    private LivingEntityData createLivingEntity(EntityType entityType) {
        LivingEntityData entityData;

        if (EntityTypes.isTypeInstanceOf(entityType, EntityTypes.WOLF)) {
            entityData = new WolfData();
        } else if (RidableEntities.RIDABLE_ENTITY_TYPES.contains(entityType)) {
            entityData = new RidableEntityData();
        } else {
            entityData = new LivingEntityData();
        }

        entityData.setEntityType(entityType);
        return entityData;
    }

    private void handlePassengerEvent(User user, int vehicleId, float healthValue, boolean entering) {
        platform.getScheduler().runAsyncTask((o) -> {
            if (!entering && isBypassEnabled) {
                if (platform.hasPermission(user.getUUID(), "AntiHealthIndicator.Bypass")) return;
            }

            List<EntityData> metadata = Collections.singletonList(new EntityData(MetadataIndex.HEALTH, EntityDataTypes.FLOAT, healthValue));
            user.sendPacketSilently(new WrapperPlayServerEntityMetadata(vehicleId, metadata));
        });
    }
}