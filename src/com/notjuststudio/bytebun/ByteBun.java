package com.notjuststudio.bytebun;

import com.sun.istack.internal.NotNull;

import java.lang.reflect.Array;
import java.util.Arrays;

public class ByteBun {

    private final static int BYTE_MASK = 0xff;

    private byte[] bytes;

    private int readerIndex = 0;
    private byte readerBitOffset = 0;

    private int writerIndex = 0;
    private byte writerBitOffset = 0;

    private ByteBun(@NotNull final int capacity) {
        bytes = new byte[capacity];
    }

    private ByteBun(@NotNull final byte[] bytes) {
        this.bytes = bytes;
    }

    public int capacity() {
        return bytes.length;
    }

    public ByteBun capacity(@NotNull final int capacity) {
        if (capacity != bytes.length) {
            final byte[] tmp = new byte[capacity];
            System.arraycopy(bytes, 0, tmp, 0, capacity > bytes.length ? bytes.length : capacity);
            bytes = tmp;
        }
        return this;
    }

    public int availableWrite() {
        return capacity() - writerIndex - (writerBitOffset != 0 ? 1 : 0);
    }

    public int availableRead() {
        int tmpIndex = writerIndex;
        byte tmpOffset = writerBitOffset;
        if (writerBitOffset < readerBitOffset) {
            tmpIndex--;
            tmpOffset += 8 - readerBitOffset;
        } else {
            tmpOffset -= readerBitOffset;
        }
        return tmpIndex - readerIndex - (tmpOffset != 0 ? 1 : 0);
    }

    private void checkWriter(@NotNull final int index, @NotNull final byte offset) {
        if (index + (offset + 7) / 8 > bytes.length)
            throw new IndexOutOfBoundsException("Writer index out of capacity: " + index + "." + offset + " > " + bytes.length);
    }

    private void checkReader(@NotNull final int index, @NotNull final byte offset) {
        if (index < writerIndex)
            return;
        if (index == writerIndex)
            if (offset <= writerBitOffset)
                return;
        throw new IndexOutOfBoundsException("Reader index higher than writer index: " + index + "." + offset + " > " + writerIndex + "." + writerBitOffset);
    }

    private static void checkArray(@NotNull final Object array, @NotNull final int pos, @NotNull final int length) {
        final int arrayLength = Array.getLength(array);
        if (pos + length > arrayLength)
            throw new IndexOutOfBoundsException("pos + length > array.length: " + pos + " + " + length + " > " + arrayLength);
    }

    private static void checkLength(@NotNull final int length) {
        if (length < 0)
            throw new IllegalArgumentException("Length must be more or equal than zero: " + length);
    }

    public ByteBun writerIndex(@NotNull final int index) {
        checkWriter(index, writerBitOffset);
        writerIndex = index;
        return this;
    }

    public int writerIndex() {
        return writerIndex;
    }

    public ByteBun writerBitOffset(@NotNull final byte offset) {
        checkWriter(writerIndex, offset);
        writerBitOffset = offset;
        return this;
    }

    public byte writerBitOffset() {
        return writerBitOffset;
    }

    public ByteBun readerIndex(@NotNull final int index) {
        checkReader(index, readerBitOffset);
        readerIndex = index;
        return this;
    }

    public int readerIndex() {
        return readerIndex;
    }

    public ByteBun readerBitOffset(@NotNull final byte offset) {
        checkReader(readerIndex, offset);
        readerBitOffset = offset;
        return this;
    }

    public byte readerBitOffset() {
        return readerBitOffset;
    }

    public ByteBun writeBoolean(@NotNull final boolean value) {
        final byte tmpOffset = (byte) ((writerBitOffset + 1) % 8);
        final int tmpIndex = writerIndex + (tmpOffset == 0 ? 1 : 0);

        checkWriter(tmpIndex, tmpOffset);

        final byte byteValue = (byte) (1 << (7 - writerBitOffset));
        bytes[writerIndex] &= ~byteValue;
        if (value)
            bytes[writerIndex] |= byteValue;

        writerIndex = tmpIndex;
        writerBitOffset = tmpOffset;

        return this;
    }

    public boolean readBoolean() {
        final byte tmpOffset = (byte) ((readerBitOffset + 1) % 8);
        final int tmpIndex = readerIndex + (tmpOffset == 0 ? 1 : 0);

        checkReader(tmpIndex, tmpOffset);

        final boolean value = (((bytes[readerIndex] & BYTE_MASK) >> (7 - readerBitOffset)) & 1) == 1;

        readerIndex = tmpIndex;
        readerBitOffset = tmpOffset;

        return value;
    }

    public ByteBun writeByte(@NotNull final byte value) {
        final int tmpIndex = writerIndex + 1;

        checkWriter(tmpIndex, writerBitOffset);

        if (writerBitOffset == 0) {
            bytes[writerIndex] = value;
        } else {
            final byte headMask = (byte)((1 << (8 - writerBitOffset)) - 1);
            final byte tailMask = (byte)(~headMask);

            bytes[writerIndex] &= tailMask;
            bytes[writerIndex] |= (value & BYTE_MASK) >> (writerBitOffset);

            bytes[tmpIndex] &= headMask;
            bytes[tmpIndex] |= value << (8 - writerBitOffset);
        }

        writerIndex = tmpIndex;

        return this;
    }

    public byte readByte() {
        final int tmpIndex = readerIndex + 1;

        checkReader(tmpIndex, readerBitOffset);

        byte value = 0;

        if (readerBitOffset == 0) {
            value = bytes[readerIndex];
        } else {
            value |= bytes[readerIndex] << (readerBitOffset);
            value |= (bytes[tmpIndex] & BYTE_MASK) >> (8 - readerBitOffset);
        }

        readerIndex = tmpIndex;

        return value;
    }

    public ByteBun writeShort(@NotNull final short value) {
        checkWriter(writerIndex + 2, writerBitOffset);

        if (writerBitOffset == 0) {
            bytes[writerIndex++] = (byte)(value >> 8);
            bytes[writerIndex++] = (byte)value;
        } else {
            final byte headMask = (byte)((1 << (8 - writerBitOffset)) - 1);
            final byte tailMask = (byte)(~headMask);

            bytes[writerIndex] &= tailMask;
            bytes[writerIndex++] |= (byte)(value >> (8 + writerBitOffset));

            bytes[writerIndex++] = (byte)(value >> (writerBitOffset));

            bytes[writerIndex] &= headMask;
            bytes[writerIndex] |= (byte)(value << (8 - writerBitOffset));
        }

        return this;
    }

    public short readShort() {
        checkReader(readerIndex + 2, readerBitOffset);

        short value = 0;

        if (readerBitOffset == 0) {
            value |= bytes[readerIndex++] << 8;
            value |= bytes[readerIndex++];
        } else {
            value |= bytes[readerIndex++] << (8 + readerBitOffset);
            value |= bytes[readerIndex++] << (readerBitOffset);
            value |= (bytes[readerIndex] & BYTE_MASK) >> (8 - readerBitOffset);
        }

        return value;
    }

    public ByteBun writeChar(@NotNull final char value) {
        return writeShort((short)value);
    }

    public char readChar() {
        return (char)readShort();
    }

    public ByteBun writeInt(@NotNull final int value) {
        checkWriter(writerIndex + 4, writerBitOffset);

        if (writerBitOffset == 0) {
            bytes[writerIndex++] = (byte)(value >> 24);
            bytes[writerIndex++] = (byte)(value >> 16);
            bytes[writerIndex++] = (byte)(value >> 8);
            bytes[writerIndex++] = (byte)value;
        } else {
            final byte headMask = (byte)((1 << (8 - writerBitOffset)) - 1);
            final byte tailMask = (byte)(~headMask);

            bytes[writerIndex] &= tailMask;
            bytes[writerIndex++] |= (byte)(value >> (24 + writerBitOffset));

            bytes[writerIndex++] = (byte)(value >> (16 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (8 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (writerBitOffset));

            bytes[writerIndex] &= headMask;
            bytes[writerIndex] |= (byte)(value << (8 - writerBitOffset));
        }

        return this;
    }

    public int readInt() {
        checkReader(readerIndex + 4, readerBitOffset);

        int value = 0;

        if (readerBitOffset == 0) {
            value |= bytes[readerIndex++] << 24;
            value |= bytes[readerIndex++] << 16;
            value |= bytes[readerIndex++] << 8;
            value |= bytes[readerIndex++];
        } else {
            value |= bytes[readerIndex++] << (24 + readerBitOffset);
            value |= bytes[readerIndex++] << (16 + readerBitOffset);
            value |= bytes[readerIndex++] << (8 + readerBitOffset);
            value |= bytes[readerIndex++] << (readerBitOffset);
            value |= (bytes[readerIndex] & BYTE_MASK) >> (8 - readerBitOffset);
        }

        return value;
    }

    public ByteBun writeFloat(@NotNull final float value) {
        return writeInt(Float.floatToRawIntBits(value));
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public ByteBun writeLong(@NotNull final long value) {
        checkWriter(writerIndex + 8, writerBitOffset);

        if (writerBitOffset == 0) {
            bytes[writerIndex++] = (byte)(value >> 56);
            bytes[writerIndex++] = (byte)(value >> 48);
            bytes[writerIndex++] = (byte)(value >> 40);
            bytes[writerIndex++] = (byte)(value >> 32);
            bytes[writerIndex++] = (byte)(value >> 24);
            bytes[writerIndex++] = (byte)(value >> 16);
            bytes[writerIndex++] = (byte)(value >> 8);
            bytes[writerIndex++] = (byte)value;
        } else {
            final byte headMask = (byte)((1 << (8 - writerBitOffset)) - 1);
            final byte tailMask = (byte)(~headMask);

            bytes[writerIndex] &= tailMask;
            bytes[writerIndex++] |= (byte)(value >> (56 + writerBitOffset));

            bytes[writerIndex++] = (byte)(value >> (48 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (40 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (32 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (24 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (16 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (8 + writerBitOffset));
            bytes[writerIndex++] = (byte)(value >> (writerBitOffset));

            bytes[writerIndex] &= headMask;
            bytes[writerIndex] |= (byte)(value << (8 - writerBitOffset));
        }

        return this;
    }

    public long readLong() {
        checkReader(readerIndex + 8, readerBitOffset);

        long value = 0;

        if (readerBitOffset == 0) {
            value |= bytes[readerIndex++] << 56;
            value |= bytes[readerIndex++] << 48;
            value |= bytes[readerIndex++] << 40;
            value |= bytes[readerIndex++] << 32;
            value |= bytes[readerIndex++] << 24;
            value |= bytes[readerIndex++] << 16;
            value |= bytes[readerIndex++] << 8;
            value |= bytes[readerIndex++];
        } else {
            value |= bytes[readerIndex++] << (56 + readerBitOffset);
            value |= bytes[readerIndex++] << (48 + readerBitOffset);
            value |= bytes[readerIndex++] << (40 + readerBitOffset);
            value |= bytes[readerIndex++] << (32 + readerBitOffset);
            value |= bytes[readerIndex++] << (24 + readerBitOffset);
            value |= bytes[readerIndex++] << (16 + readerBitOffset);
            value |= bytes[readerIndex++] << (8 + readerBitOffset);
            value |= bytes[readerIndex++] << (readerBitOffset);
            value |= (bytes[readerIndex] & BYTE_MASK) >> (8 - readerBitOffset);
        }

        return value;
    }

    public ByteBun writeDouble(@NotNull final double value) {
        return writeLong(Double.doubleToRawLongBits(value));
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public ByteBun writeBooleans(@NotNull final boolean[] value) {
        return writeBooleans(value, 0, value.length);
    }

    public ByteBun writeBooleans(@NotNull final boolean[] value, @NotNull final int pos, @NotNull final int length) {
        checkArray(value, pos, length);
        checkWriter(writerIndex + (writerBitOffset + length) / 8, (byte)((writerBitOffset + length) % 8));
        for (int i = 0; i < length; i++ ) {
            writeBoolean(value[pos + i]);
        }
        return this;
    }

    public boolean[] readBooleans(@NotNull final int length) {
        checkLength(length);
        final boolean[] target = new boolean[length];
        readBooleans(target);
        return target;
    }

    public ByteBun readBooleans(@NotNull final boolean[] target) {
        return readBooleans(target, 0, target.length);
    }

    public ByteBun readBooleans (@NotNull final boolean[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + (readerBitOffset + length) / 8, (byte)((readerBitOffset + length) % 8));
        for (int i = 0; i < length; i++) {
            target[i + pos] = readBoolean();
        }
        return this;
    }

    public ByteBun writeBytes(@NotNull final byte[] value) {
        return writeBytes(value, 0, value.length);
    }

    public ByteBun writeBytes(@NotNull final byte[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeByte(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeBytes(@NotNull final ByteBun buffer) {
        return writeBytes(buffer, buffer.availableRead());
    }

    public ByteBun writeBytes(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length, buffer.readerBitOffset);
        checkWriter(writerIndex + length, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeByte(buffer.readByte());
        }
        return this;
    }

    public byte[] readBytes(@NotNull final int length) {
        checkLength(length);
        final byte[] target = new byte[length];
        readBytes(target);
        return target;
    }

    public ByteBun readBytes(@NotNull final byte[] target) {
        return readBytes(target, 0, target.length);
    }

    public ByteBun readBytes(@NotNull final byte[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i + pos] = readByte();
        }
        return this;
    }

    public ByteBun readBytes(@NotNull final ByteBun buffer) {
        return readBytes(buffer, buffer.availableWrite());
    }

    public ByteBun readBytes(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length, buffer.writerBitOffset);
        checkReader(readerIndex + length, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeByte(readByte());
        }
        return this;
    }

    public ByteBun writeShorts(@NotNull final short[] value) {
        return writeShorts(value, 0, value.length);
    }

    public ByteBun writeShorts(@NotNull final short[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length * 2, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeShort(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeShorts(@NotNull final ByteBun buffer) {
        return writeShorts(buffer, buffer.availableRead() / 2);
    }

    public ByteBun writeShorts(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length * 2, buffer.readerBitOffset);
        checkWriter(writerIndex + length * 2, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeShort(buffer.readShort());
        }
        return this;
    }

    public short[] readShorts(@NotNull final int length) {
        checkLength(length);
        final short[] target = new short[length];
        readShorts(target);
        return target;
    }

    public ByteBun readShorts(@NotNull final short[] target) {
        return readShorts(target, 0, target.length);
    }

    public ByteBun readShorts(@NotNull final short[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length * 2, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i + pos] = readShort();
        }
        return this;
    }

    public ByteBun readShorts(@NotNull final ByteBun buffer) {
        return readShorts(buffer, buffer.availableWrite() / 2);
    }

    public ByteBun readShorts(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length * 2, buffer.writerBitOffset);
        checkReader(readerIndex + length * 2, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeShort(readShort());
        }
        return this;
    }

    public ByteBun writeChars(@NotNull final char[] value) {
        return writeChars(value, 0, value.length);
    }

    public ByteBun writeChars(@NotNull final char[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length * 2, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeChar(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeChars(@NotNull final ByteBun buffer) {
        return writeShorts(buffer, buffer.availableRead() / 2);
    }

    public ByteBun writeChars(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length * 2, buffer.readerBitOffset);
        checkWriter(writerIndex + length * 2, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeChar(buffer.readChar());
        }
        return this;
    }

    public char[] readChars(@NotNull final int length) {
        checkLength(length);
        final char[] target = new char[length];
        readChars(target);
        return target;
    }

    public ByteBun readChars(@NotNull final char[] target) {
        return readChars(target, 0, target.length);
    }

    public ByteBun readChars(@NotNull final char[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length * 2, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i + pos] = readChar();
        }
        return this;
    }

    public ByteBun readChars(@NotNull final ByteBun buffer) {
        return readShorts(buffer, buffer.availableWrite() / 2);
    }

    public ByteBun readChars(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length * 2, buffer.writerBitOffset);
        checkReader(readerIndex + length * 2, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeChar(readChar());
        }
        return this;
    }

    public ByteBun writeInts(@NotNull final int[] value) {
        return writeInts(value, 0, value.length);
    }

    public ByteBun writeInts(@NotNull final int[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length * 4, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeInt(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeInts(@NotNull final ByteBun buffer) {
        return writeInts(buffer, buffer.availableRead() / 4);
    }

    public ByteBun writeInts(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length * 4, buffer.readerBitOffset);
        checkWriter(writerIndex + length * 4, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeInt(buffer.readInt());
        }
        return this;
    }

    public int[] readInts(@NotNull final int length) {
        checkLength(length);
        final int[] target = new int[length];
        readInts(target);
        return target;
    }

    public ByteBun readInts(@NotNull final int[] target) {
        return readInts(target, 0, target.length);
    }

    public ByteBun readInts(@NotNull final int[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length * 4, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i] = readInt();
        }
        return this;
    }

    public ByteBun readInts(@NotNull final ByteBun buffer) {
        return readInts(buffer, buffer.availableWrite() / 4);
    }

    public ByteBun readInts(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length * 4, buffer.writerBitOffset);
        checkReader(readerIndex + length * 4, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeInt(readInt());
        }
        return this;
    }

    public ByteBun writeFloats(@NotNull final float[] value) {
        return writeFloats(value, 0, value.length);
    }

    public ByteBun writeFloats(@NotNull final float[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length * 4, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeFloat(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeFloats(@NotNull final ByteBun buffer) {
        return writeFloats(buffer, buffer.availableRead() / 4);
    }

    public ByteBun writeFloats(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length * 4, buffer.readerBitOffset);
        checkWriter(writerIndex + length * 4, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeFloat(buffer.readFloat());
        }
        return this;
    }

    public float[] readFloats(@NotNull final int length) {
        checkLength(length);
        final float[] target = new float[length];
        readFloats(target);
        return target;
    }

    public ByteBun readFloats(@NotNull final float[] target) {
        return readFloats(target, 0, target.length);
    }

    public ByteBun readFloats(@NotNull final float[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length * 4, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i] = readFloat();
        }
        return this;
    }

    public ByteBun readFloats(@NotNull final ByteBun buffer) {
        return readFloats(buffer, buffer.availableWrite() / 4);
    }

    public ByteBun readFloats(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length * 4, buffer.writerBitOffset);
        checkReader(readerIndex + length * 4, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeFloat(readFloat());
        }
        return this;
    }

    public ByteBun writeLongs(@NotNull final long[] value) {
        return writeLongs(value, 0, value.length);
    }

    public ByteBun writeLongs(@NotNull final long[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length * 8, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeLong(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeLongs(@NotNull final ByteBun buffer) {
        return writeFloats(buffer, buffer.availableRead() / 8);
    }

    public ByteBun writeLongs(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length * 8, buffer.readerBitOffset);
        checkWriter(writerIndex + length * 8, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeLong(buffer.readLong());
        }
        return this;
    }

    public long[] readLongs(@NotNull final int length) {
        checkLength(length);
        final long[] target = new long[length];
        readLongs(target);
        return target;
    }

    public ByteBun readLongs(@NotNull final long[] target) {
        return readLongs(target, 0, target.length);
    }

    public ByteBun readLongs(@NotNull final long[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length * 8, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i] = readLong();
        }
        return this;
    }

    public ByteBun readLongs(@NotNull final ByteBun buffer) {
        return readFloats(buffer, buffer.availableWrite() / 8);
    }

    public ByteBun readLongs(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length * 8, buffer.writerBitOffset);
        checkReader(readerIndex + length * 8, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeLong(readLong());
        }
        return this;
    }

    public ByteBun writeDoubles(@NotNull final double[] value) {
        return writeDoubles(value, 0, value.length);
    }

    public ByteBun writeDoubles(@NotNull final double[] value, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(value, pos, length);
        checkWriter(writerIndex + length * 8, writerBitOffset);
        for (int i = 0; i < length; i++ ) {
            writeDouble(value[pos + i]);
        }
        return this;
    }

    public ByteBun writeDoubles(@NotNull final ByteBun buffer) {
        return writeFloats(buffer, buffer.availableRead() / 8);
    }

    public ByteBun writeDoubles(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkReader(buffer.readerIndex + length * 8, buffer.readerBitOffset);
        checkWriter(writerIndex + length * 8, writerBitOffset);
        for (int i = 0; i < length; i++) {
            writeDouble(buffer.readDouble());
        }
        return this;
    }

    public double[] readDoubles(@NotNull final int length) {
        checkLength(length);
        final double[] target = new double[length];
        readDoubles(target);
        return target;
    }

    public ByteBun readDoubles(@NotNull final double[] target) {
        return readDoubles(target, 0, target.length);
    }

    public ByteBun readDoubles(@NotNull final double[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkReader(readerIndex + length * 8, readerBitOffset);
        for (int i = 0; i < length; i++) {
            target[i] = readDouble();
        }
        return this;
    }

    public ByteBun readDoubles(@NotNull final ByteBun buffer) {
        return readFloats(buffer, buffer.availableWrite() / 8);
    }

    public ByteBun readDoubles(@NotNull final ByteBun buffer, @NotNull final int length) {
        checkLength(length);
        buffer.checkWriter(buffer.writerIndex + length * 8, buffer.writerBitOffset);
        checkReader(readerIndex + length * 8, readerBitOffset);
        for (int i = 0; i < length; i++) {
            buffer.writeDouble(readDouble());
        }
        return this;
    }

    public byte getByte(@NotNull final int index) {
        return bytes[index];
    }

    public short getUnsignedByte(@NotNull final int index) {
        return (short)((bytes[index] + 256) % 256);
    }

    public ByteBun getBytes(@NotNull final int index, @NotNull final byte[] target) {
        return getBytes(index, target, 0, target.length);
    }

    public ByteBun getBytes(@NotNull final int index, @NotNull final byte[] target, @NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(target, pos, length);
        checkArray(bytes, index, length);
        for (int i = 0; i < length; i++) {
            target[pos + i] = bytes[index + i];
        }
        return this;
    }

    public ByteBun getBytes(@NotNull final int index, @NotNull final ByteBun buffer) {
        return getBytes(index, buffer, 0, buffer.availableWrite());
    }

    public ByteBun getBytes(@NotNull final int index, @NotNull final ByteBun buffer, @NotNull final int length) {
        return getBytes(index, buffer, 0, length);
    }

    public ByteBun getBytes(@NotNull final int index, @NotNull final ByteBun buffer, @NotNull final int pos, @NotNull final int length) {
        return getBytes(index, buffer.bytes, pos, length);
    }

    public ByteBun copy() {
        return copy(0, bytes.length);
    }

    public ByteBun copy(@NotNull final int pos, @NotNull final int length) {
        checkLength(length);
        checkArray(bytes, pos, length);
        final ByteBun bun = ByteBun.allocate(length);
        System.arraycopy(bytes, pos, bun.bytes, 0, length);
        return bun;
    }

    public ByteBun duplicate() {
        final ByteBun bun = ByteBun.allocate();
        bun.bytes = bytes;
        return bun;
    }

    public ByteBun clear() {
        writerIndex = 0;
        writerBitOffset = 0;
        readerIndex = 0;
        writerBitOffset = 0;
        Arrays.fill(bytes, (byte)0);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(obj instanceof ByteBun))
            return false;
        final ByteBun bun = (ByteBun)obj;
//        if (writerIndex != bun.writerIndex || writerBitOffset != bun.writerBitOffset)
//            return false;
//        if (readerIndex != bun.readerIndex || readerBitOffset != bun.readerBitOffset)
//            return false;
        if (bytes.length != bun.bytes.length)
            return false;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != bun.bytes[i])
                return false;
        }
        return true;
    }

    private Integer hashCode = null;

    @Override
    public int hashCode() {
        if (hashCode != null)
            return hashCode;
        int hash = 0;
        for (int i = 0; i < bytes.length - 4; i += 4) {
            hash ^= (bytes[i] << 24) | (bytes[i + 1] << 16) | (bytes[i + 2] << 8) | (bytes[i + 3]);
        }
        final int mod = bytes.length % 4;
        final int tail = (mod == 0 ? 4 : mod);
        if (tail >= 1) {
            hash ^= (bytes[bytes.length - mod] << 24);
        }
        if (tail >= 2) {
            hash ^= (bytes[bytes.length - mod + 1] << 16);
        }
        if (tail >= 3) {
            hash ^= (bytes[bytes.length - mod + 2] << 8);
        }
        if (tail == 4) {
            hash ^= (bytes[bytes.length - mod + 3]);
        }
        hashCode = hash;
        return hashCode;
    }

    public static ByteBun allocate() {
        return allocate(0);
    }

    public static ByteBun allocate(@NotNull final int capacity) {
        return new ByteBun(capacity);
    }

}
