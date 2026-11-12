package com.slizerozik.demoncow;

import com.slizerozik.demoncow.entity.DemonCow;
import com.slizerozik.demoncow.entity.SummonPortal;
import com.slizerozik.demoncow.item.CowBellItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DemonCowMod.MODID)
public class DemonCowMod {
    public static final String MODID = "demoncow";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredItem<CowBellItem> COW_BELL = ITEMS.register("cow_bell",
            () -> new CowBellItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredHolder<EntityType<?>, EntityType<DemonCow>> DEMON_COW = ENTITIES.register("demon_cow",
            () -> EntityType.Builder.of(DemonCow::new, MobCategory.MONSTER)
                    .sized(8.0F, 14.0F).eyeHeight(13.0F).fireImmune().clientTrackingRange(20).updateInterval(1)
                    .build("demon_cow"));

    public static final DeferredHolder<EntityType<?>, EntityType<SummonPortal>> SUMMON_PORTAL = ENTITIES.register("summon_portal",
            () -> EntityType.Builder.<SummonPortal>of(SummonPortal::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(20).updateInterval(20)
                    .build("summon_portal"));

    public DemonCowMod(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::addCreative);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DEMON_COW.get(), DemonCow.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(COW_BELL);
        }
    }
}
