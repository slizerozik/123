package com.slizerozik.demoncow.entity;

import com.slizerozik.demoncow.DemonCowMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** The summoning ritual and the giant portal the demon cow walks out of. Purely a set piece: it cannot be hit. */
public class SummonPortal extends Entity {
    public static final float RADIUS = 20.0F;
    public static final int CENTER_HEIGHT = 23;
    public static final int OPEN_START = 100;
    public static final int OPEN_END = 160;
    public static final int CLOSE_TIME = 40;

    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SummonPortal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOSING = SynchedEntityData.defineId(SummonPortal.class, EntityDataSerializers.INT);

    @Nullable
    private UUID cowId;
    private final List<FallingBlockEntity> floating = new ArrayList<>();

    public SummonPortal(EntityType<? extends SummonPortal> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AGE, 0);
        builder.define(CLOSING, -1);
    }

    public int getAge() {
        return this.entityData.get(AGE);
    }

    /** 0 = closed, 1 = fully open. */
    public float getOpenScale(float partialTick) {
        float scale = Mth.clamp((this.getAge() + partialTick - OPEN_START) / (OPEN_END - OPEN_START), 0.0F, 1.0F);
        int closing = this.entityData.get(CLOSING);
        if (closing >= 0) {
            scale *= Mth.clamp(1.0F - (closing + partialTick) / CLOSE_TIME, 0.0F, 1.0F);
        }
        return scale;
    }

    public Vec3 forward() {
        return Vec3.directionFromRotation(0, this.getYRot());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        int age = this.getAge() + 1;
        this.entityData.set(AGE, age);
        this.steerFloatingBlocks(age);

        if (age < OPEN_END) {
            this.tickRitual(level, age);
        } else if (age == OPEN_END) {
            this.spawnCow(level);
        } else {
            if (age % 60 == 0) {
                this.sound(SoundEvents.PORTAL_AMBIENT, 6.0F, 0.5F);
            }
            int closing = this.entityData.get(CLOSING);
            if (closing >= 0) {
                if (closing >= CLOSE_TIME) {
                    this.discard();
                } else {
                    this.entityData.set(CLOSING, closing + 1);
                }
            } else if (age % 10 == 0) {
                Entity cow = this.cowId == null ? null : level.getEntity(this.cowId);
                if (cow == null || !cow.isAlive()) {
                    this.entityData.set(CLOSING, 0);
                    this.sound(SoundEvents.BEACON_DEACTIVATE, 8.0F, 0.5F);
                }
            }
        }
    }

    private void tickRitual(ServerLevel level, int age) {
        double groundY = this.getY() - CENTER_HEIGHT;
        if (age == 1) {
            this.sound(SoundEvents.PORTAL_TRIGGER, 8.0F, 0.5F);
        }
        if (age == OPEN_START) {
            this.sound(SoundEvents.END_PORTAL_SPAWN, 8.0F, 0.6F);
        }
        // smoke bursts all over the place
        if (age % 4 == 0) {
            double x = this.getX() + this.random.triangle(0, 38);
            double y = groundY + this.random.nextDouble() * 30;
            double z = this.getZ() + this.random.triangle(0, 38);
            sendFar(level, ParticleTypes.EXPLOSION, x, y, z, 3, 1.5, 1.5, 1.5, 0);
            sendFar(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 10, 1.5, 1.5, 1.5, 0.02);
            sendFar(level, ParticleTypes.LARGE_SMOKE, x, y, z, 12, 2, 2, 2, 0.05);
            level.playSound(null, x, y, z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 6.0F, 0.6F + this.random.nextFloat() * 0.4F);
        }
        if (age % 18 == 9) {
            int x = Mth.floor(this.getX() + this.random.triangle(0, 30));
            int z = Mth.floor(this.getZ() + this.random.triangle(0, 30));
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        // rip blocks out of the ground and pull them into the portal
        if (age >= 50 && age % 3 == 0 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            int x = Mth.floor(this.getX() + this.random.triangle(0, 16));
            int z = Mth.floor(this.getZ() + this.random.triangle(0, 16));
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            BlockState state = level.getBlockState(pos);
            if (canLift(level, pos, state)) {
                FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
                block.setNoGravity(true);
                block.disableDrop();
                block.setDeltaMovement(0, 0.5, 0);
                this.floating.add(block);
            }
        }
    }

    static boolean canLift(Level level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0;
    }

    private void steerFloatingBlocks(int age) {
        Iterator<FallingBlockEntity> it = this.floating.iterator();
        while (it.hasNext()) {
            FallingBlockEntity block = it.next();
            Vec3 toCenter = this.position().subtract(block.position());
            if (!block.isAlive() || toCenter.lengthSqr() < 16 || block.tickCount > 120) {
                block.discard();
                it.remove();
            } else if (block.tickCount % 10 == 5) {
                block.setDeltaMovement(toCenter.normalize().scale(0.7));
                block.hasImpulse = true;
            }
        }
    }

    private void spawnCow(ServerLevel level) {
        DemonCow cow = DemonCowMod.DEMON_COW.get().create(level);
        if (cow == null) {
            this.discard();
            return;
        }
        Vec3 start = this.position().subtract(this.forward().scale(12));
        cow.moveTo(start.x, this.getY() - 8, start.z, this.getYRot(), 0);
        cow.yBodyRot = this.getYRot();
        cow.yHeadRot = this.getYRot();
        cow.startEmerging(80);
        level.addFreshEntity(cow);
        this.cowId = cow.getUUID();
        this.sound(SoundEvents.WITHER_SPAWN, 10.0F, 0.6F);
        this.sound(SoundEvents.COW_AMBIENT, 10.0F, 0.4F);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Vanilla only shows particles within 32 blocks; everything here happens much further away. */
    static void sendFar(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(AGE, tag.getInt("Age"));
        this.entityData.set(CLOSING, tag.contains("Closing") ? tag.getInt("Closing") : -1);
        this.cowId = tag.hasUUID("Cow") ? tag.getUUID("Cow") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.getAge());
        tag.putInt("Closing", this.entityData.get(CLOSING));
        if (this.cowId != null) {
            tag.putUUID("Cow", this.cowId);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
