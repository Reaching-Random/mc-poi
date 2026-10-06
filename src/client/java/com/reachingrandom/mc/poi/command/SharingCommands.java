package com.reachingrandom.mc.poi.command;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.reachingrandom.mc.poi.Pointsofinterest;
import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.config.PoiConfig;
import com.reachingrandom.mc.poi.storage.PoiStorageProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.reachingrandom.mc.poi.command.PoiCommand.async;
import static com.reachingrandom.mc.poi.command.PoiCommand.chat;
import static com.reachingrandom.mc.poi.command.PoiCommand.err;
import static com.reachingrandom.mc.poi.command.PoiCommand.gray;
import static com.reachingrandom.mc.poi.command.PoiCommand.header;
import static com.reachingrandom.mc.poi.command.PoiCommand.ok;
import static com.reachingrandom.mc.poi.command.PoiCommand.send;
import static com.reachingrandom.mc.poi.command.PoiCommand.sendComponent;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Shared worlds: {@code /world share|invites|members|leave}, {@code /poi link|unlink},
 * answering invites sent to this account, and the background checks that keep a shared
 * world's POIs current. All of it needs online mode; the site enforces every role.
 */
public final class SharingCommands {

    private static final List<String> ROLES = List.of("read-only", "contribute", "admin");

    private SharingCommands() {}

    // ── Registration ──────────────────────────────────────────────────────────

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> share = literal("share");
        for (String role : ROLES) {
            share.then(literal(role)
                .then(argument("players", StringArgumentType.greedyString())
                    .suggests(SharingCommands::suggestPlayers)
                    .executes(ctx -> executeShare(ctx.getSource(), role, getString(ctx, "players")))));
        }

        dispatcher.register(
            literal("world")
                .then(share)
                .then(literal("invites")
                    .executes(ctx -> executeInvites(ctx.getSource()))
                    .then(literal("revoke")
                        .then(argument("number", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeRevoke(ctx.getSource(), getInteger(ctx, "number"))))))
                .then(literal("members")
                    .executes(ctx -> executeMembers(ctx.getSource())))
                .then(literal("leave")
                    .executes(ctx -> executeLeave(ctx.getSource(), false))
                    .then(literal("confirm")
                        .executes(ctx -> executeLeave(ctx.getSource(), true))))
        );

        dispatcher.register(
            literal("poi")
                .then(literal("link")
                    .executes(ctx -> executeLink(ctx.getSource())))
                .then(literal("unlink")
                    .executes(ctx -> executeUnlink(ctx.getSource())))
                // Click targets of the invite message; not listed in help
                .then(literal("invite")
                    .then(literal("accept")
                        .then(argument("id", StringArgumentType.word())
                            .executes(ctx -> executeRespond(ctx.getSource(), getString(ctx, "id"), true))))
                    .then(literal("decline")
                        .then(argument("id", StringArgumentType.word())
                            .executes(ctx -> executeRespond(ctx.getSource(), getString(ctx, "id"), false)))))
        );
    }

    /** Suggests names from the server's tab list for the last word typed. */
    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<FabricClientCommandSource> ctx,
                                                                 SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String prefix = remaining.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
        SuggestionsBuilder last = builder.createOffset(builder.getStart() + lastSpace + 1);
        for (GameProfile profile : onlineProfiles().values()) {
            if (profile.name().toLowerCase(Locale.ROOT).startsWith(prefix)) last.suggest(profile.name());
        }
        return last.buildFuture();
    }

    /** Players in the server's tab list, by lowercase name. */
    private static Map<String, GameProfile> onlineProfiles() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        Map<String, GameProfile> out = new LinkedHashMap<>();
        if (connection == null) return out;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && profile.name() != null) out.put(profile.name().toLowerCase(Locale.ROOT), profile);
        }
        return out;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** An API client when sharing is possible (online mode with a key), else explains why not. */
    private static ApiClient onlineClient(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        if (!cfg.isOnlineMode() || !cfg.hasApiKey()) {
            send(source, err("Shared worlds need online mode. Run /poi setkey <key> first."));
            return null;
        }
        return new ApiClient();
    }

    private static String selectedWorldName(String worldId) {
        ApiModels.WorldSummary w = PoiSession.get().knownWorld(worldId);
        return w != null ? w.name : "this world";
    }

    private static MutableComponent button(String label, ChatFormatting color, ClickEvent click, String hover) {
        return Component.literal(label).withStyle(s -> s
                .withColor(color)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /**
     * After a world is bound to a multiplayer server, record that server on the world
     * (owners and admins only, once), so members' mods can select it when they join.
     */
    static void rememberServer(String placeKey, ApiModels.WorldSummary world) {
        if (placeKey == null || !placeKey.startsWith("mp:") || world == null) return;
        if (world.serverAddress != null || !world.isAdmin() || world.role == null) return;
        String address = placeKey.substring(3);
        CompletableFuture.runAsync(() -> {
            try {
                new ApiClient().setServerAddress(world.id, address);
                world.serverAddress = address;
            } catch (Exception ignored) {
                // Only a convenience for other members
            }
        });
    }

    // ── /world share ──────────────────────────────────────────────────────────

    private static int executeShare(FabricClientCommandSource source, String role, String playersText) {
        String worldId = PoiCommand.requireCurrentWorld(source);
        if (worldId == null) return 0;
        ApiClient client = onlineClient(source);
        if (client == null) return 0;

        Map<String, GameProfile> online = onlineProfiles();
        List<ApiModels.Player> players = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String raw : playersText.trim().split("\\s+")) {
            String name = raw.startsWith("@") ? raw.substring(1) : raw;
            if (name.isEmpty() || !seen.add(name.toLowerCase(Locale.ROOT))) continue;
            GameProfile profile = online.get(name.toLowerCase(Locale.ROOT));
            if (profile == null) missing.add(name);
            else players.add(new ApiModels.Player(profile.id().toString(), profile.name()));
        }
        if (!missing.isEmpty()) {
            send(source, gray("Not on this server: " + String.join(", ", missing)
                    + ". Invite them from Share on the POI Tracker website instead."));
        }
        if (players.isEmpty()) return missing.isEmpty() ? 0 : 1;

        String worldName = selectedWorldName(worldId);
        async(source, () -> {
            List<ApiModels.InviteResult> results = client.invitePlayers(worldId, role, players);
            send(source, header("Invites to " + worldName + " (" + role + ")"));
            for (ApiModels.InviteResult r : results) {
                String kind = r.kind != null ? r.kind : "";
                switch (kind) {
                    case "addressed" -> send(source, ok("  " + r.name + ": invite sent to their game"));
                    case "member" -> send(source, gray("  " + r.name + " is already a member"));
                    case "link" -> {
                        if (r.url == null) {
                            send(source, gray("  " + r.name + ": invited (see Share on the website for the link)"));
                            break;
                        }
                        MutableComponent line = Component.literal("  " + r.name + ": ")
                                .append(button("[Copy link]", ChatFormatting.AQUA,
                                        new ClickEvent.CopyToClipboard(r.url), "Copy the invite link"))
                                .append(Component.literal(" "))
                                .append(button("[Open]", ChatFormatting.AQUA,
                                        new ClickEvent.OpenUrl(URI.create(r.url)), r.url));
                        sendComponent(source, line);
                    }
                    // A result type from a newer site: say what it is rather than fail
                    default -> send(source, gray("  " + r.name + ": " + (kind.isEmpty() ? "done" : kind)));
                }
            }
            if (results.stream().anyMatch(r -> "link".equals(r.kind))) {
                send(source, gray("  Send each link to that player. It works once and expires in 7 days."));
                send(source, gray("  Players who run /poi link get invites in game instead."));
            }
        });
        return 1;
    }

    // ── /world invites ────────────────────────────────────────────────────────

    /** Pending invites from the last /world invites, for /world invites revoke <#>. */
    private static volatile List<ApiModels.Invite> lastInvites = Collections.emptyList();

    private static int executeInvites(FabricClientCommandSource source) {
        String worldId = PoiCommand.requireCurrentWorld(source);
        if (worldId == null) return 0;
        ApiClient client = onlineClient(source);
        if (client == null) return 0;

        String worldName = selectedWorldName(worldId);
        async(source, () -> {
            List<ApiModels.Invite> pending = client.getInvites(worldId).stream()
                    .filter(i -> "pending".equals(i.status))
                    .toList();
            lastInvites = pending;
            send(source, header("Pending invites — " + worldName));
            if (pending.isEmpty()) {
                send(source, gray("  None. Use /world share <role> <player...> to invite someone."));
                return;
            }
            for (int i = 0; i < pending.size(); i++) {
                ApiModels.Invite inv = pending.get(i);
                String label = inv.label != null ? inv.label : "Unlabelled link";
                String how = inv.addressed ? "in game" : "link";
                String until = inv.expiresAt != null && inv.expiresAt.length() >= 10 ? ", until " + inv.expiresAt.substring(0, 10) : "";
                MutableComponent line = Component.literal("  §e" + (i + 1) + ".§r " + label + " §7(" + inv.role + ", " + how + until + ")§r ")
                        .append(button("[Revoke]", ChatFormatting.RED,
                                new ClickEvent.RunCommand("/world invites revoke " + (i + 1)), "Revoke this invite"));
                sendComponent(source, line);
            }
        });
        return 1;
    }

    private static int executeRevoke(FabricClientCommandSource source, int number) {
        String worldId = PoiCommand.requireCurrentWorld(source);
        if (worldId == null) return 0;
        ApiClient client = onlineClient(source);
        if (client == null) return 0;
        List<ApiModels.Invite> invites = lastInvites;
        if (number > invites.size()) {
            send(source, err(invites.isEmpty() ? "Run /world invites first." : "Invalid number. Choose 1–" + invites.size() + "."));
            return 0;
        }
        ApiModels.Invite invite = invites.get(number - 1);
        async(source, () -> {
            client.revokeInvite(worldId, invite.id);
            send(source, ok("Revoked the invite for " + (invite.label != null ? invite.label : "that link") + "."));
        });
        return 1;
    }

    // ── /world members, /world leave ─────────────────────────────────────────

    private static int executeMembers(FabricClientCommandSource source) {
        String worldId = PoiCommand.requireCurrentWorld(source);
        if (worldId == null) return 0;
        ApiClient client = onlineClient(source);
        if (client == null) return 0;

        String worldName = selectedWorldName(worldId);
        async(source, () -> {
            List<ApiModels.Member> members = client.getMembers(worldId);
            send(source, header("Members — " + worldName));
            for (ApiModels.Member m : members) {
                String name = m.name != null ? m.name : "Unlinked member";
                send(source, "  " + name + (m.isYou ? " §7(you)§r" : "") + " §7— " + m.role + "§r");
            }
        });
        return 1;
    }

    private static int executeLeave(FabricClientCommandSource source, boolean confirmed) {
        String worldId = PoiCommand.requireCurrentWorld(source);
        if (worldId == null) return 0;
        ApiClient client = onlineClient(source);
        if (client == null) return 0;

        String worldName = selectedWorldName(worldId);
        if (!confirmed) {
            MutableComponent ask = Component.literal(gray("Leave " + worldName + "? You'll need a new invite to come back. "))
                    .append(button("[Leave]", ChatFormatting.RED,
                            new ClickEvent.RunCommand("/world leave confirm"), "Leave " + worldName));
            sendComponent(source, ask);
            return 1;
        }
        String place = PoiCommand.getPlaceKey();
        async(source, () -> {
            client.leaveWorld(worldId);
            Minecraft.getInstance().execute(() -> {
                PoiConfig.get().bindWorld(place, null);
                PoiCommand.switchWorld(null);
            });
            send(source, ok("You left " + worldName + "."));
        });
        return 1;
    }

    // ── /poi link, /poi unlink ────────────────────────────────────────────────

    /**
     * Proves this Minecraft account is yours, the way servers check players: the site
     * hands out a one-time nonce, we "join" Mojang's session server with it, and the site
     * asks Mojang who joined with that nonce.
     */
    private static int executeLink(FabricClientCommandSource source) {
        ApiClient client = onlineClient(source);
        if (client == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();

        send(source, gray("Linking " + user.getName() + " with Mojang's help..."));
        async(source, () -> {
            ApiModels.LinkedAccount account;
            try {
                account = linkNow(client);
            } catch (AuthenticationException e) {
                send(source, err("Mojang couldn't confirm your session: " + e.getMessage()));
                send(source, gray("  Restart the game from the Minecraft Launcher and try again."));
                return;
            }
            linkedProfile = user.getProfileId();
            send(source, ok("Linked " + account.name + " to your Reaching Random account."));
            send(source, gray("  Shared-world members now see you as " + account.name
                    + (account.primary ? "" : " (if it's your primary account; change that in Settings on the site)")
                    + ", and invites to this account arrive in game."));
        });
        return 1;
    }

    /** The account linked (or found linked) this session, so join doesn't check again. */
    private static volatile java.util.UUID linkedProfile;

    /**
     * The handshake itself: a one-time nonce from the site, a "join" on Mojang's session
     * server with it, then the site checks with Mojang. Throws if Mojang refuses the session
     * (it's too old: the game needs restarting from the launcher) or the site refuses the link.
     */
    private static ApiModels.LinkedAccount linkNow(ApiClient client) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        String nonce = client.createLinkNonce();
        mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), nonce);
        return client.link(user.getName(), nonce);
    }

    /**
     * Links the signed-in account if it isn't linked yet, without a word on failure: after
     * {@code /poi setkey}, and on every join until it works. A stale session (the launcher has
     * been open for days) just leaves it for the next join after a restart. {@code /poi link}
     * is the version that reports errors.
     */
    public static void autoLink() {
        PoiConfig cfg = PoiConfig.get();
        if (!cfg.isOnlineMode() || !cfg.hasApiKey()) return;
        java.util.UUID me = Minecraft.getInstance().getUser().getProfileId();
        if (me == null || me.equals(linkedProfile)) return;
        CompletableFuture.runAsync(() -> {
            try {
                ApiClient client = new ApiClient();
                boolean linked = client.getLinkedAccounts().stream().anyMatch(a -> me.toString().equals(a.uuid));
                if (!linked) {
                    ApiModels.LinkedAccount account = linkNow(client);
                    chat("§7[POI] Linked " + account.name + " to your Reaching Random account.§r");
                }
                linkedProfile = me;
            } catch (Exception e) {
                Pointsofinterest.LOGGER.debug("[POI] Automatic account link didn't happen: {}", e.getMessage());
            }
        });
    }

    private static int executeUnlink(FabricClientCommandSource source) {
        ApiClient client = onlineClient(source);
        if (client == null) return 0;
        User user = Minecraft.getInstance().getUser();
        async(source, () -> {
            client.unlink(user.getProfileId().toString());
            linkedProfile = null;
            send(source, ok("Unlinked " + user.getName() + " from your Reaching Random account."));
        });
        return 1;
    }

    // ── Invites sent to this account ─────────────────────────────────────────

    /** Invite ids already shown this session, so each one is announced once. */
    private static final Set<String> announced = ConcurrentHashMap.newKeySet();

    private static void announce(ApiModels.PendingInvite inv) {
        String who = inv.invitedBy != null ? inv.invitedBy : "Someone";
        String world = inv.worldName != null ? inv.worldName : "a world";
        String role = inv.role != null ? " as " + inv.role : "";
        MutableComponent msg = Component.literal("§b[POI]§r " + who + " invited you to §f" + world + "§r" + role + ". ")
                .append(button("[Accept]", ChatFormatting.GREEN,
                        new ClickEvent.RunCommand("/poi invite accept " + inv.id), "Join " + world))
                .append(Component.literal(" "))
                .append(button("[Decline]", ChatFormatting.GRAY,
                        new ClickEvent.RunCommand("/poi invite decline " + inv.id), "Decline the invite"));
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(msg, false);
        });
    }

    private static int executeRespond(FabricClientCommandSource source, String inviteId, boolean accept) {
        ApiClient client = onlineClient(source);
        if (client == null) return 0;
        String place = PoiCommand.getPlaceKey();
        async(source, () -> {
            String worldId = client.respondToInvite(inviteId, accept);
            announced.remove(inviteId);
            if (!accept) {
                send(source, gray("Invite declined."));
                return;
            }
            List<ApiModels.WorldSummary> worlds = PoiStorageProvider.get().listWorlds();
            PoiSession.get().setKnownWorlds(worlds);
            ApiModels.WorldSummary world = PoiSession.get().knownWorld(worldId);
            String name = world != null ? world.name : "the world";
            PoiConfig cfg = PoiConfig.get();
            boolean playsHere = world != null && world.serverAddress != null && place != null
                    && place.equals("mp:" + PoiCommand.normalizeAddress(world.serverAddress));
            if (place != null && (playsHere || cfg.getBinding(place) == null)) {
                Minecraft.getInstance().execute(() -> {
                    cfg.bindWorld(place, worldId);
                    PoiCommand.switchWorld(worldId);
                });
                send(source, ok("Joined " + name + ". It's now selected here."));
            } else {
                send(source, ok("Joined " + name + ". Run /world list to select it."));
            }
        });
        return 1;
    }

    // ── Background checks ─────────────────────────────────────────────────────
    //
    // One request, GET /poll, paced by the site: it answers with the selected world's
    // revision, how many invites are waiting, and how long to wait before asking again.
    // The site can slow everyone down without a release; busy answers (429/503) back off.

    /** Used until the site says otherwise, and the bounds any hint is held to. */
    private static final int DEFAULT_POLL_SECONDS = 30;
    private static final int MIN_POLL_SECONDS = 15;
    private static final int MAX_POLL_SECONDS = 15 * 60;
    private static final int FIRST_POLL_AFTER_JOIN_SECONDS = 5;
    /** A world is only given up on after this many "not found" answers in a row. */
    private static final int NOT_FOUND_BEFORE_DESELECT = 2;

    private static final java.util.Random JITTER = new java.util.Random();
    private static final AtomicBoolean pollInFlight = new AtomicBoolean();
    private static volatile long nextPollAtMillis = Long.MAX_VALUE;
    private static volatile int pollSeconds = DEFAULT_POLL_SECONDS;
    private static volatile int backoffSeconds;
    private static volatile int notFoundInARow;
    private static volatile String revWorldId;
    private static volatile long lastRev = -1;
    private static volatile int lastPendingInvites;

    public static void registerWatcher() {
        ClientTickEvents.END_CLIENT_TICK.register(SharingCommands::onTick);
    }

    /** Called on joining a world or server: poll shortly after, and link the account if needed. */
    public static void onJoin() {
        revWorldId = null;
        lastRev = -1;
        lastPendingInvites = 0;
        notFoundInARow = 0;
        backoffSeconds = 0;
        scheduleIn(FIRST_POLL_AFTER_JOIN_SECONDS);
        autoLink();
    }

    public static void onDisconnect() {
        nextPollAtMillis = Long.MAX_VALUE;
        revWorldId = null;
        lastRev = -1;
    }

    private static boolean online() {
        PoiConfig cfg = PoiConfig.get();
        return cfg.isOnlineMode() && cfg.hasApiKey();
    }

    /** Waits about {@code seconds}, give or take 20%, so clients don't all ask at once. */
    private static void scheduleIn(int seconds) {
        double jitter = 0.8 + 0.4 * JITTER.nextDouble();
        nextPollAtMillis = System.currentTimeMillis() + (long) (seconds * 1000L * jitter);
    }

    private static int clampSeconds(Integer seconds) {
        if (seconds == null) return DEFAULT_POLL_SECONDS;
        return Math.max(MIN_POLL_SECONDS, Math.min(MAX_POLL_SECONDS, seconds));
    }

    private static void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !online()) return;
        if (System.currentTimeMillis() >= nextPollAtMillis && pollInFlight.compareAndSet(false, true)) poll();
    }

    private static void poll() {
        String worldId = PoiConfig.get().currentWorldId;
        CompletableFuture.runAsync(() -> {
            int next = pollSeconds;
            try {
                ApiModels.PollResponse resp = new ApiClient().poll(worldId);
                backoffSeconds = 0;
                notFoundInARow = 0;
                pollSeconds = next = clampSeconds(resp.pollSeconds);
                onWorldRev(worldId, resp.rev);
                onPendingInvites(resp.pendingInvites != null ? resp.pendingInvites : 0);
            } catch (ApiClient.ApiException e) {
                if (e.isBusy() || e.status == 0) {
                    // Busy or unreachable: wait as told, or twice as long each time
                    backoffSeconds = e.retryAfterSeconds > 0 ? e.retryAfterSeconds
                            : Math.max(pollSeconds, backoffSeconds) * 2;
                    next = clampSeconds(backoffSeconds);
                } else if (e.isNotFound() && worldId != null) {
                    onWorldNotFound(worldId);
                } else if (e.status == 426) {
                    next = MAX_POLL_SECONDS; // outdated build; commands show the update message
                }
            } catch (Exception ignored) {
                // Try again next time
            } finally {
                scheduleIn(next);
                pollInFlight.set(false);
            }
        });
    }

    /** Reloads the selected world's POIs when someone else changed it. */
    private static void onWorldRev(String worldId, Long rev) {
        if (worldId == null || rev == null || !worldId.equals(PoiConfig.get().currentWorldId)) return;
        if (worldId.equals(revWorldId) && rev != lastRev) PoiCommand.refreshPoiListAsync(worldId);
        revWorldId = worldId;
        lastRev = rev;
    }

    /**
     * Not found twice in a row: the player was removed from the world, or it was deleted.
     * Deselect it but keep this server's binding, so a world that comes back (or a blip
     * on the site) doesn't cost them a /world select.
     */
    private static void onWorldNotFound(String worldId) {
        if (++notFoundInARow < NOT_FOUND_BEFORE_DESELECT || !worldId.equals(PoiConfig.get().currentWorldId)) return;
        notFoundInARow = 0;
        String name = selectedWorldName(worldId);
        Minecraft.getInstance().execute(() -> PoiCommand.switchWorld(null));
        chat("§e[POI] You no longer have access to " + name + ". Run /world list to pick another world.§r");
    }

    /** Fetches and announces invites when the count goes up. */
    private static void onPendingInvites(int count) {
        int before = lastPendingInvites;
        lastPendingInvites = count;
        if (count == 0 || count <= before) return;
        try {
            for (ApiModels.PendingInvite inv : new ApiClient().getPendingInvites()) {
                if (inv.id != null && announced.add(inv.id)) announce(inv);
            }
        } catch (Exception ignored) {
            lastPendingInvites = before; // try again on the next poll
        }
    }
}
