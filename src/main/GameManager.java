package main;

import audio.AudioSystem;
import graphics.Background;
import graphics.GraphicsManager;
import input.Controls;
import input.EventManager;
import input.InputManager;
import input.controller.EntityController;
import input.controller.InteractionController;
import input.controller.TrackingCameraController;
import input.handler.KeyHandler;
import input.handler.WindowCloseHandler;
import java.io.IOException;
import java.net.*;
import resource.Models;
import resource.Sounds;
import ui.ElementManager;
import util.Synchronizer;
import world.World;
import world.WorldManager;
import world.block.DirtBlock;
import world.block.GrassBlock;
import world.block.RedBlock;
import world.entity.Player;

public final class GameManager implements Runnable, WindowCloseHandler, KeyHandler {
	private EventManager eventManager;
	private GraphicsManager graphicsManager;
	private WorldManager worldManager;
	private ClientUpdateManager clientUpdateManager;
	
	private volatile boolean exit;
	
	public GameManager(EventManager eventManager) {
		this.eventManager = eventManager;
	}
	
	public void run() {
		Synchronizer.waitForSetup(eventManager);
		eventManager.getDisplay().getInputManager().getWindowCloseHandlers().add(this);
		eventManager.getDisplay().getInputManager().getKeyHandlers().add(this);
		new Thread(graphicsManager = new GraphicsManager(eventManager.getDisplay()), "Graphics Manager").start();
		
		AudioSystem.start();
		
		Synchronizer.waitForSetup(graphicsManager);
		
		World world = new World(500, 200, 0.015f);
		for(int column = 0; column < world.getForeground().getWidth(); column++) {
			double height = world.getForeground().getHeight()/2;
			height += (Math.sin(column * 0.1f) + 1) * (world.getForeground().getHeight() - height) * 0.05f;
			
			for(int row = 0; row < height; row++) {
				if(Math.random() < 0.005) {
					world.getForeground().set(column, row, new RedBlock());
				} else {
					world.getForeground().set(column, row, new DirtBlock());
				}
				world.getBackground().set(column, row, new DirtBlock());
			}
			world.getForeground().set(column, (int)height, new GrassBlock());
			world.getBackground().set(column, (int)height, new DirtBlock());
		}
		
		Player player = new Player("blobjim");
		player.setPositionX(world.getForeground().getWidth()/2);
		player.setPositionY(world.getForeground().getHeight());
		world.getEntities().add(player);
		
		EntityController playerController = new EntityController(player, world, 0.2f);
		playerController.link(eventManager.getDisplay().getInputManager());
		
		InteractionController cursorController = new InteractionController(player, world, graphicsManager.getRenderer().getCamera(), 200);
		cursorController.link(eventManager.getDisplay().getInputManager());
		
		TrackingCameraController cameraController = new TrackingCameraController(graphicsManager.getRenderer().getCamera(), player, 0.005f, 0.05f, 0.6f);
		cameraController.link(eventManager.getDisplay().getInputManager());
		
		ElementManager manager = new ElementManager();
		eventManager.getDisplay().getInputManager().getCursorPosHandlers().add(manager);
		eventManager.getDisplay().getInputManager().getMouseButtonHandlers().add(manager);
		eventManager.getDisplay().getInputManager().getFramebufferSizeHandlers().add(manager);
		
		clientUpdateManager = new ClientUpdateManager();
		
		clientUpdateManager.getUpdatables().add(playerController);
		clientUpdateManager.getUpdatables().add(cursorController);
		clientUpdateManager.getUpdatables().add(cameraController);
		clientUpdateManager.getUpdatables().add(manager);
		clientUpdateManager.link(eventManager.getDisplay().getInputManager());
		
		new Thread(clientUpdateManager, "Client Updater").start();
		new Thread(worldManager = new WorldManager(world), "World Manager " + world.hashCode()).start();
		
		graphicsManager.getRenderables().add(new Background(Models.CLOUDS_BACKGROUND));
		graphicsManager.getRenderables().add(world);
		graphicsManager.getRenderables().add(manager);
		
		/*
		 * TODO networking:
		 * Create ServerSocket on game's port on another thread
		 * ServerSocket listens for next connection
		 * On a separate thread, Client creates a Socket
		 * socket connects to Server
		 * Server accepts connection
		 * server creates threads to handle socket's input/output
		 * if in lobby, server sends Lobby info
		 * else if in game, server sends World info, Gamemode info
		 */
		
		new Thread("Datagram Server") {
			public void run() {
				try {
					DatagramSocket server = new DatagramSocket(50000); //for actual server: new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), 50000)
					byte[] bufferArray = new byte[64000]; //make the buffer the max size possible
					DatagramPacket buffer = new DatagramPacket(bufferArray, bufferArray.length);
					System.out.println("Server waiting for data...");
					server.receive(buffer);
					System.out.println("Server received message: " + new String(buffer.getData(), buffer.getOffset(), buffer.getLength()));
					server.close();
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
		
		new Thread("Datagram Client") {
			public void run() {
				try {
					DatagramSocket client = new DatagramSocket(); //for actual game: new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), 50000)
					byte[] bufferArray = ("Lorem ipsum dolor sit amet").getBytes();
					client.send(new DatagramPacket(bufferArray, bufferArray.length, new InetSocketAddress("localhost", 50000)));
					System.out.println("Client sent message");
					client.close();
				} catch(BindException e) {
					e.printStackTrace();
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
		
		synchronized(eventManager) {
			eventManager.setReadyToDisplay();
			eventManager.notifyAll();
		}
		
//		Memory usage/gc
//		while(!exit) {
//			System.out.println("memory usage " + ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) * 0.000001) + " MB");
//			//System.gc();
//			try {
//				Thread.sleep(100);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
		
		synchronized(this) {
			try {
				while(!exit) {
					this.wait();
				}
			} catch(InterruptedException e) {
				System.err.println("Game Manager interrupted while waiting to exit");
			}
		}
		
 		clientUpdateManager.exit();
		Sounds.deleteAll();
		AudioSystem.stop();
		worldManager.exit();
		Synchronizer.waitForExit(graphicsManager);
		Synchronizer.waitForExit(eventManager);
		System.exit(0);
	}

	@Override
	public synchronized void windowClose() {
		this.exit = true;
		this.notifyAll();
	}

	@Override
	public synchronized void keyboardButton(int key, int scancode, int action, int mods) {
		if(key == Controls.KEYBIND_QUIT && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
			synchronized(this) {
				this.exit = true;
				this.notifyAll();
			}
		}
		
        else if(key == Controls.KEYBIND_FULLSCREEN && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            eventManager.getDisplay().setFullscreen(!eventManager.getDisplay().getFullscreen());
        }
	}

	@Override
	public void link(InputManager manager) {
		manager.getKeyHandlers().add(this);
	}

	@Override
	public void unlink(InputManager manager) {
		manager.getKeyHandlers().remove(this);
	}
}
