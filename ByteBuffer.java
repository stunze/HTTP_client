import java.util.Arrays;

public class ByteBuffer {
	private byte[] buffer;
	private int size;
	private int elementPointer=0;
	
	public ByteBuffer(int size) {
		this.size = size;
		this.buffer=new byte[this.size];
	}
	
	public void addByte(byte c) {
		if(isFull()) throw new RuntimeException("ByteBufferOverflow");
		this.buffer[elementPointer++]=c;
	}
	
	public boolean isFull() {
		return this.elementPointer == this.size;
	}
	
	public void reset() {
		this.buffer = new byte[this.size];
		this.elementPointer=0;
	}
	
	public byte[] getBuffer() {
		return this.buffer;
	}
	
	public byte[] getTrimmedBuffer() {
		return Arrays.copyOf(getBuffer(), elementPointer);
	}
	
	public int getElementPointer() {
		return this.elementPointer;
	}
	
	public int getSize() {
		return this.size;
	}
}