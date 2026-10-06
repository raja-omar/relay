package dev.relay.gui;

import java.util.List;

import dev.relay.RelayClient;
import dev.relay.common.GroupNames;
import dev.relay.common.PlayerNames;
import dev.relay.group.GroupState;
import dev.relay.group.PendingInvites;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;

/**
 * Create, join, invite, and leave. Lives with Hotkeys so Settings can stay identity-only.
 */
final class GroupsModule implements RelayModule {
	@Override
	public String id() {
		return "groups";
	}

	@Override
	public String name() {
		return "Groups";
	}

	@Override
	public String description() {
		return "Create a group, invite builders, and share placements.";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public boolean utility() {
		return true;
	}

	@Override
	public int badge() {
		return RelayClient.get().pendingInvites().size();
	}

	@Override
	public List<HotkeyBinding> hotkeys() {
		return List.of();
	}

	@Override
	public void render(FlowLayout content, RelayScreen overlay) {
		if (!RelayClient.get().isSignedIn()) {
			content.child(RelayUi.pageScroll(signedOut(overlay)));
			return;
		}

		RelayClient.get().group().ifPresentOrElse(
				group -> roster(content, overlay, group),
				() -> content.child(RelayUi.pageScroll(create(overlay))));
	}

	private static FlowLayout signedOut(RelayScreen overlay) {
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(8);
		inner.child(RelayUi.title("Groups"));
		inner.child(RelayUi.muted("Sign in with your Relay ID in Settings, then you can create or join a group."));
		inner.child(RelayUi.button("Open Settings", RelayUi.Kind.PRIMARY, true, "", overlay::openSettings));
		return inner;
	}

	private static FlowLayout create(RelayScreen overlay) {
		FlowLayout inner = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		inner.gap(8);
		inner.child(RelayUi.title("Groups"));
		inner.child(RelayUi.muted("You are not in a group yet."));
		inner.child(RelayUi.heading("Create"));
		inner.child(RelayUi.muted("Anyone you invite can share schematics with the rest of the group."));
		FlowLayout create = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		create.gap(6);
		create.verticalAlignment(VerticalAlignment.CENTER);
		create.child(overlay.field(Sizing.expand(), "Group name", overlay.groupDraft(), GroupNames.MAX_LENGTH, null,
				overlay::groupDraft, overlay::createGroup));
		create.child(RelayUi.button("Create", RelayUi.Kind.PRIMARY, true, "", overlay::createGroup));
		inner.child(create);

		List<PendingInvites.Invite> invites = RelayClient.get().pendingInvites().list();
		inner.child(RelayUi.section("Invitations"));
		if (invites.isEmpty()) {
			inner.child(RelayUi.muted("None waiting."));
			return inner;
		}

		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		rows.gap(4);
		for (PendingInvites.Invite invite : invites) {
			rows.child(inviteRow(overlay, invite));
		}
		inner.child(rows);
		return inner;
	}

	private static FlowLayout inviteRow(RelayScreen overlay, PendingInvites.Invite invite) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		row.gap(8);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.padding(Insets.of(2, 2, 2, 2));

		FlowLayout copy = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
		copy.gap(1);
		copy.child(RelayUi.label(invite.groupName(), RelayTheme.TEXT));
		copy.child(RelayUi.muted("from " + invite.inviterName()));
		row.child(copy);

		row.child(RelayUi.button("Accept", RelayUi.Kind.SUCCESS, true, "", () -> {
			overlay.selectInvite(invite.groupName());
			overlay.answerInvite(true);
		}));
		row.child(RelayUi.button("Decline", RelayUi.Kind.DANGER, true, "", () -> {
			overlay.selectInvite(invite.groupName());
			overlay.answerInvite(false);
		}));
		return row;
	}

	private static void roster(FlowLayout content, RelayScreen overlay, GroupState group) {
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		header.gap(8);
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(RelayUi.title(group.name()));
		header.child(RelayUi.spacer());
		header.child(RelayUi.status(group.onlineCount() + " online",
				group.onlineCount() > 0 ? RelayTheme.GOOD : RelayTheme.MUTED));
		header.child(RelayUi.button("Leave", RelayUi.Kind.DANGER, true, "Leave this group.", overlay::leaveGroup));
		content.child(header);

		content.child(RelayUi.heading("Members"));
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
		for (GroupState.Member member : group.members()) {
			boolean owner = member.id().equals(group.ownerId());
			rows.child(memberRow(member, owner));
		}
		content.child(RelayUi.list(rows, Sizing.expand()));

		content.child(RelayUi.section("Invite"));
		content.child(RelayUi.muted("Use their Minecraft name, as it appears in the tab list."));
		FlowLayout invite = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
		invite.gap(6);
		invite.verticalAlignment(VerticalAlignment.CENTER);
		invite.child(overlay.field(Sizing.expand(), "Player", overlay.inviteDraft(), PlayerNames.MAX_LENGTH, null,
				overlay::inviteDraft, overlay::invite));
		invite.child(RelayUi.button("Invite", RelayUi.Kind.PRIMARY, true, "", overlay::invite));
		content.child(invite);
	}

	private static FlowLayout memberRow(GroupState.Member member, boolean owner) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(20));
		row.gap(8);
		row.padding(Insets.of(0, 0, 6, 6));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.child(RelayUi.dot(member.online() ? RelayTheme.GOOD : RelayTheme.MUTED));
		row.child(RelayUi.label(member.name(), member.online() ? RelayTheme.TEXT : RelayTheme.MUTED));
		row.child(RelayUi.spacer());
		String role = owner ? "owner" : member.online() ? "online" : "offline";
		int roleColor = owner ? RelayTheme.ACCENT : member.online() ? RelayTheme.GOOD : RelayTheme.MUTED;
		row.child(RelayUi.label(role, roleColor));
		return row;
	}
}
