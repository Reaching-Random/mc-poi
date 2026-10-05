package com.reachingrandom.mc.poi.campsite;

import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.command.PoiSession;
import com.reachingrandom.mc.poi.config.PoiConfig;
import com.reachingrandom.mc.poi.storage.PoiStorage;
import com.reachingrandom.mc.poi.storage.PoiStorageProvider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns named campfires into POIs in the {@value CampsiteIndex#GROUP_NAME} group.
 *
 * <ul>
 *   <li><b>Place</b> a campfire → once the server confirms it, a name prompt opens.</li>
 *   <li><b>Right-click</b> a campfire with an empty main hand → rename prompt.</li>
 *   <li><b>Break</b> a campfire → its POI is deleted.</li>
 * </ul>
 *
 * <p>Everything runs on the client, so it works on vanilla servers. Break detection
 * polls the indexed positions in loaded chunks once a second. A POI is only deleted
 * if its campfire was seen at that spot earlier in the same session. That guards
 * against deleting another world's campsites when the selected POI world doesn't
 * match the world being played.
 */
public final class CampsiteTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("points-of-interest");

    /** Ticks a placed campfire must persist before prompting (lets the server reject it first). */
    private static final int CONFIRM_TICKS = 10;
    /** Ticks to wait for a placed campfire to appear before giving up. */
    private static final int PENDING_TIMEOUT_TICKS = 60;
    private static final int CHECK_INTERVAL_TICKS = 20;

    private record Pending(String dimension, BlockPos pos, int ticksLeft, int ticksPresent) {}

    // Render-thread state
    private static Pending pending;
    private static final Set<CampsiteIndex.Key> seen = new HashSet<>();
    private static String seenForWorldId;
    private static int tickCounter;

    /** IDs of campsite POIs with a delete in flight, so the poller doesn't repeat it. */
    private static final Set<String> deleting = ConcurrentHashMap.newKeySet();

    private CampsiteTracker() {}

    public static void register() {
        UseBlockCallback.EVENT.register(CampsiteTracker::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(CampsiteTracker::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetSession());
    }

    private static void resetSession() {
        pending = null;
        seen.clear();
        seenForWorldId = null;
    }

    private static boolean enabled() {
        PoiConfig cfg = PoiConfig.get();
        return cfg.campfireCampsites && !cfg.isOffMode();
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() || player.isSpectator() || !enabled()) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        BlockPos clicked = hit.getBlockPos();

        // Empty-hand right-click on a campfire: rename
        if (hand == InteractionHand.MAIN_HAND && stack.isEmpty()
                && isCampfire(level, clicked)
                && !offhandUsesCampfire(player.getOffhandItem())) {
            String worldId = PoiConfig.get().currentWorldId;
            if (worldId == null) {
                overlay("Select a POI world (/world list) to name campfires.");
                return InteractionResult.PASS;
            }
            if (!CampsiteIndex.isBuiltFor(worldId)) {
                PoiCommand.refreshPoiListAsync(worldId);
                overlay("Loading POIs… try again in a moment.");
                return InteractionResult.PASS;
            }
            String dimension = dimensionOf(level);
            BlockPos pos = clicked.immutable();
            markSeen(worldId, dimension, pos);
            ApiModels.WorldItem existing = CampsiteIndex.find(dimension, pos);
            Minecraft.getInstance().setScreen(
                    new CampsiteNameScreen(dimension, pos, existing != null ? existing.name : null));
            return InteractionResult.SUCCESS;
        }

        // Placing a campfire: remember where it should appear and wait for it in onTick
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CampfireBlock) {
            BlockPlaceContext ctx = new BlockPlaceContext(player, hand, stack, hit);
            pending = new Pending(dimensionOf(level), ctx.getClickedPos().immutable(), PENDING_TIMEOUT_TICKS, 0);
        }
        return InteractionResult.PASS;
    }

    /**
     * Whether the off-hand item does something to a campfire when the main hand is empty
     * (cooking food, dousing with a shovel, lighting). Those clicks are left to vanilla.
     */
    private static boolean offhandUsesCampfire(ItemStack offhand) {
        if (offhand.isEmpty()) return false;
        return offhand.has(DataComponents.FOOD)
                || offhand.is(ItemTags.SHOVELS)
                || offhand.is(Items.FLINT_AND_STEEL)
                || offhand.is(Items.FIRE_CHARGE);
    }

    // ── Tick: placement confirmation + break detection ────────────────────────

    private static void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        String dimension = dimensionOf(mc.level);

        if (pending != null) tickPending(mc, dimension);

        if (++tickCounter % CHECK_INTERVAL_TICKS == 0 && enabled()) {
            checkCampsites(mc.level, dimension);
        }
    }

    private static void tickPending(Minecraft mc, String dimension) {
        Pending p = pending;
        if (!p.dimension().equals(dimension) || p.ticksLeft() <= 0) {
            pending = null;
            return;
        }
        int present = isCampfire(mc.level, p.pos()) ? p.ticksPresent() + 1 : 0;
        if (present < CONFIRM_TICKS) {
            pending = new Pending(p.dimension(), p.pos(), p.ticksLeft() - 1, present);
            return;
        }

        pending = null;
        if (!enabled()) return;
        String worldId = PoiConfig.get().currentWorldId;
        if (worldId == null) {
            chat("§7[POI] Campfire not saved as a campsite: no POI world selected (/world list).");
            return;
        }
        markSeen(worldId, dimension, p.pos());
        if (mc.screen != null) return; // don't yank the player out of another screen
        ApiModels.WorldItem existing = CampsiteIndex.find(dimension, p.pos());
        mc.setScreen(new CampsiteNameScreen(dimension, p.pos(), existing != null ? existing.name : null));
    }

    private static void checkCampsites(Level level, String dimension) {
        String worldId = PoiConfig.get().currentWorldId;
        if (!CampsiteIndex.isBuiltFor(worldId)) return;
        if (!worldId.equals(seenForWorldId)) {
            seen.clear();
            seenForWorldId = worldId;
        }

        for (Map.Entry<CampsiteIndex.Key, ApiModels.WorldItem> entry : CampsiteIndex.all().entrySet()) {
            CampsiteIndex.Key key = entry.getKey();
            if (!key.dimension().equals(dimension) || !level.isLoaded(key.pos())) continue;

            if (isCampfire(level, key.pos())) {
                seen.add(key);
            } else if (seen.remove(key)) {
                ApiModels.WorldItem poi = entry.getValue();
                if (poi.id != null && deleting.add(poi.id)) {
                    removeBrokenCampsite(worldId, key, poi);
                }
            }
        }
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    /**
     * Applies a name from {@link CampsiteNameScreen}. Blank + no campsite: nothing.
     * Blank + campsite: delete it. Otherwise create or rename.
     */
    static void saveName(String dimension, BlockPos pos, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        String worldId = PoiConfig.get().currentWorldId;
        Minecraft mc = Minecraft.getInstance();
        if (worldId == null || mc.level == null) return;

        ApiModels.WorldItem indexed = CampsiteIndex.find(dimension, pos);
        if (indexed == null && name.isEmpty()) return;
        if (indexed != null && name.equals(indexed.name)) return;
        if (!dimension.equals(dimensionOf(mc.level)) || !isCampfire(mc.level, pos)) {
            chat("§c[POI] That campfire is gone; campsite not saved.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                PoiStorage storage = PoiStorageProvider.get();
                // Re-read storage rather than trusting the index, so we never duplicate a campsite
                var items = storage.listItems(worldId);
                ApiModels.WorldItem group = CampsiteIndex.findGroup(items);
                ApiModels.WorldItem existing = CampsiteIndex.findIn(group, dimension, pos);

                if (existing == null) {
                    if (name.isEmpty()) return;
                    storage.createCampsite(worldId, name, dimension, pos.getX(), pos.getY(), pos.getZ());
                    chat("§a[POI] Campsite saved: " + name + "§r");
                } else if (name.isEmpty()) {
                    storage.deleteCampsite(worldId, existing.id);
                    forgetCampsite(storage, worldId, existing, "Removed campsite: ");
                    return; // forgetCampsite refreshes
                } else {
                    storage.renameCampsite(worldId, existing.id, name);
                    String poiId = existing.id;
                    Minecraft.getInstance().execute(() -> PoiSession.get().getTrackedPois().stream()
                            .filter(p -> poiId.equals(p.id))
                            .forEach(p -> p.name = name));
                    chat("§a[POI] Campsite renamed: " + existing.name + " → " + name + "§r");
                }
                PoiCommand.refreshPoiList(storage, worldId);
            } catch (ApiClient.ApiException e) {
                // On a shared world the site explains refusals, e.g. renaming someone else's campsite
                if (e.status == 409) PoiCommand.refreshPoiListAsync(worldId); // someone named it first
                chat("§c[POI] " + e.getMessage() + "§r");
            } catch (Exception e) {
                LOGGER.error("[POI] Failed to save campsite", e);
                chat("§c[POI] Couldn't save campsite: " + e.getMessage() + "§r");
            }
        });
    }

    /**
     * The campfire was seen here this session and is gone now. Online this succeeds even
     * if another player's mod already removed it, so everyone who saw it break agrees.
     */
    private static void removeBrokenCampsite(String worldId, CampsiteIndex.Key key, ApiModels.WorldItem poi) {
        CompletableFuture.runAsync(() -> {
            try {
                PoiStorage storage = PoiStorageProvider.get();
                BlockPos pos = key.pos();
                storage.removeCampsiteAt(worldId, key.dimension(), pos.getX(), pos.getY(), pos.getZ());
                forgetCampsite(storage, worldId, poi, "Campfire gone — removed campsite: ");
            } catch (Exception e) {
                if (e instanceof ApiClient.ApiException api && api.isNotFound()) {
                    // Already deleted elsewhere (e.g. on the website): just resync the index
                    PoiCommand.refreshPoiListAsync(worldId);
                } else {
                    LOGGER.error("[POI] Failed to remove campsite", e);
                    chat("§c[POI] Couldn't remove campsite " + poi.name + ": " + e.getMessage() + "§r");
                }
            } finally {
                deleting.remove(poi.id);
            }
        });
    }

    /** Untracks a removed campsite and resyncs the index. Runs on a background thread. */
    private static void forgetCampsite(PoiStorage storage, String worldId,
                                       ApiModels.WorldItem poi, String message) {
        Minecraft.getInstance().execute(() -> {
            PoiSession.get().removeTrackedPoiById(poi.id);
            PoiConfig cfg = PoiConfig.get();
            if (cfg.getTrackedPoiIds().remove(poi.id)) cfg.save();
        });
        PoiCommand.refreshPoiList(storage, worldId);
        chat("§e[POI] " + message + poi.name + "§r");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void markSeen(String worldId, String dimension, BlockPos pos) {
        if (!worldId.equals(seenForWorldId)) {
            seen.clear();
            seenForWorldId = worldId;
        }
        seen.add(new CampsiteIndex.Key(dimension, pos));
    }

    private static boolean isCampfire(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CampfireBlock;
    }

    private static String dimensionOf(Level level) {
        var dim = level.dimension();
        if (dim.equals(Level.NETHER)) return "nether";
        if (dim.equals(Level.END))    return "end";
        return "overworld";
    }

    private static void chat(String legacyText) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(legacyText));
        });
    }

    private static void overlay(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("§7" + text));
    }
}
