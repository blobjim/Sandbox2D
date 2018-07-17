package ritzow.sandbox.client.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ritzow.sandbox.client.util.SerializationProvider;
import ritzow.sandbox.client.world.entity.ClientPlayerEntity;
import ritzow.sandbox.data.ByteArrayDataReader;
import ritzow.sandbox.data.Bytes;
import ritzow.sandbox.data.DataReader;
import ritzow.sandbox.network.NetworkController;
import ritzow.sandbox.network.Protocol;
import ritzow.sandbox.network.Protocol.PlayerAction;
import ritzow.sandbox.network.TimeoutException;
import ritzow.sandbox.util.Utility;
import ritzow.sandbox.world.World;
import ritzow.sandbox.world.entity.Entity;
import ritzow.sandbox.world.entity.PlayerEntity;

//TODO run message sending in its own thread, optimize message sending for single thread
public class Client {
	private final InetSocketAddress serverAddress;
	private final NetworkController network;
	private final Object worldLock, playerLock, connectionLock;
	private volatile Status status;
	private volatile ConnectionState state;
	private Executor runner;
	
	private static enum Status {
		NOT_CONNECTED,
		REJECTED,
		CONNECTED,
		DISCONNECTED
	}
	
	private static final class ConnectionState {
		volatile World world;
		volatile ClientPlayerEntity player;
		volatile byte[] worldData;
		volatile int worldBytesRemaining;
	}
	
	public static final class ConnectionFailedException extends IOException {
		public ConnectionFailedException(String message) {
			super(message);
		}
	}
	
	/**
	 * Creates a client bound to the provided address
	 * @param bindAddress the local address to bind to.
	 * @throws IOException if an internal I/O error occurrs
	 * @throws SocketException if the local address could not be bound to.
	 * @throws ConnectionFailedException if the client could not connect to the specified server
	 */
	public static Client connect(InetSocketAddress bindAddress, InetSocketAddress serverAddress) 
			throws IOException, ConnectionFailedException {
		return new Client(bindAddress, serverAddress);
	}
	
	private Client(InetSocketAddress bindAddress, InetSocketAddress serverAddress) throws IOException {
		network = new NetworkController(Utility.getProtocolFamily(bindAddress.getAddress()), bindAddress, this::process);
		worldLock = new Object();
		playerLock = new Object();
		connectionLock = new Object();
		this.serverAddress = serverAddress;
		this.runner = Executors.newSingleThreadExecutor();
		connect();
	}
	
	/** Redirect all received message processing to the provided TaskQueue **/
	public void setExecutor(Executor processor) {
		if(runner instanceof ExecutorService)
			((ExecutorService)runner).shutdownNow().forEach(processor::execute); //transfer remaining tasks
		this.runner = processor;
	}
	
	public InetSocketAddress getServerAddress() {
		return serverAddress;
	}
	
	public InetSocketAddress getAddress() {
		return network.getBindAddress();
	}
	
	private void connect() throws ConnectionFailedException {
		network.start();
		try {
			byte[] packet = new byte[2];
			Bytes.putShort(packet, 0, Protocol.TYPE_CLIENT_CONNECT_REQUEST);
			network.sendReliable(serverAddress, packet, Protocol.RESEND_COUNT, Protocol.RESEND_INTERVAL);
		} catch(TimeoutException e) { //if client receives no ack, disconnect and throw
			disconnect(false);
			throw new ConnectionFailedException("request timed out");
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		//received ack on request packet, wait one second for response
		Utility.waitOnCondition(serverAddress, 1000, () -> status != Status.NOT_CONNECTED);
		
		if(status == Status.REJECTED) {
			disconnect(false);
			throw new ConnectionFailedException("connection rejected");
		} else if(status == Status.CONNECTED) {
			disconnect(false);
			throw new ConnectionFailedException("request timed out");
		}
	}
	
	private void process(InetSocketAddress sender, byte[] data) {
		if(sender.equals(serverAddress)) {
			if(status == Status.DISCONNECTED)
				throw new NotConnectedException();
			runner.execute(() -> {
				DataReader reader = new ByteArrayDataReader(data);
				onReceive(reader.readShort(), reader);
			});	
		}
	}
	
	private void onReceive(short type, DataReader data) {
		if(type == Protocol.TYPE_SERVER_CONNECT_ACKNOWLEDGMENT) {
			processServerConnectAcknowledgement(data);
		} else if(status == Status.CONNECTED) {
			Utility.waitOnCondition(connectionLock, () -> state != null);
			
			switch(type) {
			case Protocol.TYPE_CONSOLE_MESSAGE:
				System.out.println("[Server Message] " + new String(data.readBytes(data.remaining()), Protocol.CHARSET));
				break;
			case Protocol.TYPE_SERVER_WORLD_DATA:
				processReceiveWorldData(data);
				break;
			case Protocol.TYPE_SERVER_ENTITY_UPDATE:
				processUpdateEntity(data);
				break;
			case Protocol.TYPE_SERVER_ADD_ENTITY:
				processAddEntity(data);
				break;
			case Protocol.TYPE_SERVER_REMOVE_ENTITY:
				processRemoveEntity(data);
				break;
			case Protocol.TYPE_SERVER_CLIENT_DISCONNECT:
				processServerDisconnect(data);
				break;
			case Protocol.TYPE_SERVER_PLAYER_ID:
				processReceivePlayerEntityID(data);
				break;
			case Protocol.TYPE_SERVER_REMOVE_BLOCK:
				processServerRemoveBlock(data);
				break;
			case Protocol.TYPE_SERVER_PING:
				break;
			case Protocol.TYPE_SERVER_PLAYER_ACTION:
				PlayerEntity e = getEntityFromID(data.readInteger());
				e.processAction(PlayerAction.forCode(data.readByte()), data.readBoolean());
				break;
			default:
				throw new IllegalArgumentException("Client received message of unknown protocol " + type);
			}
		}
	}
	
	private void processServerConnectAcknowledgement(DataReader data) {
		byte response = data.readByte();
		switch(response) {
		case Protocol.CONNECT_STATUS_REJECTED:
			status = Status.REJECTED;
			break;
		case Protocol.CONNECT_STATUS_WORLD:
			status = Status.CONNECTED;
			state = new ConnectionState();
			int remaining = data.readInteger();
			state.worldBytesRemaining = remaining;
			state.worldData = new byte[remaining];
			Utility.notify(connectionLock);
			break;
		case Protocol.CONNECT_STATUS_LOBBY:
			status = Status.REJECTED;
			throw new UnsupportedOperationException("CONNECT_STATUS_LOBBY not supported");
		default:
			throw new RuntimeException("unknown connect ack type " + response);
		}
		
		Utility.notify(serverAddress);
	}
	
	public boolean isConnected() {
		return status == Status.CONNECTED;
	}
	
	public void disconnect() {
		checkConnected();
		disconnect(true);
	}
	
	private void disconnect(boolean notifyServer) {
		try {
			status = Status.DISCONNECTED;
			if(notifyServer) {
				network.sendReliable(serverAddress, Bytes.of(Protocol.TYPE_CLIENT_DISCONNECT), Protocol.RESEND_COUNT, Protocol.RESEND_INTERVAL);	
			}
		} catch(TimeoutException e) {
			//do nothing
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			network.stop();
			Utility.notify(connectionLock);
		}
	}
	
	public World getWorld() {
		checkConnected();
		Utility.waitOnCondition(worldLock, () -> state.world != null);
		return state.world;
	}
	
	public ClientPlayerEntity getPlayer() {
		checkConnected();
		Utility.waitOnCondition(playerLock, () -> state.player != null);
		return state.player;
	}
	
	private void checkConnected() {
		if(status != Status.CONNECTED)
			throw new IllegalStateException("client is not connected to a server");
	}
	
	private void sendReliable(byte[] data) {
		try{
			network.sendReliable(serverAddress, data, Protocol.RESEND_COUNT, Protocol.RESEND_INTERVAL);
		} catch(TimeoutException e) {
			disconnect(false);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unused")
	private void sendUnreliable(byte[] data) {
		checkConnected();
		try {
			network.sendUnreliable(serverAddress, data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendBombThrow(float angle) {
		checkConnected();
		byte[] packet = new byte[2 + 4];
		Bytes.putShort(packet, 0, Protocol.TYPE_CLIENT_BOMB_THROW);
		Bytes.putFloat(packet, 2, angle);
		sendReliable(packet);
	}
	
	public void sendBlockBreak(int x, int y) {
		checkConnected();
		byte[] packet = new byte[10];
		Bytes.putShort(packet, 0, Protocol.TYPE_CLIENT_BREAK_BLOCK);
		Bytes.putInteger(packet, 2, x);
		Bytes.putInteger(packet, 6, y);
		sendReliable(packet);
	}
	
	public void sendPlayerAction(PlayerAction action, boolean enable) {
		checkConnected();
		byte[] packet = new byte[4];
		Bytes.putShort(packet, 0, Protocol.TYPE_CLIENT_PLAYER_ACTION);
		packet[2] = action.getCode();
		Bytes.putBoolean(packet, 3, enable);
		sendReliable(packet);
	}
	
	@SuppressWarnings("unchecked")
	private <T extends Entity> T getEntityFromID(int ID) {
		for(Entity e : getWorld()) { //block until world is received so no NPEs happen
			if(e.getID() == ID) {
				return (T)e;
			}
		}
		throw new IllegalStateException("No entity with ID " + ID + " exists");
	}
	
	private void processServerRemoveBlock(DataReader data) {
		state.world.getForeground().destroy(state.world, data.readInteger(), data.readInteger());
	}
	
	private void processServerDisconnect(DataReader data) {
		disconnect(false);
		int length = data.readInteger();
		System.out.println("Disconnected from server: " + 
				new String(data.readBytes(data.remaining()), 0, length, Protocol.CHARSET));
	}
	
	private void processReceivePlayerEntityID(DataReader data) {
		int id = data.readInteger();
		state.player = getEntityFromID(id);
		Utility.notify(playerLock);
	}
	
	private void processRemoveEntity(DataReader data) {
		int id = data.readInteger();
		state.world.removeIf(e -> e.getID() == id);
	}
	
	private static <T> T deserialize(byte[] data, boolean compress) {
		return SerializationProvider.getProvider().deserialize(compress ? Bytes.decompress(data) : data);
	}
	
	private void processAddEntity(DataReader data) {
		try {
			boolean compressed = data.readBoolean();
			byte[] entity = data.readBytes(data.remaining());
			Entity e = deserialize(entity, compressed);
			state.world.forEach(o -> {
				if(o.getID() == e.getID())
					throw new IllegalStateException("cannot have two entities with the same ID");
			});
			state.world.add(e);
		} catch(ClassCastException e) {
			System.err.println("Error while deserializing received entity");
		}
	}
	
	private void processUpdateEntity(DataReader data) {
		int id = data.readInteger();
		World world = state.world;
		for(Entity e : world) {
			if(e.getID() == id) {
				e.setPositionX(data.readFloat());
				e.setPositionY(data.readFloat());
				e.setVelocityX(data.readFloat());
				e.setVelocityY(data.readFloat());
				return;
			}
		}
	}
	
	private void processReceiveWorldData(DataReader data) {
		ConnectionState state = this.state;
		if(state.worldData == null)
			throw new IllegalStateException("world head packet has not been received");
		synchronized(state.worldData) {
			int dataSize = data.remaining();
			Bytes.copy(data.readBytes(dataSize), state.worldData, state.worldData.length - state.worldBytesRemaining);
			boolean receivedAll = (state.worldBytesRemaining -= dataSize) == 0;
			System.out.println(Utility.formatSize(state.worldBytesRemaining) + " remaining of " + Utility.formatSize(state.worldData.length) + " of world data.");
			if(receivedAll) {
				new Thread(this::buildWorld, "World Build Task").start();
			}
		}
	}
	
	private void buildWorld() {
		System.out.print("Building world... ");
		long start = System.nanoTime();
		state.world = deserialize(state.worldData, Protocol.COMPRESS_WORLD_DATA);
		System.out.println("took " + Utility.formatTime(Utility.nanosSince(start)) + ".");
		state.worldData = null; //release the raw data to the garbage collector
		sendReliable(Bytes.of(Protocol.TYPE_CLIENT_WORLD_BUILT));
		Utility.notify(worldLock);
	}
}
