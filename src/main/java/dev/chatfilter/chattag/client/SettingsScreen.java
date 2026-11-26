package dev.chatfilter.chattag.client;

import dev.chatfilter.chattag.core.FilterBook;
import dev.chatfilter.chattag.core.RuleEngine;
import dev.chatfilter.chattag.core.ServerChatFormat;
import dev.chatfilter.chattag.core.Settings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

final class SettingsScreen extends Screen {
    private static final String KEY = "chatfilter_chattag.";
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 240;
    private static final int GAP = 4;
    private static final int CONTROL_HEIGHT = 18;
    private static final int PAGE_BUTTON_WIDTH = 78;
    private static final int ROW_TOP = 76;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_STEP = 18;
    private final Screen parent;
    private final ClientConfigStore store;
    private final List<RuleDraft> rules = new ArrayList<>();
    private final List<TagDraft> tags = new ArrayList<>();
    private final List<FieldLabel> labels = new ArrayList<>();
    private final List<ServerDraft> servers = new ArrayList<>();
    private Page page = Page.RULES;
    private int ruleIndex;
    private int tagIndex;
    private int serverIndex;
    private int left;
    private int formWidth;
    private int rowsPerPage;
    private boolean stackedPreview;
    private String sample = "LLLL!!!";
    private Component status;
    private boolean statusError;
    private Component before;
    private Component after;
    private EditBox prefixBox;
    private EditBox suffixBox;
    private EditBox nameColorBox;
    private RuleRowButton selectedRow;
    private EditBox matchedBox;
    private Button candidateButton, previousCandidate, nextCandidate, clearMatch;
    private List<ServerNameMatches.Candidate> candidates = List.of();
    private ServerNameMatches.Scope candidateScope;
    private int candidateIndex, candidateTicks;

    SettingsScreen(Screen parent, ClientConfigStore store) {
        super(tr("title"));
        this.parent = parent;
        this.store = store;
        store.settings().rules().forEach(r -> rules.add(new RuleDraft(r)));
        store.settings().tags().forEach(t -> tags.add(new TagDraft(t.account(), t.prefix(), t.suffix(), t.nameColor())));
        store.settings().serverFormats().forEach(f -> servers.add(new ServerDraft(f)));
    }

    @Override
    protected void init() {
        labels.clear();
        prefixBox = suffixBox = nameColorBox = null;
        selectedRow = null;
        matchedBox = null;
        candidateButton = previousCandidate = nextCandidate = clearMatch = null;
        formWidth = Math.max(0, Math.min(width - 12, 600));
        left = (width - formWidth) / 2;
        if (!fitsEditor()) {
            if (formWidth > 0 && height >= 24) button("cancel", left, height - 24, formWidth, this::onClose);
            return;
        }

        int half = (formWidth - GAP) / 2;
        int tab = (formWidth - 2 * GAP) / 3;
        button("rules", left, 16, tab, () -> page(Page.RULES)).active = page != Page.RULES;
        button("tags", left + tab + GAP, 16, tab, () -> page(Page.TAGS)).active = page != Page.TAGS;
        button("servers", left + 2 * (tab + GAP), 16, formWidth - 2 * (tab + GAP),
                () -> page(Page.SERVERS)).active = page != Page.SERVERS;
        switch (page) {
            case RULES -> initRules();
            case TAGS -> initTags();
            case SERVERS -> initServers();
        }

        Button save = button("save", left, height - 24, half, this::save);
        if (store.loadError() != null) {
            save.active = false;
            save.setTooltip(Tooltip.create(tr("load_error")));
        }
        button("cancel", left + half + GAP, height - 24, half, this::onClose);
        updatePreview();
    }

    private boolean fitsEditor() { return width >= MIN_WIDTH && height >= MIN_HEIGHT; }

    private void initRules() {
        int bookWidth = Math.max(108, formWidth / 3);
        int cell = (formWidth - bookWidth - 4 * GAP) / 4;
        button("add", left, 36, cell, this::add).active = rules.size() < Settings.MAX_RULES;
        button("delete", left + cell + GAP, 36, cell, this::delete).active = !rules.isEmpty();
        button("up", left + 2 * (cell + GAP), 36, cell, () -> move(-1)).active = ruleIndex > 0;
        button("down", left + 3 * (cell + GAP), 36, cell, () -> move(1)).active = ruleIndex + 1 < rules.size();
        button("add_book", left + formWidth - bookWidth, 36, bookWidth, this::chooseBook)
                .setTooltip(Tooltip.create(tr("help.add_book")));

        stackedPreview = height >= 270;
        int editorY = height - (stackedPreview ? 140 : 129);
        // The list grows into the free space above the editor, never into the editor or footer.
        rowsPerPage = 1 + (editorY - ROW_TOP - ROW_HEIGHT - 1) / ROW_STEP;
        int first = rulePage() * rowsPerPage;
        button("prev_page", left, 56, PAGE_BUTTON_WIDTH, () -> select(first - rowsPerPage)).active = first > 0;
        button("next_page", left + formWidth - PAGE_BUTTON_WIDTH, 56, PAGE_BUTTON_WIDTH, () -> select(first + rowsPerPage))
                .active = first + rowsPerPage < rules.size();
        for (int i = first; i < Math.min(first + rowsPerPage, rules.size()); i++) {
            RuleDraft rule = rules.get(i);
            int y = ROW_TOP + ROW_STEP * (i - first);
            int toggleWidth = 72;
            RuleRowButton row = addRenderableWidget(new RuleRowButton(i, y, formWidth - toggleWidth - GAP, rule));
            if (i == ruleIndex) selectedRow = row;
            addRenderableWidget(Button.builder(enabledLabel(rule), b -> {
                rule.enabled = !rule.enabled;
                b.setMessage(enabledLabel(rule));
                status = null;
                updatePreview();
            }).bounds(left + formWidth - toggleWidth, y, toggleWidth, ROW_HEIGHT).build());
        }

        if (!rules.isEmpty()) {
            RuleDraft rule = rules.get(ruleIndex);
            int half = (formWidth - GAP) / 2;
            button("match." + id(rule.match), left, editorY, half, () -> {
                rule.match = next(rule.match, Settings.Match.values());
                status = null;
                rebuildWidgets();
            });
            button("action." + id(rule.action), left + half + GAP, editorY, half, () -> {
                rule.action = next(rule.action, Settings.Action.values());
                status = null;
                rebuildWidgets();
            });
            field("pattern", editorY + 20, rule.pattern, Settings.MAX_RULE_TEXT, value -> rule.pattern = value)
                    .setEditable(rule.match != Settings.Match.L_SPAM);
            field("replacement", editorY + 40, rule.replacement, Settings.MAX_RULE_TEXT, value -> rule.replacement = value)
                    .setEditable(rule.action == Settings.Action.REPLACE);
        }
        field("sample", editorY + 60, sample, Settings.MAX_RULE_TEXT, value -> sample = value);
    }

    private int rulePage() { return ruleIndex / rowsPerPage; }
    private int pageCount() { return Math.max(1, (rules.size() + rowsPerPage - 1) / rowsPerPage); }

    private void initTags() {
        int cell = (formWidth - 3 * GAP) / 4;
        button("prev", left, 36, cell, () -> select(tagIndex - 1)).active = tagIndex > 0;
        button("next", left + cell + GAP, 36, cell, () -> select(tagIndex + 1)).active = tagIndex + 1 < tags.size();
        button("add", left + 2 * (cell + GAP), 36, cell, this::add).active = tags.size() < Settings.MAX_TAGS;
        button("delete", left + 3 * (cell + GAP), 36, cell, this::delete).active = !tags.isEmpty();
        if (!tags.isEmpty()) {
            TagDraft t = tags.get(tagIndex);
            field("account", 80, t.account, Settings.MAX_ACCOUNT, value -> t.account = value);
            prefixBox = legacyField("prefix", 102, t.prefix, Settings.MAX_AFFIX, value -> t.prefix = value);
            suffixBox = legacyField("suffix", 124, t.suffix, Settings.MAX_AFFIX, value -> t.suffix = value);
            nameColorBox = legacyField("name_color", 146, t.nameColor, Settings.MAX_NAME_COLOR, value -> t.nameColor = value);
        }
    }

    private void initServers() {
        int cell = (formWidth - 3 * GAP) / 4;
        button("prev", left, 36, cell, () -> select(serverIndex - 1)).active = serverIndex > 0;
        button("next", left + cell + GAP, 36, cell, () -> select(serverIndex + 1)).active = serverIndex + 1 < servers.size();
        button("add", left + 2 * (cell + GAP), 36, cell, this::add).active = servers.size() < Settings.MAX_SERVER_FORMATS;
        button("delete", left + 3 * (cell + GAP), 36, cell, this::delete).active = !servers.isEmpty();
        var current = button("current_server", left, 65, formWidth, this::fillCurrentServer);
        current.active = ServerChatTags.currentAddress(minecraft) != null;
        current.setTooltip(Tooltip.create(tr("help.current_server")));
        if (servers.isEmpty()) return;

        ServerDraft f = servers.get(serverIndex);
        field("server_address", 85, f.address, ServerChatFormat.MAX_ADDRESS, value -> f.setAddress(value));
        field("server_template", 105, f.template, ServerChatFormat.MAX_TEMPLATE, value -> {
            if (!f.template.equals(value)) f.clearMatch();
            f.template = value;
        });
        field("server_alias", 125, f.alias, ServerChatFormat.MAX_ALIAS, value -> f.alias = value);
        int actionWidth = Math.min(formWidth / 3, 97);
        field("search_name", 145, f.searchInput, ServerChatFormat.MAX_ALIAS, value -> {
            if (!value.equals(f.searchInput)) f.clearMatch();
            f.searchInput = value;
            if (value.isEmpty()) f.searchName = "";
        }, actionWidth + GAP);
        button("auto_match", left + formWidth - actionWidth, 145, actionWidth, () -> armSearch(f))
                .setTooltip(Tooltip.create(tr("help.auto_match")));
        int arrow = 22;
        previousCandidate = button("candidate_prev", left, 165, arrow, () -> moveCandidate(-1));
        nextCandidate = button("candidate_next", left + formWidth - arrow, 165, arrow, () -> moveCandidate(1));
        candidateButton = button("candidate_empty", left + arrow + GAP, 165, formWidth - 2 * (arrow + GAP),
                () -> stageCandidate(f));
        matchedBox = field("matched_name", 185, f.matchedName, ServerChatFormat.MAX_ALIAS, value -> {}, actionWidth + GAP);
        matchedBox.setEditable(false);
        clearMatch = button("clear_match", left + formWidth - actionWidth, 185, actionWidth, () -> {
            f.clearMatch();
            updateMatchWidgets();
        });
        candidates = List.of();
        candidateScope = null;
        candidateIndex = 0;
        refreshCandidates();
    }

    @Override
    public void tick() {
        super.tick();
        if (candidateButton != null && ++candidateTicks >= 10) {
            candidateTicks = 0;
            refreshCandidates();
        }
    }

    private void armSearch(ServerDraft f) {
        if (f.searchInput.isEmpty() || !ServerChatFormat.validSearchName(f.searchInput)) {
            error("invalid_search_name");
            return;
        }
        try {
            new ServerChatFormat(f.address, f.template, f.alias, f.searchInput, "", null);
        } catch (IllegalArgumentException e) {
            error("invalid_server");
            return;
        }
        f.searchName = f.searchInput;
        refreshCandidates();
        statusError = false;
        status = tr("search_draft");
    }

    private void refreshCandidates() {
        if (candidateButton == null) return;
        ServerDraft f = servers.get(serverIndex);
        var scope = ServerNameMatches.scope(minecraft);
        List<ServerNameMatches.Candidate> items = List.of();
        if (!f.searchName.isEmpty() && f.searchName.equals(f.searchInput)) {
            try {
                items = ServerNameMatches.candidates(minecraft, f.value());
            } catch (IllegalArgumentException ignored) {
                // An incomplete address/template/alias is a normal draft state.
            }
        }
        if (!items.equals(candidates) || !java.util.Objects.equals(candidateScope, scope)) {
            var previous = java.util.Objects.equals(candidateScope, scope) && !candidates.isEmpty()
                    ? candidates.get(candidateIndex) : null;
            candidates = items;
            candidateScope = scope;
            candidateIndex = previous == null ? 0 : Math.max(0, candidates.indexOf(previous));
        }
        updateMatchWidgets();
    }

    private void moveCandidate(int delta) {
        if (candidates.isEmpty()) return;
        candidateIndex = Math.clamp(candidateIndex + delta, 0, candidates.size() - 1);
        updateMatchWidgets();
    }

    private void stageCandidate(ServerDraft f) {
        var before = candidateScope;
        var selected = candidates.isEmpty() ? null : candidates.get(candidateIndex);
        refreshCandidates();
        if (selected == null || !selected.selectable() || !java.util.Objects.equals(before, candidateScope)
                || candidateScope == null || !candidates.contains(selected)) return;
        f.matchedName = selected.name();
        f.matchOwner = candidateScope.owner();
        f.pendingScope = candidateScope;
        updateMatchWidgets();
        statusError = false;
        status = tr("candidate_draft");
    }

    private void updateMatchWidgets() {
        var f = servers.get(serverIndex);
        if (!matchedBox.getValue().equals(f.matchedName)) matchedBox.setValue(f.matchedName);
        clearMatch.active = !f.matchedName.isEmpty();
        previousCandidate.active = candidateIndex > 0;
        nextCandidate.active = candidateIndex + 1 < candidates.size();
        var candidate = candidates.isEmpty() ? null : candidates.get(candidateIndex);
        candidateButton.active = candidate != null && candidate.selectable();
        if (candidate == null) {
            candidateButton.setMessage(tr(f.searchName.isEmpty() ? "button.candidate_empty" : "button.candidate_none"));
            candidateButton.setTooltip(Tooltip.create(tr("help.auto_match")));
        } else {
            String label = tr("button.candidate", candidateIndex + 1, candidates.size(), candidate.title()).getString();
            int available = candidateButton.getWidth() - 8;
            if (font.width(label) > available) label = font.plainSubstrByWidth(label, available - font.width("…")) + "…";
            candidateButton.setMessage(Component.literal(label));
            candidateButton.setTooltip(Tooltip.create(tr(candidate.selectable() ? "help.candidate" : "help.candidate_ambiguous",
                    candidate.title(), candidate.name())));
        }
    }

    private void fillCurrentServer() {
        String address = ServerChatTags.currentAddress(minecraft);
        if (address == null) return;
        for (int i = 0; i < servers.size(); i++) {
            if (address.equals(ServerChatFormat.addressKey(servers.get(i).address))) {
                select(i);
                return;
            }
        }
        if (servers.isEmpty()) servers.add(new ServerDraft(address, "", ""));
        else servers.get(serverIndex).setAddress(address);
        select(serverIndex);
    }

    boolean isLegacyBox(EditBox box) {
        return box == prefixBox || box == suffixBox || box == nameColorBox;
    }

    @Override
    protected void setInitialFocus() {
        if (prefixBox != null) setFocused(prefixBox);
        else super.setInitialFocus();
    }

    @Override
    protected void repositionElements() {
        // Resizing may repaginate; keep the selected RuleDraft by index and the active editor's caret.
        FieldLabel focused = labels.stream().filter(label -> label.box() == getFocused()).findFirst().orElse(null);
        int cursor = focused == null ? 0 : focused.box().getCursorPosition();
        super.repositionElements();
        if (focused != null) {
            for (FieldLabel label : labels) {
                if (label.name().equals(focused.name())) {
                    setFocused(label.box());
                    label.box().setCursorPosition(cursor);
                    label.box().setHighlightPos(cursor);
                    break;
                }
            }
        }
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        // A page/navigation button can rebuild the widgets before mouse dispatch tries to focus it.
        if (listener == null || children().contains(listener)) super.setFocused(listener);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!super.mouseClicked(mouseX, mouseY, button)) return false;
        // ContainerEventHandler focuses the clicked button after onPress; leave its editor focused instead.
        if (getFocused() instanceof InsertSectionButton insert) setFocused(insert.box);
        return true;
    }

    private void page(Page next) {
        page = next;
        status = null;
        rebuildWidgets();
    }

    private void select(int i) {
        switch (page) {
            case RULES -> ruleIndex = i;
            case TAGS -> tagIndex = i;
            case SERVERS -> serverIndex = i;
        }
        status = null;
        rebuildWidgets();
    }

    private void add() {
        if (page == Page.SERVERS) {
            servers.add(new ServerDraft("", "", ""));
            select(servers.size() - 1);
        } else if (page == Page.TAGS) {
            tags.add(new TagDraft("", "", "", ""));
            select(tags.size() - 1);
        } else {
            rules.add(new RuleDraft(new Settings.Rule(false, Settings.Match.PART, "word", Settings.Action.REPLACE, "")));
            select(rules.size() - 1);
        }
    }

    private void delete() {
        if (page == Page.SERVERS) {
            servers.remove(serverIndex);
            select(Math.min(serverIndex, Math.max(0, servers.size() - 1)));
        } else if (page == Page.TAGS) {
            tags.remove(tagIndex);
            select(Math.min(tagIndex, Math.max(0, tags.size() - 1)));
        } else {
            rules.remove(ruleIndex);
            select(Math.min(ruleIndex, Math.max(0, rules.size() - 1)));
        }
    }

    private void move(int delta) {
        Collections.swap(rules, ruleIndex, ruleIndex + delta);
        select(ruleIndex + delta);
    }

    private void chooseBook() {
        String file;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var patterns = stack.pointers(stack.UTF8("*.md"), stack.UTF8("*.json"));
            file = TinyFileDialogs.tinyfd_openFileDialog(tr("button.add_book").getString(), "", patterns,
                    tr("book_filter").getString(), false);
        } catch (LinkageError | RuntimeException e) {
            error("book_picker_error");
            return;
        }
        if (importBook(file)) rebuildWidgets();
    }

    private boolean importBook(String file) {
        if (file == null) return false; // Cancelled: preserve the draft, selection and status.
        List<Settings.Rule> incoming;
        try {
            incoming = FilterBook.read(Path.of(file));
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            // Do not log or display exceptions that might include the selected path.
            error("book_read_error");
            return false;
        }
        List<Settings.Rule> current;
        try {
            current = ruleValues();
        } catch (IllegalArgumentException e) {
            error("book_draft_error");
            return false;
        }
        List<Settings.Rule> merged;
        try {
            merged = FilterBook.merge(current, incoming);
        } catch (IllegalArgumentException e) {
            error("book_limit", Settings.MAX_RULES);
            return false;
        }
        int additional = merged.size() - current.size();
        if (additional > 0) {
            // Append only after validation, retaining existing RuleDraft instances and unsaved edits.
            rules.addAll(merged.subList(current.size(), merged.size()).stream().map(RuleDraft::new).toList());
            ruleIndex = current.size();
        }
        statusError = false;
        status = tr("book_added", additional);
        return additional > 0;
    }

    private void error(String key, Object... args) {
        statusError = true;
        status = tr(key, args);
    }

    private EditBox legacyField(String name, int y, String value, int maxLength, Consumer<String> update) {
        Component caption = tr("button.insert_section", String.valueOf(TextInput.SECTION_SIGN));
        int buttonWidth = Math.min(formWidth / 3, font.width(TextInput.literal(caption.getString())) + 12);
        EditBox box = field(name, y, value, maxLength, update, buttonWidth + GAP);
        addRenderableWidget(new InsertSectionButton(left + formWidth - buttonWidth, y, buttonWidth, caption, box));
        return box;
    }

    private EditBox field(String name, int y, String value, int maxLength, Consumer<String> update) {
        return field(name, y, value, maxLength, update, 0);
    }

    private EditBox field(String name, int y, String value, int maxLength, Consumer<String> update, int reservedWidth) {
        Component label = tr("field." + name);
        int labelWidth = Math.min(110, formWidth / 3);
        EditBox box = new EditBox(font, left + labelWidth, y, formWidth - labelWidth - reservedWidth, CONTROL_HEIGHT, label);
        labels.add(new FieldLabel(name, label, y + 5, labelWidth - 6, box));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setTooltip(Tooltip.create(tr("help." + name)));
        box.setResponder(text -> {
            update.accept(text);
            if (selectedRow != null) selectedRow.refresh();
            status = null;
            updatePreview();
        });
        return addRenderableWidget(box);
    }

    private Button button(String name, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(tr("button." + name), b -> action.run()).bounds(x, y, w, CONTROL_HEIGHT).build());
    }

    private static Component enabledLabel(RuleDraft rule) { return tr(rule.enabled ? "button.enabled" : "button.disabled"); }

    private void updatePreview() {
        if (page == Page.SERVERS) {
            before = after = null;
        } else if (page == Page.TAGS) {
            if (tags.isEmpty()) { before = after = null; return; }
            TagDraft t = tags.get(tagIndex);
            String account = Settings.isAccountName(t.account) ? t.account : "qaz";
            before = Component.literal(account);
            after = LegacyText.decorateName(LegacyText.colorPlayerName(before, account, t.nameColor, Style.EMPTY),
                    t.prefix, t.suffix);
        } else {
            before = Component.literal(sample);
            try {
                var result = RuleEngine.apply(ruleValues(), sample, List.of());
                String output = result.preview(sample);
                after = result.hidden() ? tr("hidden") : output.isEmpty() ? tr("empty_output") : Component.literal(output);
            } catch (IllegalArgumentException e) {
                after = tr("invalid_rule");
            }
        }
    }

    private List<Settings.Rule> ruleValues() { return rules.stream().map(RuleDraft::value).toList(); }

    private void save() {
        var formats = new ArrayList<ServerChatFormat>();
        var addresses = new HashSet<String>();
        for (int i = 0; i < servers.size(); i++) {
            ServerDraft draft = servers.get(i);
            if (!draft.searchName.equals(draft.searchInput)) {
                showServerError(i, "search_not_armed");
                return;
            }
            if (draft.pendingScope != null && !draft.pendingScope.equals(ServerNameMatches.scope(minecraft))) {
                draft.clearMatch();
                showServerError(i, "candidate_expired");
                return;
            }
            ServerChatFormat format;
            try {
                format = servers.get(i).value();
            } catch (IllegalArgumentException e) {
                showServerError(i, "invalid_server");
                return;
            }
            if (!addresses.add(format.address())) {
                showServerError(i, "duplicate_server");
                return;
            }
            formats.add(format);
        }
        for (int i = 0; i < tags.size(); i++) {
            if (!Settings.isValidNameColor(tags.get(i).nameColor)) {
                page = Page.TAGS;
                select(i);
                setFocused(nameColorBox);
                error("invalid_name_color");
                return;
            }
        }
        Settings next;
        try {
            next = new Settings(ruleValues(), tags.stream().map(TagDraft::value).toList(), formats);
        } catch (IllegalArgumentException e) {
            error("invalid");
            return;
        }
        try {
            store.save(next);
            onClose();
        } catch (IOException | SecurityException e) {
            ClientFeatures.LOG.warn("Could not save chat filter settings", e);
            error("save_error");
        }
    }

    private void showServerError(int i, String key) {
        page = Page.SERVERS;
        select(i);
        error(key);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!fitsEditor()) {
            if (height >= 48 && formWidth > 0) {
                center(g, tr("screen_small", MIN_WIDTH, MIN_HEIGHT), width / 2, height / 2 - 10, formWidth, 0xffffff);
            }
            return;
        }
        boolean loadFailed = store.loadError() != null;
        Component heading = loadFailed && status != null ? tr("load_error") : title;
        center(g, heading, width / 2, 4, formWidth, heading == title ? 0xffffff : 0xff8080);
        if (page == Page.SERVERS) {
            Component step = servers.isEmpty() ? tr("servers_empty") : tr("position", serverIndex + 1, servers.size());
            center(g, step, width / 2, 55, formWidth, 0xa0a0a0);
            if (servers.isEmpty()) {
                center(g, Component.literal(ServerChatFormat.RANK_EXAMPLE), width / 2, height - 64, formWidth, 0xe0e0e0);
                center(g, tr("servers_scope"), width / 2, height - 51, formWidth, 0xa0a0a0);
            }
        } else if (page == Page.TAGS) {
            Component step = tags.isEmpty() ? tr("empty_list") : tr("position", tagIndex + 1, tags.size());
            center(g, step, width / 2, 62, formWidth, 0xa0a0a0);
        } else {
            center(g, tr("rule_page", rulePage() + 1, pageCount()), width / 2, 61,
                    formWidth - 2 * (PAGE_BUTTON_WIDTH + GAP), 0xe0e0e0);
            if (rules.isEmpty()) center(g, tr("empty_list"), width / 2, ROW_TOP + 4, formWidth, 0xa0a0a0);
        }
        for (FieldLabel label : labels) drawLine(g, label.text(), left, label.y(), label.width(), 0xe0e0e0);
        if (before != null) {
            if (page == Page.TAGS || stackedPreview) {
                drawLine(g, tr("before", before), left, height - 59, formWidth, 0xe0e0e0);
                drawLine(g, tr("after", after), left, height - 48, formWidth, 0xe0e0e0);
            } else {
                int half = (formWidth - GAP) / 2;
                drawLine(g, tr("before", before), left, height - 48, half, 0xe0e0e0);
                drawLine(g, tr("after", after), left + half + GAP, height - 48, half, 0xe0e0e0);
            }
        }
        boolean badNameColor = nameColorBox != null && !Settings.isValidNameColor(tags.get(tagIndex).nameColor);
        Component note = status != null ? status : loadFailed ? tr("load_error") : badNameColor ? tr("invalid_name_color")
                : page == Page.RULES ? tr("order_hint") : null;
        int color = status != null ? (statusError ? 0xff8080 : 0x90e890) : loadFailed || badNameColor ? 0xff8080 : 0xa0a0a0;
        if (note != null) center(g, note, width / 2, height - 36, formWidth, color);
    }

    private void drawLine(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(text, w - 1)), x, y, color);
    }

    private void center(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.drawCenteredString(font, Language.getInstance().getVisualOrder(font.substrByWidth(text, w - 2)), x, y, color);
    }

    private static Component tr(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private static String id(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static <T extends Enum<T>> T next(T current, T[] values) { return values[(current.ordinal() + 1) % values.length]; }
    private record FieldLabel(String name, Component text, int y, int width, EditBox box) {}

    private final class RuleRowButton extends Button {
        private final int rowIndex;
        private final RuleDraft rule;

        RuleRowButton(int index, int y, int width, RuleDraft rule) {
            super(left, y, width, ROW_HEIGHT, Component.empty(), b -> {
                if (ruleIndex != index) select(index);
            }, DEFAULT_NARRATION);
            this.rowIndex = index;
            this.rule = rule;
            refresh();
        }

        void refresh() {
            Component text = rule.match == Settings.Match.L_SPAM ? tr("button.match.l_spam") : Component.literal(rule.pattern);
            setMessage(tr(rowIndex == ruleIndex ? "selected_rule_row" : "rule_row", rowIndex + 1, text));
        }

        @Override
        public void renderString(GuiGraphics g, Font font, int color) {
            String text = getMessage().getString();
            int available = getWidth() - 9;
            if (font.width(text) > available) text = font.plainSubstrByWidth(text, available - font.width("…")) + "…";
            g.drawString(font, text, getX() + 4, getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    rowIndex == ruleIndex ? 0xffc0e8ff : color);
        }
    }

    private final class InsertSectionButton extends Button {
        private final EditBox box;

        InsertSectionButton(int x, int y, int width, Component caption, EditBox box) {
            super(x, y, width, CONTROL_HEIGHT, caption, button -> {
                SettingsScreen.this.setFocused(box);
                box.charTyped(TextInput.SECTION_SIGN, 0);
            }, DEFAULT_NARRATION);
            this.box = box;
            setTooltip(Tooltip.create(tr("help.insert_section")));
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            // The normal Component renderer interprets (and hides) the section sign as legacy formatting.
            String caption = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 4);
            graphics.drawCenteredString(font, TextInput.literal(caption), getX() + getWidth() / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1, color);
        }
    }

    private static final class RuleDraft {
        boolean enabled;
        Settings.Match match;
        String pattern;
        Settings.Action action;
        String replacement;

        RuleDraft(Settings.Rule rule) {
            enabled = rule.enabled();
            match = rule.match();
            pattern = rule.pattern();
            action = rule.action();
            replacement = rule.replacement();
        }

        Settings.Rule value() { return new Settings.Rule(enabled, match, pattern, action, replacement); }
    }

    private enum Page { RULES, TAGS, SERVERS }

    private static final class ServerDraft {
        String address, template, alias;
        String searchInput = "", searchName = "", matchedName = "";
        UUID matchOwner;
        ServerNameMatches.Scope pendingScope;

        ServerDraft(String address, String template, String alias) {
            this.address = address;
            this.template = template;
            this.alias = alias;
        }

        ServerDraft(ServerChatFormat f) {
            this(f.address(), f.template(), f.alias());
            searchInput = searchName = f.searchName();
            matchedName = f.matchedName();
            matchOwner = f.matchOwner();
        }

        void setAddress(String value) {
            if (!java.util.Objects.equals(ServerChatFormat.addressKey(address), ServerChatFormat.addressKey(value))) clearMatch();
            address = value;
        }

        void clearMatch() {
            matchedName = "";
            matchOwner = null;
            pendingScope = null;
        }

        ServerChatFormat value() { return new ServerChatFormat(address, template, alias, searchName, matchedName, matchOwner); }
    }

    private static final class TagDraft {
        String account, prefix, suffix, nameColor;
        TagDraft(String account, String prefix, String suffix, String nameColor) {
            this.account = account;
            this.prefix = prefix;
            this.suffix = suffix;
            this.nameColor = nameColor;
        }
        Settings.Tag value() { return new Settings.Tag(account, prefix, suffix, nameColor); }
    }
}
