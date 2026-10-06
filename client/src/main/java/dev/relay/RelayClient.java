package dev.relay;

import java.util.Optional;
import java.util.UUID;

import dev.relay.chat.ChatMessages;
import dev.relay.common.AccessTokens;
import dev.relay.common.PlacementNames;
import dev.relay.common.RecentIds;
import dev.relay.common.SchematicLimits;
import dev.relay.common.protocol.Message;
import dev.relay.common.protocol.MessageType;
import dev.relay.common.protocol.PacketReader;
import dev.relay.common.PingLocations;
import dev.relay.common.protocol.PacketWriter;
import dev.relay.common.protocol.PingPackets;
import dev.relay.common.protocol.ProtocolException;
import dev.relay.common.protocol.SharePackets;
import dev.relay.ping.BlockPings;
import dev.relay.group.GroupState;
import dev.relay.group.PendingInvites;
import dev.relay.litematica.LitematicaIntegration;
import dev.relay.network.SocketClient;
import dev.relay.network.Target;
import dev.relay.schematic.PendingShares;
import dev.relay.schematic.SchematicException;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

/**
 * The mod's one moving part: the connection, the group it knows about, and the requests commands
 * and the GUI make of it.
 *
 * <p>Messages arrive on a network thread and are handled on Minecraft's thread, so
 * {@link #group()} and the fields behind it are only ever touched from there. That is why none of
 * this needs locking.
 *
 * <p>Commands do not wait for answers. They send a request and return; whatever the server decides
 * arrives later as a notice or an error, and reaches the player the same way news from other members
 * does.
 */
public final class RelayClient implements SocketClient.Listener {
	private static final int REMEMBERED_SHARES = 64;

	private static RelayClient instance;

	private final RelayConfig config;
	private final SocketClient socket = new SocketClient(this);
	private final RecentIds receivedShares = new RecentIds(REMEMBERED_SHARES);
	private final PendingShares pendingShares = new PendingShares(REMEMBERED_SHARES);
	private final PendingInvites pendingInvites = new PendingInvites();

	private GroupState group;
	private boolean signedIn;

	private RelayClient(RelayConfig config) {
		this.config = config;
	}

	static RelayClient create(RelayConfig config) {
		instance = new RelayClient(config);
		return instance;
	}

	/** The one instance, created while the mod starts up. */
	public static RelayClient get() {
		if (instance == null) {
			throw new IllegalStateException(ModInfo.NAME + " has not started yet");
		}

		return instance;
	}

	public RelayConfig config() {
		return config;
	}

	public SocketClient.Status status() {
		return socket.status();
	}

	public String address() {
		return socket.address();
	}

	public boolean isSignedIn() {
		return signedIn && socket.status() == SocketClient.Status.CONNECTED;
	}

	/** The group this player is in, as far as the server has told us. */
	public Optional<GroupState> group() {
		return Optional.ofNullable(group);
	}

	public PendingInvites pendingInvites() {
		return pendingInvites;
	}

	public PendingShares pendingShares() {
		return pendingShares;
	}

	/** Connects to the server in the config, if one is set and a player id is stored. */
	public void connect() {
		if (!config.hasServer()) {
			ChatMessages.show(ChatMessages.error("No schematic server set. Use /" + ModInfo.ID
					+ " connect <host> once, or edit " + ModInfo.ID + ".properties."));
			return;
		}

		if (!config.hasToken()) {
			ChatMessages.show(ChatMessages.error("No player id set. Use /" + ModInfo.ID
					+ " login <id> — the server operator issues these."));
			return;
		}

		if (!config.hasTlsPin()) {
			ChatMessages.show(ChatMessages.error("No TLS pin. Use /" + ModInfo.ID
					+ " connect <host> <port> <pin>, or pack tlsPin= in the jar."));
			return;
		}

		socket.connect(new Target(config.host(), config.port(), config.token(), config.tlsPin()));
	}

	/** Stores the issued id and connects if a server is already configured. */
	public void login(String token) {
		if (!AccessTokens.isWellFormed(token)) {
			ChatMessages.show(ChatMessages.error("That is not a player id."));
			return;
		}

		config.setToken(token);

		if (!config.hasServer()) {
			ChatMessages.show(ChatMessages.info("Saved your id. Use /" + ModInfo.ID
					+ " connect <host> to pick a server."));
			return;
		}

		ChatMessages.show(ChatMessages.info("Saved your id. Connecting..."));
		connect();
	}

	/** Points at a different server, remembers it, and connects. */
	public void connectTo(String host, int port) {
		config.setServer(host, port);
		connect();
	}

	public void connectTo(String host, int port, String tlsPin) {
		config.setServer(host, port, tlsPin);
		connect();
	}

	public void disconnect() {
		if (socket.status() == SocketClient.Status.DISCONNECTED) {
			ChatMessages.show(ChatMessages.info("Not connected."));
			return;
		}

		socket.disconnect("you asked to disconnect");
	}

	public void shutdown() {
		socket.shutdown();
	}

	public boolean createGroup(String name) {
		return request(new PacketWriter().writeString(name).toMessage(MessageType.GROUP_CREATE));
	}

	public boolean invite(String playerName) {
		return request(new PacketWriter().writeString(playerName).toMessage(MessageType.GROUP_INVITE));
	}

	public boolean accept(String groupName) {
		if (!request(new PacketWriter().writeString(groupName).toMessage(MessageType.GROUP_ACCEPT))) {
			return false;
		}

		pendingInvites.take(groupName);
		return true;
	}

	public boolean decline(String groupName) {
		if (!request(new PacketWriter().writeString(groupName).toMessage(MessageType.GROUP_DECLINE))) {
			return false;
		}

		pendingInvites.take(groupName);
		return true;
	}

	public boolean leave() {
		return request(Message.of(MessageType.GROUP_LEAVE));
	}

	public boolean refreshGroup() {
		return request(Message.of(MessageType.GROUP_INFO));
	}

	/** Shares the placement currently selected in Litematica, if there is one. */
	public void shareSelectedPlacement() {
		LitematicaIntegration.selectedPlacement().ifPresentOrElse(
				this::sharePlacement,
				() -> ChatMessages.show(ChatMessages.error("Select a placement first.")));
	}

	/**
	 * Sends the placement as it currently sits in Litematica. Recipients get a chat offer to
	 * download it; nothing is loaded until they click.
	 */
	public void sharePlacement(SchematicPlacement placement) {
		if (!isSignedIn()) {
			ChatMessages.show(ChatMessages.error("Not connected to a schematic server."));
			ChatMessages.show(ChatMessages.info("Connect with /" + ModInfo.ID + " connect <host>."));
			return;
		}

		if (group == null) {
			ChatMessages.show(ChatMessages.error("You are not in a group."));
			return;
		}

		try {
			String name = LitematicaIntegration.placementName(placement);

			if (!PlacementNames.isValid(name)) {
				ChatMessages.show(ChatMessages.error(PlacementNames.RULES));
				return;
			}

			byte[] data = LitematicaIntegration.serializePlacement(placement);
			UUID transferId = UUID.randomUUID();
			receivedShares.add(transferId);

			if (!request(SharePackets.share(transferId, name, data))) {
				ChatMessages.show(ChatMessages.error("Not connected to a schematic server."));
			}
		} catch (SchematicException failed) {
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	/** Loads a pending share into Litematica. Nothing is written to disk. */
	public void downloadShare(UUID transferId) {
		PendingShares.Share share = pendingShares.take(transferId).orElse(null);

		if (share == null) {
			ChatMessages.show(ChatMessages.shareUnavailable());
			return;
		}

		try {
			LitematicaIntegration.loadSharedPlacement(share.data());
			ChatMessages.show(ChatMessages.schematicDownloaded(share.name()));
		} catch (SchematicException failed) {
			pendingShares.put(share);
			ChatMessages.show(ChatMessages.error(failed.getMessage()));
		}
	}

	/**
	 * Sends a ping to the rest of the group when sharing is on and this client is in one.
	 * The beam on this screen is already drawn by the caller.
	 */
	public void sharePing(int x, int y, int z, int face, String dimension) {
		if (!config.pingShare() || group == null || !PingLocations.isValid(x, y, z, face, dimension)) {
			return;
		}
		request(PingPackets.ping(x, y, z, face, dimension));
	}

	/** @return false when there is no connection to send it down */
	private boolean request(Message message) {
		return isSignedIn() && socket.send(message);
	}

	@Override
	public void onConnected() {
		ModInfo.LOG.info("Connected to {}", socket.address());
	}

	@Override
	public void onMessage(Message message) {
		// Straight onto Minecraft's thread: everything below touches the game.
		Minecraft.getInstance().execute(() -> handleOnClientThread(message));
	}

	@Override
	public void onDisconnected(String reason, boolean willRetry) {
		Minecraft.getInstance().execute(() -> {
			boolean wasSignedIn = signedIn;
			signedIn = false;
			group = null;
			pendingInvites.clear();

			if (wasSignedIn) {
				ChatMessages.show(ChatMessages.error("Disconnected from the schematic server: " + reason
						+ (willRetry ? ". Reconnecting..." : ".")));
			} else if (!willRetry) {
				ChatMessages.show(ChatMessages.error(capitalise(reason) + "."));
			}
		});
	}

	private void handleOnClientThread(Message message) {
		try {
			switch (message.type()) {
				case AUTH_RESULT -> onAuthResult(message);
				case GROUP_UPDATE -> {
					group = GroupState.read(message).orElse(null);

					if (group != null) {
						pendingInvites.take(group.name());
					}
				}
				case INVITE -> onInvite(message);
				case SCHEM_SHARED -> onSchematicShared(message);
				case BLOCK_PINGED -> onBlockPinged(message);
				case NOTICE -> ChatMessages.show(ChatMessages.info(message.reader().readString()));
				case ERROR -> ChatMessages.show(ChatMessages.error(message.reader().readString()));
				case PONG -> {
					// Keepalive answer, nothing to do.
				}
				default -> ModInfo.LOG.debug("Ignoring {}", message);
			}
		} catch (ProtocolException malformed) {
			ModInfo.LOG.warn("Could not read {}", message, malformed);

			if (message.type() == MessageType.SCHEM_SHARED) {
				ChatMessages.show(ChatMessages.error("That share was damaged and could not be received."));
			}
		}
	}

	private void onAuthResult(Message message) throws ProtocolException {
		PacketReader reader = message.reader();

		if (reader.readBoolean()) {
			signedIn = true;
			ChatMessages.show(ChatMessages.success("Connected to " + socket.address() + "."));
			return;
		}

		String reason = reader.readString();
		ChatMessages.show(ChatMessages.error("The schematic server turned us away: " + reason));

		// Retrying would only be turned away again, so stop until the player does something.
		socket.disconnect(reason);
	}

	private void onInvite(Message message) throws ProtocolException {
		PacketReader reader = message.reader();
		String groupName = reader.readString();
		String inviterName = reader.readString();

		pendingInvites.put(groupName, inviterName);
		ChatMessages.show(ChatMessages.invite(groupName, inviterName));
		playInviteSound();
	}

	private void onBlockPinged(Message message) throws ProtocolException {
		PacketReader reader = message.reader();
		reader.readString();
		int x = reader.readInt();
		int y = reader.readInt();
		int z = reader.readInt();
		int face = reader.readInt();
		String dimension = reader.readString();

		if (!PingLocations.isValid(x, y, z, face, dimension)) {
			return;
		}

		BlockPings.receive(Minecraft.getInstance(), x, y, z, face, dimension);
	}

	private void onSchematicShared(Message message) throws ProtocolException {
		PacketReader reader = message.reader();
		UUID transferId = reader.readUuid();
		String senderName = reader.readString();
		String name = reader.readString();
		byte[] data = reader.readBytes(SchematicLimits.MAX_BYTES);

		if (!receivedShares.add(transferId) || !pendingShares.put(
				new PendingShares.Share(transferId, senderName, name, data))) {
			ChatMessages.show(ChatMessages.alreadyHaveShare());
			return;
		}

		ChatMessages.show(ChatMessages.schematicOffered(senderName, name, transferId.toString()));
	}

	/** An invitation is easy to miss in a busy chat, so it makes a noise. */
	private static void playInviteSound() {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6f, 1.4f);
		}
	}

	private static String capitalise(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
