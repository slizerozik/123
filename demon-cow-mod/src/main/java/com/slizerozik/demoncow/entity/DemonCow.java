package com.slizerozik.demoncow.entity;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Giant hovering cow. Comes out of the portal calm and on four legs; the first hit enrages it:
 * it rears up and cycles through eye lasers, a milk beam and a ground slam.
 */
public class DemonCow extends Monster {
    /** Model scale; the renderer and the anchor points below both depend on it. */
    public static final float SCALE = 10.0F;
    public static final int BEAM_NONE = 0;
    public static final int BEAM_LASER = 1;
    public static final int BEAM_MILK = 2;

    private static final EntityDataAccessor<Boolean> STANDING = SynchedEntityData.defineId(DemonCow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BEAM = SynchedEntityData.defineId(DemonCow.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM_TARGET = SynchedEntityData.defineId(DemonCow.class, EntityDataSerializers.VECTOR3);

    private static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable(8.0F, 22.0F).withEyeHeight(19.0F);
    private static final int ATTACK_NONE = 0, ATTACK_LASER = 1, ATTACK_MILK = 2, ATTACK_SLAM = 3;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.demoncow.demon_cow"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    /** Client-side blend between the four-legged (0) and upright (1) pose. */
    public float standAnim, standAnimO;

    private boolean enraged;
    private int emergeTicks;
    private int attack = ATTACK_NONE;
    private int attackTicks;
    private int attackCooldown = 60;
    private int nextAttack = ATTACK_LASER;
    private int calmTicks;
    private Vec3 aim = Vec3.ZERO;

    public DemonCow(EntityType<? extends DemonCow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
        this.xpReward = 500;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 128.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STANDING, false);
        builder.define(BEAM, BEAM_NONE);
        builder.define(BEAM_TARGET, new Vector3f());
    }

    public boolean isStanding() {
        return this.entityData.get(STANDING);
    }

    public int getBeam() {
        return this.entityData.get(BEAM);
    }

    public Vector3f getBeamTarget() {
        return this.entityData.get(BEAM_TARGET);
    }

    public void startEmerging(int ticks) {
        this.emergeTicks = ticks;
    }

    // --- anchor points, shared by the server logic and the beam renderer ---

    private static Vec3 anchor(Vec3 pos, float yawDeg, double up, double forward, double side) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        return new Vec3(pos.x + fx * forward + fz * side, pos.y + up, pos.z + fz * forward - fx * side);
    }

    /** side: -1 or 1. */
    public static Vec3 eyePos(Vec3 pos, float yawDeg, float stand, int side) {
        return anchor(pos, yawDeg, Mth.lerp(stand, 13.4, 19.0), Mth.lerp(stand, 8.9, 4.5), side * 1.56);
    }

    public static Vec3 udderPos(Vec3 pos, float yawDeg, float stand) {
        return anchor(pos, yawDeg, Mth.lerp(stand, 6.4, 9.0), Mth.lerp(stand, -2.5, 4.2), 0);
    }

    private float standValue() {
        return this.isStanding() ? 1.0F : 0.0F;
    }

    // --- ticking ---

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.standAnimO = this.standAnim;
            this.standAnim = Mth.approach(this.standAnim, this.standValue(), 0.04F);
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        // free flight: velocity is fully driven by customServerAiStep
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.calculateEntityAnimation(false);
    }

    @Override
    protected void customServerAiStep() {
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        ServerLevel level = (ServerLevel) this.level();

        if (this.emergeTicks > 0) {
            this.emergeTicks--;
            Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
            this.setDeltaMovement(forward.scale(0.35));
            if (this.emergeTicks % 20 == 0) {
                this.sound(SoundEvents.COW_AMBIENT, 10.0F, 0.4F);
            }
            return;
        }

        Player target = this.findTarget();
        if (!this.enraged) {
            this.hover(this.position(), 5.0, 0.2);
            if (target != null) {
                this.face(target.position(), 2.0F);
            }
            if (this.random.nextInt(160) == 0) {
                this.sound(SoundEvents.COW_AMBIENT, 8.0F, 0.4F);
            }
            return;
        }

        if (target == null) {
            this.stopAttack();
            this.hover(this.position(), 5.0, 0.2);
            if (++this.calmTicks > 400) {
                this.enraged = false;
                this.entityData.set(STANDING, false);
            }
            return;
        }
        this.calmTicks = 0;
        this.face(target.position(), 5.0F);

        if (this.attack == ATTACK_NONE) {
            this.keepDistance(target, 26.0);
            if (--this.attackCooldown <= 0) {
                this.startAttack(target);
            }
            return;
        }

        this.attackTicks++;
        switch (this.attack) {
            case ATTACK_LASER -> this.tickLaser(level, target);
            case ATTACK_MILK -> this.tickMilk(level, target);
            case ATTACK_SLAM -> this.tickSlam(level, target);
            default -> this.stopAttack();
        }
    }

    @Nullable
    private Player findTarget() {
        Player player = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 128.0, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        // with nobody to hurt, still put on a show for a creative player
        return player != null ? player : this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 128.0, EntitySelector.NO_SPECTATORS);
    }

    private double groundY(double x, double z) {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    private void flyTo(Vec3 dest, double maxSpeed) {
        Vec3 delta = dest.subtract(this.position());
        double dist = delta.length();
        Vec3 wanted = dist < 0.3 ? Vec3.ZERO : delta.scale(Math.min(maxSpeed, dist * 0.1) / dist);
        this.setDeltaMovement(this.getDeltaMovement().lerp(wanted, 0.15));
    }

    private void hover(Vec3 over, double height, double speed) {
        double bob = Mth.sin(this.tickCount * 0.06F) * 0.8;
        this.flyTo(new Vec3(over.x, this.groundY(over.x, over.z) + height + bob, over.z), speed);
    }

    private void keepDistance(Player target, double distance) {
        Vec3 away = new Vec3(this.getX() - target.getX(), 0, this.getZ() - target.getZ());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        this.hover(target.position().add(away.scale(distance)), 4.0, 0.45);
    }

    private void face(Vec3 pos, float maxTurn) {
        double dx = pos.x - this.getX(), dz = pos.z - this.getZ();
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(Mth.approachDegrees(this.getYRot(), wanted, maxTurn));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    // --- attacks ---

    private void startAttack(Player target) {
        this.attack = this.nextAttack;
        this.nextAttack = this.nextAttack % 3 + 1;
        this.attackTicks = 0;
        if (this.attack == ATTACK_LASER) {
            // start beside the target and sweep onto it
            Vec3 side = new Vec3(this.random.nextBoolean() ? 14 : -14, 0, this.random.nextBoolean() ? 14 : -14);
            this.aim = target.position().add(side);
            this.sound(SoundEvents.WARDEN_SONIC_CHARGE, 10.0F, 0.7F);
        } else if (this.attack == ATTACK_MILK) {
            this.aim = target.position();
            this.sound(SoundEvents.COW_HURT, 10.0F, 0.4F);
        } else {
            this.sound(SoundEvents.RAVAGER_ROAR, 10.0F, 0.5F);
        }
    }

    private void stopAttack() {
        this.attack = ATTACK_NONE;
        this.attackCooldown = 30 + this.random.nextInt(20);
        this.entityData.set(BEAM, BEAM_NONE);
    }

    /** Extends the ray from `from` through `through` until it hits a block. */
    private Vec3 beamEnd(Vec3 from, Vec3 through) {
        Vec3 dir = through.subtract(from).normalize();
        HitResult hit = this.level().clip(new ClipContext(from, from.add(dir.scale(160)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getLocation();
    }

    private void tickLaser(ServerLevel level, Player target) {
        this.keepDistance(target, 30.0);
        Vec3 toTarget = target.position().subtract(this.aim);
        double dist = toTarget.length();
        if (dist > 0.01) {
            this.aim = this.aim.add(toTarget.scale(Math.min(0.75, dist) / dist));
        }
        if (this.attackTicks < 15) {
            return;
        }
        Vec3 eyes = eyePos(this.position(), this.getYRot(), this.standValue(), 0);
        Vec3 end = this.beamEnd(eyes, this.aim);
        this.entityData.set(BEAM, BEAM_LASER);
        this.entityData.set(BEAM_TARGET, end.toVector3f());

        SummonPortal.sendFar(level, ParticleTypes.FLAME, end.x, end.y + 0.5, end.z, 6, 0.8, 0.5, 0.8, 0.08);
        SummonPortal.sendFar(level, ParticleTypes.LAVA, end.x, end.y + 0.5, end.z, 2, 0.8, 0.3, 0.8, 0);
        if (this.attackTicks % 2 == 0) {
            for (LivingEntity victim : this.victims(this.aim, 2.5, end, 3.0)) {
                if (victim.hurt(this.damageSources().mobAttack(this), 6.0F)) {
                    victim.igniteForSeconds(4.0F);
                }
            }
        }
        if (this.attackTicks % 8 == 0) {
            level.explode(this, end.x, end.y + 0.5, end.z, 2.5F, true, Level.ExplosionInteraction.MOB);
            this.sound(SoundEvents.FIRECHARGE_USE, 8.0F, 0.5F);
        }
        if (this.attackTicks >= 75) {
            this.stopAttack();
        }
    }

    private void tickMilk(ServerLevel level, Player target) {
        this.keepDistance(target, 24.0);
        if (this.attackTicks < 12) {
            // still aiming
            this.aim = target.position();
            Vec3 udder = udderPos(this.position(), this.getYRot(), this.standValue());
            SummonPortal.sendFar(level, ParticleTypes.CHERRY_LEAVES, udder.x, udder.y, udder.z, 6, 1.5, 1.5, 1.5, 0);
            return;
        }
        Vec3 udder = udderPos(this.position(), this.getYRot(), this.standValue());
        Vec3 end = this.beamEnd(udder, this.aim);
        this.entityData.set(BEAM, BEAM_MILK);
        this.entityData.set(BEAM_TARGET, end.toVector3f());
        if (this.attackTicks == 12) {
            this.sound(SoundEvents.WARDEN_SONIC_BOOM, 10.0F, 1.2F);
            level.explode(this, end.x, end.y + 0.5, end.z, 3.0F, false, Level.ExplosionInteraction.MOB);
        }
        SummonPortal.sendFar(level, ParticleTypes.SNOWFLAKE, end.x, end.y + 1, end.z, 20, 2.5, 1.0, 2.5, 0.25);
        SummonPortal.sendFar(level, ParticleTypes.CHERRY_LEAVES, end.x, end.y + 2, end.z, 8, 3, 2, 3, 0);
        SummonPortal.sendFar(level, ParticleTypes.CLOUD, end.x, end.y + 0.5, end.z, 6, 2, 0.3, 2, 0.15);
        if (this.attackTicks % 5 == 2) {
            for (LivingEntity victim : this.victims(this.aim, 3.0, end, 7.0)) {
                victim.hurt(this.damageSources().mobAttack(this), 8.0F);
                this.launch(victim, end, 1.2, 0.7);
            }
        }
        if (this.attackTicks >= 45) {
            this.stopAttack();
        }
    }

    private void tickSlam(ServerLevel level, Player target) {
        if (this.attackTicks < 30) {
            // dive at the target
            this.flyTo(target.position().add(0, 1, 0), 1.3);
            if (this.attackTicks < 29 && this.distanceToSqr(target) > 14 * 14) {
                return;
            }
            this.attackTicks = 30;
        }
        if (this.attackTicks == 30) {
            Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
            double x = this.getX() + forward.x * 5, z = this.getZ() + forward.z * 5;
            Vec3 center = new Vec3(x, Math.min(this.groundY(x, z), this.getY() + 4), z);
            this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 10.0F, 0.5F);
            this.sound(SoundEvents.ENDER_DRAGON_GROWL, 10.0F, 0.7F);
            SummonPortal.sendFar(level, ParticleTypes.SWEEP_ATTACK, center.x, center.y + 2, center.z, 40, 6, 2.5, 6, 0);
            SummonPortal.sendFar(level, ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 2, 3, 0, 3, 0);
            level.explode(this, center.x, center.y + 1, center.z, 4.0F, false, Level.ExplosionInteraction.MOB);
            for (LivingEntity victim : this.victims(center, 14.0, center, 0)) {
                victim.hurt(this.damageSources().mobAttack(this), 14.0F);
                this.launch(victim, center, 1.6, 1.3);
            }
            this.throwBlocks(level, center);
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        if (this.attackTicks >= 50) {
            this.stopAttack();
        }
    }

    private List<LivingEntity> victims(Vec3 a, double radiusA, Vec3 b, double radiusB) {
        AABB box = new AABB(a, a).inflate(radiusA).minmax(new AABB(b, b).inflate(radiusB));
        return this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive()
                && (e.position().distanceToSqr(a) < radiusA * radiusA || e.position().distanceToSqr(b) < radiusB * radiusB));
    }

    private void launch(LivingEntity victim, Vec3 from, double horizontal, double up) {
        Vec3 dir = new Vec3(victim.getX() - from.x, 0, victim.getZ() - from.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, this.getYRot()) : dir.normalize();
        victim.push(dir.x * horizontal, up, dir.z * horizontal);
        victim.hurtMarked = true;
    }

    private void throwBlocks(ServerLevel level, Vec3 center) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        for (int i = 0; i < 14; i++) {
            int x = Mth.floor(center.x + this.random.triangle(0, 8));
            int z = Mth.floor(center.z + this.random.triangle(0, 8));
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            BlockState state = level.getBlockState(pos);
            if (SummonPortal.canLift(level, pos, state)) {
                FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
                block.dropItem = false;
                block.setDeltaMovement(this.random.triangle(0, 0.5), 0.6 + this.random.nextDouble() * 0.6, this.random.triangle(0, 0.5));
                block.hasImpulse = true;
            }
        }
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY() + 8, this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // --- damage and state ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() == this || source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == null) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && !this.enraged && this.isAlive() && source.getEntity() instanceof LivingEntity) {
            this.enrage();
        }
        return hurt;
    }

    private void enrage() {
        this.enraged = true;
        this.emergeTicks = 0;
        this.attackCooldown = 50;
        this.entityData.set(STANDING, true);
        this.sound(SoundEvents.ENDER_DRAGON_GROWL, 10.0F, 0.6F);
        this.sound(SoundEvents.COW_HURT, 10.0F, 0.3F);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            this.entityData.set(BEAM, BEAM_NONE);
            this.sound(SoundEvents.WITHER_DEATH, 10.0F, 0.6F);
            SummonPortal.sendFar(level, ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 8, this.getZ(), 12, 5, 8, 5, 0);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STANDING.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isStanding() ? STANDING_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", this.enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.enraged = tag.getBoolean("Enraged");
        this.entityData.set(STANDING, this.enraged);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COW_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 8.0F;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }
}
