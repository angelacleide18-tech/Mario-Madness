package com.mariomadness.mobile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** JDK-only regression checks against Android's published tombstone wire schema. */
public class TombstoneSummaryTest {
    public static void main(String[] args) throws Exception {
        byte[] signal = join(string(2, "SIGSEGV"), string(4, "SEGV_MAPERR"), number(9, 123));
        byte[] frame = join(number(1, 0xabc), string(4, "hx::Boot"), string(6, "libApplicationMain.so"),
            number(7, 4096), string(8, "0123abcd"));
        byte[] crashed = join(string(2, "SDLThread"), message(4, frame));
        byte[] other = join(string(2, "unrelated-thread"), message(4, frame));
        // Deliberately put thread map entries before tid. Field 7 in a frame is
        // numeric file_map_offset; field 8 is the build ID, needed for symbolizing.
        byte[] tombstone = join(message(16, join(number(1, 99), message(2, other))),
            message(16, join(number(1, 42), message(2, crashed))), number(6, 42),
            string(9, "com.mariomadness.mobile:game"), message(10, signal),
            string(14, "test abort"), message(15, string(1, "test cause")), number(1000, 987));
        String report = TombstoneSummary.read(new ByteArrayInputStream(tombstone));
        for (String expected : new String[] {"SIGSEGV", "SEGV_MAPERR", "fault=0x7b", "Abort: test abort",
            "Cause: test cause", "SDLThread", "pc abc", "libApplicationMain.so", "buildId=0123abcd"}) {
            check(report.contains(expected), "Missing field: " + expected + " in " + report);
        }
        check(!report.contains("unrelated-thread"), "Selected the wrong thread");
        check(!report.contains("incomplete"), "Valid protobuf failed to parse");

        byte[] truncated = join(tombstone, new byte[] {(byte) 0x8a, 0x01, 20, 1});
        report = TombstoneSummary.read(new ByteArrayInputStream(truncated));
        check(report.contains("Abort: test abort") && report.contains("incomplete"), "Truncated tail lost useful evidence");
        check(TombstoneSummary.read(null).contains("No native trace"), "Null trace must be reported");
        report = TombstoneSummary.read(new ByteArrayInputStream(new byte[] {0}));
        check(report.contains("invalid field"), "Malformed input must be reported");
        report = TombstoneSummary.read(new ByteArrayInputStream(string(14, new String(new char[10000]).replace('\0', 'a'))));
        check(report.length() < 512, "Unbounded output from a long field");
        System.out.println("TombstoneSummary: 6 regression scenarios passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] string(int field, String text) {
        return message(field, text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] message(int field, byte[] body) {
        return join(varint((field << 3) | 2), varint(body.length), body);
    }

    private static byte[] number(int field, long value) {
        return join(varint(field << 3), varint(value));
    }

    private static byte[] varint(long value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while ((value & ~127L) != 0) {
            bytes.write((int) (value & 127) | 128);
            value >>>= 7;
        }
        bytes.write((int) value);
        return bytes.toByteArray();
    }

    private static byte[] join(byte[]... arrays) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] array : arrays) bytes.write(array, 0, array.length);
        return bytes.toByteArray();
    }
}
