package com.reachingrandom.mc.poi.gui;

import com.mojang.authlib.GameProfile;
import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.command.PoiSession;
import com.reachingrandom.mc.poi.command.SharingCommands;
import com.reachingrandom.mc.poi.config.PoiConfig;
import com.reachingrandom.mc.poi.storage.PoiStorageProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The World tab: your POI worlds on the left, the highlighted world on the right
 * (use it here, members, inviting players on this server, pending invites, leave),
 * and invites waiting for you along the bottom. Highlighting a world only shows it;
 * switching takes "Use this world", since it changes what's tracked and bound here.
 */
class WorldTab implements Tab {

    private static final int LIST_WIDTH = 130;
    private static final int GAP = 6;
    private static final int PADDING_TOP = 6;
    private static final int STRIP_HEIGHT = 24;
    private static final List<String> INVITE_ROLES = List.of("read-only", "contribute", "admin");

    private final PoiScreen screen;

    // ── Data (survives screen init) ──
    private List<ApiModels.WorldSummary> worlds = List.of();
    /** Shown instead of the world list while loading or when worlds can't be listed. */
    private Component worldsStatus;
    private boolean worldsLoaded;
    private String highlightedId;
    private int worldsGeneration;

    private List<ApiModels.Member> members;          // null while loading
    private List<ApiModels.Invite> pendingInvites;   // null while loading
    private String detailsError;
    private int detailsGeneration;

    /** Invites waiting for this account; null until loaded. */
    private List<ApiModels.PendingInvite> invitesForMe;

    private final Set<String> picked = new HashSet<>();
    private String inviteRole = "contribute";
    private List<ApiModels.InviteResult> inviteResults = List.of();
    private String inviteResultsFor;
    /** One-line result of the last action, shown above the details. */
    private Component notice;

    private String seenWorldId;
    private String seenMode;

    // ── Widgets (rebuilt on every screen init) ──
    private Font font;
    private WorldList worldList;
    private Button newWorldButton;
    private DetailList detailList;
    private StringWidget inviteText;
    private Button acceptButton;
    private Button declineButton;
    private Button sendButton;

    WorldTab(PoiScreen screen) {
        this.screen = screen;
        PoiConfig cfg = PoiConfig.get();
        seenWorldId = cfg.currentWorldId;
        seenMode = cfg.storageMode;
        loadWorlds();
        loadInvitesForMe();
    }

    @Override
    public Component getTabTitle() {
        // The tab bar reads this when it's built; PoiScreen rebuilds when it changes
        return Component.literal(hasInvitesForMe() ? "World ✉" : "World");
    }

    @Override
    public Component getTabExtraNarration() {
        return hasInvitesForMe() ? Component.literal("You have invites waiting") : Component.empty();
    }

    boolean hasInvitesForMe() {
        return invitesForMe != null ? !invitesForMe.isEmpty() : online() && SharingCommands.hasPendingInvites();
    }

    private static boolean online() {
        PoiConfig cfg = PoiConfig.get();
        return cfg.isOnlineMode() && cfg.hasApiKey();
    }

    private ApiModels.WorldSummary highlighted() {
        for (ApiModels.WorldSummary w : worlds) {
            if (w.id.equals(highlightedId)) return w;
        }
        return null;
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    /** Follows changes made elsewhere: Settings switching mode, or a world selected by command. */
    void tick() {
        PoiConfig cfg = PoiConfig.get();
        if (!Objects.equals(seenMode, cfg.storageMode)) {
            seenMode = cfg.storageMode;
            seenWorldId = cfg.currentWorldId;
            highlightedId = null;
            loadWorlds();
            loadInvitesForMe();
        } else if (!Objects.equals(seenWorldId, cfg.currentWorldId)) {
            seenWorldId = cfg.currentWorldId;
            rebuildWorldList();
            rebuildDetails();
        }
        if (sendButton != null) sendButton.active = !picked.isEmpty();
    }

    private void loadWorlds() {
        Minecraft mc = Minecraft.getInstance();
        PoiConfig cfg = PoiConfig.get();
        int generation = ++worldsGeneration;
        worldsLoaded = false;
        if (cfg.isOffMode()) {
            worlds = List.of();
            worldsStatus = Component.literal("Points of Interest is turned off. Turn it on in Settings.");
            refreshWidgets();
            return;
        }
        if (cfg.isOnlineMode() && !cfg.hasApiKey()) {
            worlds = List.of();
            worldsStatus = Component.literal("No API key set. Add one in Settings, or switch to offline.");
            refreshWidgets();
            return;
        }
        worldsStatus = Component.literal("Loading…");
        CompletableFuture.runAsync(() -> {
            try {
                List<ApiModels.WorldSummary> loaded = PoiStorageProvider.get().listWorlds();
                PoiSession.get().setKnownWorlds(loaded);
                mc.execute(() -> {
                    if (generation != worldsGeneration) return;
                    worlds = loaded;
                    worldsLoaded = true;
                    worldsStatus = loaded.isEmpty() ? Component.literal("No worlds yet.") : null;
                    if (highlighted() == null) {
                        String current = PoiConfig.get().currentWorldId;
                        highlightedId = loaded.stream().anyMatch(w -> w.id.equals(current)) ? current
                                : loaded.isEmpty() ? null : loaded.getFirst().id;
                    }
                    refreshWidgets();
                    loadDetails();
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    if (generation != worldsGeneration) return;
                    worldsStatus = Component.literal("Could not load worlds: " + e.getMessage());
                    refreshWidgets();
                });
            }
        });
    }

    /** Members, and for admins the pending invites, of the highlighted world. */
    private void loadDetails() {
        ApiModels.WorldSummary world = highlighted();
        int generation = ++detailsGeneration;
        members = null;
        pendingInvites = null;
        detailsError = null;
        if (world == null || !online() || world.role == null) {
            members = List.of();
            pendingInvites = List.of();
            rebuildDetails();
            return;
        }
        rebuildDetails();
        boolean admin = world.isAdmin();
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                ApiClient client = new ApiClient();
                List<ApiModels.Member> loadedMembers = client.getMembers(world.id);
                List<ApiModels.Invite> loadedInvites = admin
                        ? client.getInvites(world.id).stream().filter(i -> "pending".equals(i.status)).toList()
                        : List.of();
                mc.execute(() -> {
                    if (generation != detailsGeneration) return;
                    members = loadedMembers;
                    pendingInvites = loadedInvites;
                    rebuildDetails();
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    if (generation != detailsGeneration) return;
                    members = List.of();
                    pendingInvites = List.of();
                    detailsError = "Could not load members: " + e.getMessage();
                    rebuildDetails();
                });
            }
        });
    }

    private void loadInvitesForMe() {
        if (!online()) {
            setInvitesForMe(List.of());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                List<ApiModels.PendingInvite> loaded = new ApiClient().getPendingInvites();
                mc.execute(() -> setInvitesForMe(loaded));
            } catch (Exception ignored) {
                // The strip just stays hidden; chat still announces invites
            }
        });
    }

    private void setInvitesForMe(List<ApiModels.PendingInvite> invites) {
        boolean before = hasInvitesForMe();
        invitesForMe = invites;
        // The tab title and the strip's space both change: lay everything out again
        if (before != hasInvitesForMe() && worldList != null) {
            screen.rebuild();
        } else {
            updateStrip();
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Runs a blocking call off the game thread, then {@code after} back on it, with failures as the notice. */
    private void run(String failure, ThrowingRunnable call, Runnable after) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                call.run();
                mc.execute(after);
            } catch (Exception e) {
                mc.execute(() -> {
                    notice = Component.literal(failure + ": " + e.getMessage()).withStyle(ChatFormatting.RED);
                    rebuildDetails();
                });
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void useWorld(ApiModels.WorldSummary world) {
        PoiCommand.selectWorld(world);
        seenWorldId = world.id;
        notice = Component.literal("Now using " + world.name + " here.").withStyle(ChatFormatting.GREEN);
        rebuildWorldList();
        rebuildDetails();
    }

    private void newWorld() {
        String seed = PoiCommand.getCurrentSeed();
        String place = PoiCommand.getPlaceKey();
        String note = seed == null || seed.isBlank() ? "No seed (servers don't share theirs)" : "Seed: " + seed;
        Minecraft.getInstance().setScreen(new NamePromptScreen(screen, "New POI world", PoiCommand.getWorldName(), note, "Create", name -> {
            ApiModels.WorldSummary[] created = new ApiModels.WorldSummary[1];
            run("Couldn't create the world",
                    () -> created[0] = PoiCommand.createAndSelectWorld(name, seed != null ? seed : "", place),
                    () -> {
                        highlightedId = created[0].id;
                        seenWorldId = created[0].id;
                        notice = Component.literal("Created " + created[0].name + " and selected it here.")
                                .withStyle(ChatFormatting.GREEN);
                        loadWorlds();
                    });
        }));
    }

    private void confirmLeave(ApiModels.WorldSummary world) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(yes -> {
            mc.setScreen(screen);
            if (!yes) return;
            run("Couldn't leave", () -> SharingCommands.leave(new ApiClient(), world.id), () -> {
                highlightedId = null;
                notice = Component.literal("You left " + world.name + ".");
                loadWorlds();
            });
        }, Component.literal("Leave " + world.name + "?"),
                Component.literal("You'll need a new invite to come back.")));
    }

    private void sendInvites(ApiModels.WorldSummary world) {
        List<ApiModels.Player> players = new ArrayList<>();
        for (GameProfile p : SharingCommands.otherOnlinePlayers()) {
            if (picked.contains(p.name().toLowerCase(Locale.ROOT))) players.add(new ApiModels.Player(p.id().toString(), p.name()));
        }
        if (players.isEmpty()) return;
        String role = inviteRole;
        List<ApiModels.InviteResult> results = new ArrayList<>();
        run("Couldn't send invites", () -> results.addAll(new ApiClient().invitePlayers(world.id, role, players)), () -> {
            picked.clear();
            inviteResults = results;
            inviteResultsFor = world.id;
            notice = null;
            loadDetails();
        });
    }

    private void revoke(ApiModels.WorldSummary world, ApiModels.Invite invite) {
        run("Couldn't revoke", () -> new ApiClient().revokeInvite(world.id, invite.id), () -> {
            notice = Component.literal("Revoked the invite for " + inviteLabel(invite) + ".");
            loadDetails();
        });
    }

    private void respond(ApiModels.PendingInvite invite, boolean accept) {
        String place = PoiCommand.getPlaceKey();
        SharingCommands.Joined[] joined = new SharingCommands.Joined[1];
        run(accept ? "Couldn't accept" : "Couldn't decline",
                () -> joined[0] = SharingCommands.respond(new ApiClient(), invite.id, accept, place),
                () -> {
                    if (joined[0] == null) {
                        notice = Component.literal("Invite declined.");
                    } else {
                        highlightedId = joined[0].worldId();
                        notice = Component.literal("Joined " + joined[0].name()
                                + (joined[0].selectedHere() ? ". It's now selected here." : "."))
                                .withStyle(ChatFormatting.GREEN);
                    }
                    loadWorlds();
                    loadInvitesForMe();
                });
    }

    // ── Widgets and layout ────────────────────────────────────────────────────

    void initWidgets(Minecraft mc, Font font) {
        this.font = font;
        worldList = new WorldList(mc);
        newWorldButton = Button.builder(Component.literal("New world…"), b -> newWorld()).width(LIST_WIDTH).build();
        detailList = new DetailList(mc);
        inviteText = new StringWidget(200, 20, Component.empty(), font);
        acceptButton = Button.builder(Component.literal("Accept"), b -> firstInviteForMe(i -> respond(i, true)))
                .width(60).build();
        declineButton = Button.builder(Component.literal("Decline"), b -> firstInviteForMe(i -> respond(i, false)))
                .width(60).build();
        refreshWidgets();
    }

    private void firstInviteForMe(Consumer<ApiModels.PendingInvite> action) {
        if (invitesForMe != null && !invitesForMe.isEmpty()) action.accept(invitesForMe.getFirst());
    }

    private void refreshWidgets() {
        if (worldList == null) return;
        newWorldButton.active = !PoiConfig.get().isOffMode() && !(PoiConfig.get().isOnlineMode() && !PoiConfig.get().hasApiKey());
        rebuildWorldList();
        rebuildDetails();
        updateStrip();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(worldList);
        consumer.accept(newWorldButton);
        consumer.accept(detailList);
        consumer.accept(inviteText);
        consumer.accept(acceptButton);
        consumer.accept(declineButton);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        int left = screen.contentLeft();
        int right = screen.contentRight();
        int top = area.top() + PADDING_TOP;
        int bottom = area.bottom() - (hasInvitesForMe() ? STRIP_HEIGHT : 0);
        int detailLeft = left + LIST_WIDTH + GAP;

        worldList.updateSizeAndPosition(LIST_WIDTH, bottom - top - 24, left, top);
        newWorldButton.setPosition(left, bottom - 20);
        detailList.updateSizeAndPosition(right - detailLeft, bottom - top, detailLeft, top);

        int stripY = area.bottom() - STRIP_HEIGHT + 2;
        declineButton.setPosition(right - declineButton.getWidth(), stripY);
        acceptButton.setPosition(declineButton.getX() - 4 - acceptButton.getWidth(), stripY);
        inviteText.setPosition(left, stripY);
        inviteText.setWidth(acceptButton.getX() - left - 6);
        inviteText.setMaxWidth(acceptButton.getX() - left - 6);
    }

    /** The world list's status message, when there is one. */
    void extractOverlay(GuiGraphicsExtractor g) {
        if (worldsStatus != null && worldList != null) {
            int y = worldList.getY() + 8;
            for (var line : font.split(worldsStatus, LIST_WIDTH - 8)) {
                g.text(font, line, worldList.getX() + 4, y, 0xFFA0A0A0, false);
                y += 10;
            }
        }
    }

    private void updateStrip() {
        if (inviteText == null) return;
        boolean show = invitesForMe != null && !invitesForMe.isEmpty();
        inviteText.visible = acceptButton.visible = declineButton.visible = show;
        if (!show) return;
        ApiModels.PendingInvite inv = invitesForMe.getFirst();
        String who = inv.invitedBy != null ? inv.invitedBy : "Someone";
        String world = inv.worldName != null ? inv.worldName : "a world";
        String more = invitesForMe.size() > 1 ? " (+" + (invitesForMe.size() - 1) + " more)" : "";
        inviteText.setMessage(Component.literal("✉ " + who + " invited you to " + world
                + (inv.role != null ? " (" + roleLabel(inv.role) + ")" : "") + more));
    }

    private void rebuildWorldList() {
        if (worldList == null) return;
        List<WorldEntry> entries = worlds.stream().map(WorldEntry::new).toList();
        worldList.replaceEntries(entries);
        entries.stream().filter(e -> e.world.id.equals(highlightedId)).findFirst().ifPresent(worldList::setSelectedQuietly);
    }

    private void rebuildDetails() {
        if (detailList == null) return;
        List<Row> rows = new ArrayList<>();
        if (notice != null) rows.add(new TextRow(notice));
        ApiModels.WorldSummary world = highlighted();
        if (world == null) {
            if (worldsLoaded && worlds.isEmpty()) {
                rows.add(new TextRow(Component.literal("Create a POI world for this save or server with New world.")
                        .withStyle(ChatFormatting.GRAY)));
            } else if (worldsStatus == null) {
                rows.add(new TextRow(Component.literal("Pick a world on the left.").withStyle(ChatFormatting.GRAY)));
            }
            detailList.setRows(rows);
            sendButton = null;
            return;
        }
        addWorldRows(rows, world);
        detailList.setRows(rows);
    }

    private void addWorldRows(List<Row> rows, ApiModels.WorldSummary world) {
        PoiConfig cfg = PoiConfig.get();
        boolean current = world.id.equals(cfg.currentWorldId);

        rows.add(new TextRow(Component.literal(world.name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        String role = world.role == null ? "Your world" : "Your role: " + roleLabel(world.role);
        String origin = world.seed != null && !world.seed.isBlank() ? "Seed: " + world.seed
                : world.serverAddress != null ? "Server: " + world.serverAddress : null;
        rows.add(new TextRow(Component.literal(origin == null ? role : role + " · " + origin).withStyle(ChatFormatting.GRAY)));

        Button use = Button.builder(Component.literal(current ? "In use here" : "Use this world"), b -> useWorld(world))
                .width(110).build();
        use.active = !current;
        List<AbstractWidget> actions = new ArrayList<>(List.of(use));
        if (online() && world.isShared()) {
            actions.add(Button.builder(Component.literal("Leave…"), b -> confirmLeave(world)).width(70).build());
        }
        rows.add(new WidgetsRow(actions));

        String seed = PoiCommand.getCurrentSeed();
        if (seed != null && world.seed != null && !world.seed.isBlank() && !seed.equals(world.seed)) {
            rows.add(new TextRow(Component.literal("Saved with a different seed than the world you're in.")
                    .withStyle(ChatFormatting.YELLOW)));
        }

        if (!online() || world.role == null) {
            rows.add(new Spacer());
            rows.add(new TextRow(Component.literal(cfg.isOnlineMode() ? "Sharing needs an API key. Add one in Settings."
                    : "Sharing needs online mode. Set it up in Settings.").withStyle(ChatFormatting.GRAY)));
            sendButton = null;
            return;
        }
        if (detailsError != null) rows.add(new TextRow(Component.literal(detailsError).withStyle(ChatFormatting.RED)));

        rows.add(new Spacer());
        rows.add(heading("Members"));
        if (members == null) {
            rows.add(new TextRow(Component.literal("Loading…").withStyle(ChatFormatting.GRAY)));
        } else {
            for (ApiModels.Member m : members) {
                MutableComponent line = Component.literal(m.name != null ? m.name : "Unlinked member");
                if (m.isYou) line.append(Component.literal(" (you)").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(" · " + roleLabel(m.role)).withStyle(ChatFormatting.GRAY));
                rows.add(new TextRow(line));
            }
        }

        if (world.isAdmin()) addInviteRows(rows, world);
        else sendButton = null;

        rows.add(new Spacer());
        rows.add(new WidgetsRow(List.of(Button.builder(Component.literal("Manage members on the website…"),
                        b -> ConfirmLinkScreen.confirmLinkNow(screen, URI.create(cfg.getApiBaseUrl() + "/mc/poi")))
                .width(200).build())));
    }

    private void addInviteRows(List<Row> rows, ApiModels.WorldSummary world) {
        rows.add(new Spacer());
        rows.add(heading("Invite players on this server"));

        Set<String> memberNames = new HashSet<>();
        if (members != null) {
            for (ApiModels.Member m : members) if (m.name != null) memberNames.add(m.name.toLowerCase(Locale.ROOT));
        }
        List<GameProfile> candidates = SharingCommands.otherOnlinePlayers().stream()
                .filter(p -> !memberNames.contains(p.name().toLowerCase(Locale.ROOT)))
                .toList();
        // Forget picks for players who left
        picked.retainAll(candidates.stream().map(p -> p.name().toLowerCase(Locale.ROOT)).toList());

        if (candidates.isEmpty()) {
            rows.add(new TextRow(Component.literal("No one else is here to invite.").withStyle(ChatFormatting.GRAY)));
            sendButton = null;
        } else {
            int columnWidth = (detailList.getRowWidth() - 8) / 2;
            for (int i = 0; i < candidates.size(); i += 2) {
                List<AbstractWidget> pair = new ArrayList<>();
                for (GameProfile p : candidates.subList(i, Math.min(i + 2, candidates.size()))) {
                    String key = p.name().toLowerCase(Locale.ROOT);
                    pair.add(Checkbox.builder(Component.literal(p.name()), font)
                            .selected(picked.contains(key))
                            .maxWidth(columnWidth)
                            .onValueChange((box, on) -> {
                                if (on) picked.add(key);
                                else picked.remove(key);
                            })
                            .build());
                }
                rows.add(new WidgetsRow(pair, columnWidth));
            }
            CycleButton<String> roleButton = CycleButton.builder((String r) -> Component.literal(roleLabel(r)), inviteRole)
                    .withValues(INVITE_ROLES)
                    .create(0, 0, 130, 20, Component.literal("Role"), (b, r) -> inviteRole = r);
            sendButton = Button.builder(Component.literal("Send invites"), b -> sendInvites(world)).width(100).build();
            sendButton.active = !picked.isEmpty();
            rows.add(new WidgetsRow(List.of(roleButton, sendButton)));
        }

        if (world.id.equals(inviteResultsFor)) {
            for (ApiModels.InviteResult r : inviteResults) {
                String kind = r.kind != null ? r.kind : "";
                switch (kind) {
                    case "addressed" -> rows.add(new TextRow(Component.literal(r.name + ": invite sent to their game")
                            .withStyle(ChatFormatting.GREEN)));
                    case "member" -> rows.add(new TextRow(Component.literal(r.name + " is already a member")
                            .withStyle(ChatFormatting.GRAY)));
                    case "link" -> {
                        if (r.url == null) {
                            rows.add(new TextRow(Component.literal(r.name + ": invited (get the link from Share on the website)")));
                        } else {
                            String url = r.url;
                            rows.add(new LabelButtonRow(Component.literal(r.name + ": send them this link"),
                                    Button.builder(Component.literal("Copy link"), b -> {
                                        Minecraft.getInstance().keyboardHandler.setClipboard(url);
                                        notice = Component.literal("Copied " + r.name + "'s invite link. It works once and expires in 7 days.");
                                        rebuildDetails();
                                    }).width(70).build()));
                        }
                    }
                    default -> rows.add(new TextRow(Component.literal(r.name + ": " + (kind.isEmpty() ? "done" : kind))));
                }
            }
        }

        rows.add(new Spacer());
        rows.add(heading("Pending invites"));
        if (pendingInvites == null) {
            rows.add(new TextRow(Component.literal("Loading…").withStyle(ChatFormatting.GRAY)));
        } else if (pendingInvites.isEmpty()) {
            rows.add(new TextRow(Component.literal("None").withStyle(ChatFormatting.GRAY)));
        } else {
            for (ApiModels.Invite inv : pendingInvites) {
                MutableComponent label = Component.literal(inviteLabel(inv))
                        .append(Component.literal(" · " + roleLabel(inv.role) + " · " + (inv.addressed ? "in game" : "link")
                                + timeLeft(inv.expiresAt))
                                .withStyle(ChatFormatting.GRAY));
                rows.add(new LabelButtonRow(label,
                        Button.builder(Component.literal("Revoke"), b -> revoke(world, inv)).width(60).build()));
            }
        }
    }

    /** " · 6d left" until an ISO timestamp, or nothing if it can't be read. */
    private static String timeLeft(String expiresAt) {
        if (expiresAt == null) return "";
        try {
            long days = java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(expiresAt)).toDays();
            return days >= 1 ? " · " + days + "d left" : " · expires today";
        } catch (java.time.format.DateTimeParseException e) {
            return "";
        }
    }

    private static String inviteLabel(ApiModels.Invite invite) {
        return invite.label != null ? invite.label : "Unlabelled link";
    }

    private static String roleLabel(String role) {
        if (role == null) return "Owner";
        return switch (role) {
            case "read-only" -> "Read-only";
            case "contribute" -> "Contribute";
            case "admin" -> "Admin";
            case "owner" -> "Owner";
            default -> role;
        };
    }

    private TextRow heading(String text) {
        return new TextRow(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    // ── World list ────────────────────────────────────────────────────────────

    private class WorldList extends ObjectSelectionList<WorldEntry> {
        private boolean quiet;

        WorldList(Minecraft mc) {
            super(mc, LIST_WIDTH, 100, 0, 22);
        }

        void setSelectedQuietly(WorldEntry entry) {
            quiet = true;
            setSelected(entry);
            quiet = false;
        }

        @Override
        public void setSelected(WorldEntry entry) {
            super.setSelected(entry);
            if (!quiet && entry != null && !entry.world.id.equals(highlightedId)) {
                highlightedId = entry.world.id;
                notice = null;
                loadDetails();
            }
        }

        @Override
        public int getRowWidth() {
            return width - 10;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }
    }

    private class WorldEntry extends ObjectSelectionList.Entry<WorldEntry> {
        final ApiModels.WorldSummary world;

        WorldEntry(ApiModels.WorldSummary world) {
            this.world = world;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            boolean current = world.id.equals(PoiConfig.get().currentWorldId);
            int x = getContentX();
            int width = getContentWidth();
            if (current) {
                g.text(font, "●", x, getContentY() + 1, 0xFF55FF55, false);
                x += font.width("● ");
                width -= font.width("● ");
            }
            g.text(font, font.plainSubstrByWidth(world.name, width), x, getContentY() + 1, 0xFFFFFFFF, false);
            String detail = roleLabel(world.role) + (current ? " · in use" : "");
            g.text(font, font.plainSubstrByWidth(detail, width), x, getContentY() + 11, 0xFF909090, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(world.name + ", " + roleLabel(world.role));
        }
    }

    // ── Details pane ──────────────────────────────────────────────────────────

    private class DetailList extends ContainerObjectSelectionList<Row> {
        DetailList(Minecraft mc) {
            super(mc, 200, 100, 0, 14);
        }

        void setRows(List<Row> rows) {
            double scroll = scrollAmount();
            clearEntries();
            for (Row row : rows) addEntry(row, row.height);
            setScrollAmount(scroll);
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        final int height;
        final List<AbstractWidget> widgets;

        Row(int height, List<AbstractWidget> widgets) {
            this.height = height;
            this.widgets = widgets;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }

    private static class Spacer extends Row {
        Spacer() {
            super(6, List.of());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {}
    }

    private class TextRow extends Row {
        private final Component text;

        TextRow(Component text) {
            super(12, List.of());
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            g.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(text, getContentWidth())),
                    getContentX(), getContentY() + 2, 0xFFFFFFFF, false);
        }
    }

    /** Widgets left to right, each {@code columnWidth} apart (or packed when 0). */
    private static class WidgetsRow extends Row {
        private final int columnWidth;

        WidgetsRow(List<AbstractWidget> widgets) {
            this(widgets, 0);
        }

        WidgetsRow(List<AbstractWidget> widgets, int columnWidth) {
            super(24, widgets);
            this.columnWidth = columnWidth;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX();
            for (AbstractWidget w : widgets) {
                w.setPosition(x, getContentY() + 1);
                w.extractRenderState(g, mouseX, mouseY, delta);
                x += (columnWidth > 0 ? columnWidth : w.getWidth()) + 4;
            }
        }
    }

    /** A line of text with one button on the right. */
    private class LabelButtonRow extends Row {
        private final Component label;

        LabelButtonRow(Component label, Button button) {
            super(24, List.of(button));
            this.label = label;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            AbstractWidget button = widgets.getFirst();
            button.setPosition(getContentRight() - button.getWidth(), getContentY() + 1);
            button.extractRenderState(g, mouseX, mouseY, delta);
            int textWidth = button.getX() - getContentX() - 6;
            g.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(label, textWidth)),
                    getContentX(), getContentY() + 7, 0xFFFFFFFF, false);
        }
    }
}
