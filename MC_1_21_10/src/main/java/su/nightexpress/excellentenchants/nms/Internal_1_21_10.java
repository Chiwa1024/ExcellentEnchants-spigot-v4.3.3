package su.nightexpress.excellentenchants.nms;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_21_R6.CraftEquipmentSlot;
import org.bukkit.craftbukkit.v1_21_R6.CraftServer;
import org.bukkit.craftbukkit.v1_21_R6.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R6.block.CraftBlock;
import org.bukkit.craftbukkit.v1_21_R6.block.CraftBlockType;
import org.bukkit.craftbukkit.v1_21_R6.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftFishHook;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R6.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_21_R6.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_21_R6.util.CraftChatMessage;
import org.bukkit.craftbukkit.v1_21_R6.util.CraftNamespacedKey;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentenchants.api.ConfigBridge;
import su.nightexpress.excellentenchants.api.enchantment.*;
import su.nightexpress.excellentenchants.api.enchantment.bridge.FlameWalker;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.util.Reflex;
import su.nightexpress.nightcore.util.text.NightMessage;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;

public class Internal_1_21_10 implements EnchantNMS {

    private static final MinecraftServer             SERVER;
    private static final MappedRegistry<Enchantment> ENCHANTS;
    private static final MappedRegistry<Item>        ITEMS;

    private static final String REGISTRY_FROZEN_TAGS_FIELD = "j";
    private static final String REGISTRY_ALL_TAGS_FIELD    = "k";
    private static final String TAG_SET_UNBOUND_METHOD     = "a";
    private static final String TAG_SET_MAP_FIELD          = "val$map";

    static {
        SERVER = ((CraftServer) Bukkit.getServer()).getServer();
        ENCHANTS = (MappedRegistry<Enchantment>) SERVER.registryAccess().lookup(Registries.ENCHANTMENT).orElseThrow();
        ITEMS = (MappedRegistry<Item>) SERVER.registryAccess().lookup(Registries.ITEM).orElseThrow();
    }

    private final NightPlugin plugin;

    public Internal_1_21_10(@NotNull NightPlugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    private static <T> ResourceKey<T> getResourceKey(@NotNull Registry<T> registry, @NotNull String name) {
        return ResourceKey.create(registry.key(), ResourceLocation.withDefaultNamespace(name));
    }

    private static <T> TagKey<T> getTagKey(@NotNull Registry<T> registry, @NotNull String name) {
        return TagKey.create(registry.key(), ResourceLocation.withDefaultNamespace(name));
    }

    @SuppressWarnings("unchecked")
    @NotNull
    private static <T> Map<TagKey<T>, HolderSet.Named<T>> getFrozenTags(@NotNull MappedRegistry<T> registry) {
        return (Map<TagKey<T>, HolderSet.Named<T>>) Reflex.getFieldValue(registry, REGISTRY_FROZEN_TAGS_FIELD);
    }

    @NotNull
    private static <T> Object getAllTags(@NotNull MappedRegistry<T> registry) {
        return Reflex.getFieldValue(registry, REGISTRY_ALL_TAGS_FIELD);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    private static <T> Map<TagKey<T>, HolderSet.Named<T>> getTagsMap(@NotNull Object tagSet) {
        return new HashMap<>((Map<TagKey<T>, HolderSet.Named<T>>) Reflex.getFieldValue(tagSet, TAG_SET_MAP_FIELD));
    }

    @Override
    public void unfreezeRegistry() {
        unfreeze(ENCHANTS);
        unfreeze(ITEMS);
    }

    @Override
    public void freezeRegistry() {
        freeze(ITEMS);
        freeze(ENCHANTS);
    }

    private static <T> void unfreeze(@NotNull MappedRegistry<T> registry) {
        Reflex.setFieldValue(registry, "l", false);
        Reflex.setFieldValue(registry, "m", new IdentityHashMap<>());
    }

    private static <T> void freeze(@NotNull MappedRegistry<T> registry) {
        Object tagSet = getAllTags(registry);
        Map<TagKey<T>, HolderSet.Named<T>> tagsMap = getTagsMap(tagSet);
        Map<TagKey<T>, HolderSet.Named<T>> frozenTags = getFrozenTags(registry);

        tagsMap.forEach(frozenTags::putIfAbsent);

        unbound(registry);

        registry.freeze();

        frozenTags.forEach(tagsMap::putIfAbsent);

        Reflex.setFieldValue(tagSet, TAG_SET_MAP_FIELD, tagsMap);
        Reflex.setFieldValue(registry, REGISTRY_ALL_TAGS_FIELD, tagSet);
    }

    @SuppressWarnings("deprecation")
	private static <T> void unbound(@NotNull MappedRegistry<T> registry) {
        Class<?> tagSetClass = Reflex.getInnerClass(MappedRegistry.class.getName(), "TagSet");

        Method unboundMethod = Reflex.getMethod(tagSetClass, TAG_SET_UNBOUND_METHOD);
        Object unboundTagSet = Reflex.invokeMethod(unboundMethod, registry);

        Reflex.setFieldValue(registry, REGISTRY_ALL_TAGS_FIELD, unboundTagSet);
    }

    @Override
    public void addExclusives(@NotNull CustomEnchantment customEnchantment) {
        ResourceKey<Enchantment> enchantKey = getResourceKey(ENCHANTS, customEnchantment.getId());
        Enchantment enchantment = ENCHANTS.getValue(enchantKey);
        if (enchantment == null) {
            this.plugin.error(customEnchantment.getId() + ": Could not set exclusive item list. Enchantment is not registered.");
            return;
        }

        TagKey<Enchantment> exclusivesKey = getTagKey(ENCHANTS, "exclusive_set/" + customEnchantment.getId());

        customEnchantment.getDefinition().getConflicts().forEach(enchantId -> {
            ResourceKey<Enchantment> conflictKey = getResourceKey(ENCHANTS, enchantId);
            Holder.Reference<Enchantment> reference = ENCHANTS.get(conflictKey).orElse(null);
            if (reference == null) return;

            addInTag(exclusivesKey, reference);
        });
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public org.bukkit.enchantments.Enchantment registerEnchantment(@NotNull CustomEnchantment customEnchantment) {
        Definition customDefinition = customEnchantment.getDefinition();

        Component display = CraftChatMessage.fromJSON(NightMessage.asJson(customEnchantment.getFormattedName()));
        HolderSet.Named<Item> supportedItems = createItemsSet("enchant_supported", customEnchantment, customDefinition.getSupportedItems());
        HolderSet.Named<Item> primaryItems = createItemsSet("enchant_primary", customEnchantment, customDefinition.getPrimaryItems());
        int weight = customDefinition.getRarity().getWeight();
        int maxLevel = customDefinition.getMaxLevel();
        Enchantment.Cost minCost = nmsCost(customDefinition.getMinCost());
        Enchantment.Cost maxCost = nmsCost(customDefinition.getMaxCost());
        int anvilCost = customDefinition.getAnvilCost();
        EquipmentSlotGroup[] slots = nmsSlots(customDefinition);

        Enchantment.EnchantmentDefinition definition = Enchantment.definition(supportedItems, primaryItems, weight, maxLevel, minCost, maxCost, anvilCost, slots);
        HolderSet<Enchantment> exclusiveSet = createExclusiveSet(customEnchantment);
        DataComponentMap.Builder builder = DataComponentMap.builder();

        Enchantment enchantment = new Enchantment(display, definition, exclusiveSet, builder.build());

        Holder.Reference<Enchantment> reference = ENCHANTS.createIntrusiveHolder(enchantment);

        Registry.register(ENCHANTS, customEnchantment.getId(), enchantment);

        this.setupDistribution(customEnchantment, reference);

        return CraftEnchantment.minecraftToBukkit(enchantment);
    }

    private void setupDistribution(@NotNull CustomEnchantment customEnchantment, @NotNull Holder.Reference<Enchantment> reference) {
        boolean experimentalTrades = SERVER.getWorldData().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE);
        Distribution distribution = customEnchantment.getDistribution();

        if (distribution.isTreasure()) {
            addInTag(EnchantmentTags.TREASURE, reference);
            addInTag(EnchantmentTags.DOUBLE_TRADE_PRICE, reference);
        }
        else addInTag(EnchantmentTags.NON_TREASURE, reference);

        if (distribution.isOnRandomLoot() && ConfigBridge.isGlobalDistRandomLoot()) {
            addInTag(EnchantmentTags.ON_RANDOM_LOOT, reference);
        }

        if (!distribution.isTreasure()) {
            if (distribution.isOnMobSpawnEquipment() && ConfigBridge.isGlobalDistMobEquipment()) {
                addInTag(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT, reference);
            }

            if (distribution.isOnTradedEquipment() && ConfigBridge.isGlobalDistTradeEquipment()) {
                addInTag(EnchantmentTags.ON_TRADED_EQUIPMENT, reference);
            }
        }

        if (experimentalTrades) {
            if (distribution.isTradable() && ConfigBridge.isGlobalDistTrading()) {
                distribution.getTrades().forEach(tradeType -> {
                    addInTag(getTradeKey(tradeType), reference);
                });
            }
        }
        else {
            if (distribution.isTradable() && ConfigBridge.isGlobalDistTrading()) {
                addInTag(EnchantmentTags.TRADEABLE, reference);
            }
            else removeFromTag(EnchantmentTags.TRADEABLE, reference);
        }

        if (customEnchantment.isCurse()) {
            addInTag(EnchantmentTags.CURSE, reference);
        }
        else {
            if (!distribution.isTreasure()) {
                if (distribution.isDiscoverable() && ConfigBridge.isGlobalDistEnchanting()) {
                    addInTag(EnchantmentTags.IN_ENCHANTING_TABLE, reference);
                }
                else removeFromTag(EnchantmentTags.IN_ENCHANTING_TABLE, reference);
            }
        }
    }

    private void addInTag(@NotNull TagKey<Enchantment> tagKey, @NotNull Holder.Reference<Enchantment> reference) {
        modfiyTag(ENCHANTS, tagKey, reference, List::add);
    }

    private void removeFromTag(@NotNull TagKey<Enchantment> tagKey, @NotNull Holder.Reference<Enchantment> reference) {
        modfiyTag(ENCHANTS, tagKey, reference, List::remove);
    }

    private <T> void modfiyTag(@NotNull MappedRegistry<T> registry,
                               @NotNull TagKey<T> tagKey,
                               @NotNull Holder.Reference<T> reference,
                               @NotNull BiConsumer<List<Holder<T>>, Holder.Reference<T>> consumer) {

        HolderSet.Named<T> holders = registry.get(tagKey).orElse(null);
        if (holders == null) {
            this.plugin.warn(tagKey + ": Could not modify HolderSet. HolderSet is NULL.");
            return;
        }

        List<Holder<T>> contents = new ArrayList<>(holders.stream().toList());
        consumer.accept(contents, reference);

        registry.bindTag(tagKey, contents);
    }

    @NotNull
    @SuppressWarnings("deprecation")
    private static HolderSet.Named<Item> createItemsSet(@NotNull String prefix, @NotNull CustomEnchantment customEnchantment, @NotNull ItemsCategory category) {
        TagKey<Item> customKey = getTagKey(ITEMS, prefix + "/" + customEnchantment.getId());
        List<Holder<Item>> holders = new ArrayList<>();

        category.getMaterials().forEach(material -> {
            ResourceLocation location = CraftNamespacedKey.toMinecraft(material.getKey());
            Holder.Reference<Item> holder = ITEMS.get(location).orElse(null);
            if (holder == null) return;

            holders.add(holder);
        });

        ITEMS.bindTag(customKey, holders);

        return getFrozenTags(ITEMS).get(customKey);
    }

    @NotNull
    private static HolderSet.Named<Enchantment> createExclusiveSet(@NotNull CustomEnchantment customEnchantment) {
        TagKey<Enchantment> customKey = getTagKey(ENCHANTS, "exclusive_set/" + customEnchantment.getId());
        List<Holder<Enchantment>> holders = new ArrayList<>();

        ENCHANTS.bindTag(customKey, holders);

        return getFrozenTags(ENCHANTS).get(customKey);
    }

    @NotNull
    private static TagKey<Enchantment> getTradeKey(@NotNull TradeType tradeType) {
        return switch (tradeType) {
            case DESERT_COMMON -> EnchantmentTags.TRADES_DESERT_COMMON;
            case DESERT_SPECIAL -> EnchantmentTags.TRADES_DESERT_SPECIAL;
            case PLAINS_COMMON -> EnchantmentTags.TRADES_PLAINS_COMMON;
            case PLAINS_SPECIAL -> EnchantmentTags.TRADES_PLAINS_SPECIAL;
            case SAVANNA_COMMON -> EnchantmentTags.TRADES_SAVANNA_COMMON;
            case SAVANNA_SPECIAL -> EnchantmentTags.TRADES_SAVANNA_SPECIAL;
            case JUNGLE_COMMON -> EnchantmentTags.TRADES_JUNGLE_COMMON;
            case JUNGLE_SPECIAL -> EnchantmentTags.TRADES_JUNGLE_SPECIAL;
            case SNOW_COMMON -> EnchantmentTags.TRADES_SNOW_COMMON;
            case SNOW_SPECIAL -> EnchantmentTags.TRADES_SNOW_SPECIAL;
            case SWAMP_COMMON -> EnchantmentTags.TRADES_SWAMP_COMMON;
            case SWAMP_SPECIAL -> EnchantmentTags.TRADES_SWAMP_SPECIAL;
            case TAIGA_COMMON -> EnchantmentTags.TRADES_TAIGA_COMMON;
            case TAIGA_SPECIAL -> EnchantmentTags.TRADES_TAIGA_SPECIAL;
        };
    }

    @NotNull
    private static Enchantment.Cost nmsCost(@NotNull Cost cost) {
        return new Enchantment.Cost(cost.base(), cost.perLevel());
    }

    private static EquipmentSlotGroup[] nmsSlots(@NotNull Definition definition) {
        EquipmentSlot[] slots = definition.getSupportedItems().getSlots();
        EquipmentSlotGroup[] nmsSlots = new EquipmentSlotGroup[slots.length];

        for (int index = 0; index < nmsSlots.length; index++) {
            EquipmentSlot bukkitSlot = slots[index];
            nmsSlots[index] = CraftEquipmentSlot.getNMSGroup(bukkitSlot.getGroup());
        }

        return nmsSlots;
    }

    @Override
    public void sendAttackPacket(@NotNull Player player, int id) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer entity = craftPlayer.getHandle();
        ClientboundAnimatePacket packet = new ClientboundAnimatePacket(entity, id);
        craftPlayer.getHandle().connection.send(packet);
    }

    @Override
    public void retrieveHook(@NotNull FishHook hook, @NotNull ItemStack item, @NotNull EquipmentSlot slot) {
        CraftFishHook craftFishHook = (CraftFishHook) hook;
        FishingHook handle = craftFishHook.getHandle();

        net.minecraft.world.entity.player.Player owner = handle.getPlayerOwner();
        if (owner == null) return;

        int result = handle.retrieve(CraftItemStack.asNMSCopy(item));

        net.minecraft.world.entity.EquipmentSlot hand = slot == EquipmentSlot.HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND;

        net.minecraft.world.item.ItemStack itemStack = owner.getItemBySlot(hand);
        if (itemStack == null) return;

        itemStack.hurtAndBreak(result, handle.getPlayerOwner(), hand);
    }

    @NotNull
    @Override
    public Material getItemBlockVariant(@NotNull Material material) {
        ItemStack itemStack = new ItemStack(material);
        net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(itemStack);
        if (nmsStack.getItem() instanceof BlockItem blockItem) {
            return CraftBlockType.minecraftToBukkit(blockItem.getBlock());
        }
        return material;
    }

    @Override
    public boolean handleFlameWalker(@NotNull FlameWalker flameWalker, @NotNull LivingEntity bukkitEntity, int level) {
        Entity entity = ((CraftLivingEntity) bukkitEntity).getHandle();
        BlockPos pos = entity.blockPosition();
        Level world = entity.level();

        int radius = (int) flameWalker.getRadius().getValue(level);
        BlockState magmaState = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos posAbove = new BlockPos.MutableBlockPos();

        Set<Block> blocks = new HashSet<>();
        for (BlockPos posNear : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, -1, radius))) {
            if (!posNear.closerThan(entity.blockPosition(), radius)) continue;

            posAbove.set(posNear.getX(), posNear.getY() + 1, posNear.getZ());

            BlockState aboveLava = world.getBlockState(posAbove);
            BlockState lavaState = world.getBlockState(posNear);

            if (!aboveLava.isAir()) continue;
            if (!lavaState.getBlock().equals(Blocks.LAVA)) continue;
            if (lavaState.getValue(LiquidBlock.LEVEL) != 0) continue;
            if (!magmaState.canSurvive(world, posNear)) continue;
            if (!world.isUnobstructed(magmaState, posNear, CollisionContext.empty())) continue;
            if (!CraftEventFactory.handleBlockFormEvent(world, posNear, magmaState, entity)) continue;

            blocks.add(CraftBlock.at(world, posNear));
        }

        blocks.forEach(block -> flameWalker.addBlock(block, level));

        return !blocks.isEmpty();
    }

    @NotNull
    public org.bukkit.entity.Item popResource(@NotNull Block block, @NotNull ItemStack item) {
        Level world = ((CraftWorld)block.getWorld()).getHandle();
        BlockPos pos = ((CraftBlock)block).getPosition();
        net.minecraft.world.item.ItemStack itemstack = CraftItemStack.asNMSCopy(item);

        float yMod = EntityType.ITEM.getHeight() / 2.0F;
        double x = (pos.getX() + 0.5F) + Mth.nextDouble(world.random, -0.25D, 0.25D);
        double y = (pos.getY() + 0.5F) + Mth.nextDouble(world.random, -0.25D, 0.25D) - yMod;
        double z = (pos.getZ() + 0.5F) + Mth.nextDouble(world.random, -0.25D, 0.25D);

        ItemEntity itemEntity = new ItemEntity(world, x, y, z, itemstack);
        itemEntity.setDefaultPickUpDelay();
        return (org.bukkit.entity.Item) itemEntity.getBukkitEntity();
    }
}