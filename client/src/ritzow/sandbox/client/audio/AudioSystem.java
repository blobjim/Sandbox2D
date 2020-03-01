package ritzow.sandbox.client.audio;

import java.util.List;

import static java.util.Map.entry;
import static ritzow.sandbox.client.data.StandardClientProperties.AUDIO_PATH;
import static ritzow.sandbox.client.util.ClientUtility.log;

public interface AudioSystem {
	default void playSound(int sound, float x, float y, float velocityX, float velocityY) {
		playSound(sound, x, y, velocityX, velocityY, 1.0f, 1.0f);
	}

	void playSound(int sound, float x, float y, float velocityX, float velocityY, float gain, float pitch);
	void playSoundGlobal(int sound, float gain, float pitch);
	void registerSound(int id, AudioData data);
	void setVolume(float gain);
	void setPosition(float x, float y);
	void close();

	public static AudioSystem getDefault() {
		return Default.DEFAULT;
	}

	static final class Default {
		private static final AudioSystem DEFAULT = loadDefault();

		private static AudioSystem loadDefault() {
			try {
				log().info("Loading audio system");
				OpenALAudioSystem.initialize();
				AudioSystem audio = OpenALAudioSystem.create();
				audio.setVolume(1.0f);

				var sounds = List.of(
					entry(Sound.BLOCK_BREAK, "dig.wav"),
					entry(Sound.BLOCK_PLACE, "place.wav"),
					entry(Sound.POP, "pop.wav"),
					entry(Sound.THROW, "throw.wav"),
					entry(Sound.SNAP, "snap.wav")
				);

				for(var entry : sounds) {
					audio.registerSound(entry.getKey().code(),
							WAVEDecoder.decode(AUDIO_PATH.resolve(entry.getValue())));
				}
				return audio;
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
