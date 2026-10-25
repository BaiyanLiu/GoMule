package gomule.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class D2BitReaderTest {

    @Test
    public void getNextByteBoundaryInBits() {
        D2BitReader d2BitReader = new D2BitReader(new byte[]{1, 2, 3});
        assertEquals(0, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(1);
        assertEquals(8, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(1);
        assertEquals(8, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(6);
        assertEquals(8, d2BitReader.getNextByteBoundaryInBits());
        d2BitReader.read(1);
        assertEquals(16, d2BitReader.getNextByteBoundaryInBits());
    }

    @Test
    public void write32BitsOverExistingData() {
        D2BitReader d2BitReader = new D2BitReader(new byte[]{0x44, 0, 0, 0, 0x7F});
        d2BitReader.write(0x62, 32);
        d2BitReader.set_byte_pos(0);
        assertEquals(0x62, d2BitReader.read(32));
        assertEquals(0x7F, d2BitReader.read(8));
    }

    @Test
    public void write24BitsOverExistingData() {
        D2BitReader d2BitReader = new D2BitReader(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x11});
        d2BitReader.write(2_500_000, 24);
        d2BitReader.set_byte_pos(0);
        assertEquals(2_500_000, d2BitReader.read(24));
        assertEquals(0x11, d2BitReader.read(8));
    }
}
