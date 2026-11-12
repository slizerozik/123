package com.slizerozik.demoncow.item;

import com.slizerozik.demoncow.DemonCowMod;
import com.slizerozik.demoncow.entity.SummonPortal;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Ringing the bell opens a giant portal in front of the player, and the demon cow comes out of it. */
public class CowBellItem extends Item {
    private static final double SUMMON_DISTANCE = 48.0;

    public CowBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = Vec3.directionFromRotation(0, player.getYRot());
            }
            flat = flat.normalize();
            double x = player.getX() + flat.x * SUMMON_DISTANCE;
            double z = player.getZ() + flat.z * SUMMON_DISTANCE;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));

            SummonPortal portal = DemonCowMod.SUMMON_PORTAL.get().create(level);
            if (portal != null) {
                // the portal faces back towards the player
                float yaw = (float) (Mth.atan2(flat.x, -flat.z) * Mth.RAD_TO_DEG);
                portal.moveTo(x, ground + SummonPortal.CENTER_HEIGHT, z, yaw, 0);
                level.addFreshEntity(portal);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3.0F, 0.6F);
            player.getCooldowns().addCooldown(this, 200);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.demoncow.cow_bell.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
