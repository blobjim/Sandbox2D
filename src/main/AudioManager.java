package main;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

import audio.AudioBuffer;
import audio.Sound;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;

public class AudioManager implements Runnable, Installable, Exitable {
	private volatile boolean setupComplete;
	private volatile boolean exit;
	private volatile boolean finished;
	private volatile LinkedList<Sound> soundQueue;

	@Override
	public void run() {
		alcMakeContextCurrent(alcCreateContext(alcOpenDevice((ByteBuffer)null), (IntBuffer)null));
		AL.createCapabilities(ALC.createCapabilities(alcGetContextsDevice(alcGetCurrentContext())));
		soundQueue = new LinkedList<Sound>();
		
		int error;
		while((error = alGetError()) != 0) {
			System.err.println("OpenAL Error " + error);
		}
		
//		File audioFile = new File("resources/assets/audio/bloopBloop.wav");
//		
//		try {
//			AudioInputStream stream = AudioSystem.getAudioInputStream(audioFile);
//			byte[] data = new byte[2];
//			while(stream.available() > 0) {
//				stream.read(data);
//				System.out.println(Arrays.toString(data) + ", ");
//				if(stream.available() % 4 == 0) System.out.println();
//			}
//			stream.close();
//		} catch (UnsupportedAudioFileException e1) {
//			e1.printStackTrace();
//		} catch (IOException e1) {
//			e1.printStackTrace();
//		}

		soundQueue.add(new Sound(new AudioBuffer("resources/assets/audio/bloopBloop.wav"), 0, 0, 0, 0, 1.0f, 1.0f));
		
		synchronized(this) {
			setupComplete = true;
			notifyAll();
		}
		
		while(!exit) {
			while(!soundQueue.isEmpty()) {
				soundQueue.getFirst().play();
				soundQueue.removeFirst();
			}
			
			synchronized(this) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		long context = alcGetCurrentContext();
		long device = alcGetContextsDevice(context);
		
		alcMakeContextCurrent(0);
		alcDestroyContext(context);
		alcCloseDevice(device);
		ALC.destroy();
		
		synchronized(this) {
			finished = true;
			notifyAll();
		}
	}
	
	public void setListenerParameters(float x, float y, float velocityX, float velocityY) {
		alListener3f(AL_POSITION, x, y, 0);
		alListener3f(AL_VELOCITY, velocityX, velocityY, 0);
	}
	
	public void setVolume(float gain) {
		alListenerf(AL_GAIN, gain);
	}
	
	public void playSound(Sound sound) {
		soundQueue.add(sound);
		synchronized(this) {
			notifyAll();
		}
	}
	
	public void stopSound(Sound sound) {
		soundQueue.remove(sound);
		sound.stop();
	}
	
	public void exit() {
		exit = true;
		
		synchronized(this) {
			notifyAll();
		}
	}

	@Override
	public boolean isFinished() {
		return finished;
	}

	@Override
	public boolean isSetupComplete() {
		return setupComplete;
	}
	
}
