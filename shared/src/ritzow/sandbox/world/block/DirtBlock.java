package ritzow.sandbox.world.block;

import ritzow.sandbox.data.ByteUtil;
import ritzow.sandbox.data.DataReader;
import ritzow.sandbox.data.Serializer;

public class DirtBlock extends Block {
	
	public DirtBlock() {
		
	}
	
	public DirtBlock(DataReader data) {
		
	}

	@Override
	public int getHardness() {
		return 5;
	}

	@Override
	public float getFriction() {
		return 0.05f;
	}

	@Override
	public String getName() {
		return "dirt";
	}

	@Override
	public boolean isSolid() {
		return true;
	}

	@Override
	public byte[] getBytes(Serializer ser) {
		return ByteUtil.EMPTY_BYTE_ARRAY;
	}
}