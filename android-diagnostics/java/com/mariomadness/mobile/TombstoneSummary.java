package com.mariomadness.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads the useful fields of Android's native-crash protobuf, not arbitrary text.
 * Schema: https://android.googlesource.com/platform/system/core/+/main/debuggerd/proto/tombstone.proto
 * Unknown fields are skipped. Input, text and backtrace lengths are bounded.
 */
final class TombstoneSummary {
    static String read(InputStream input) throws IOException {
        if (input == null) return "No native trace retained by Android.\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int limit = 2 * 1024 * 1024;
        while (output.size() < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
            if (count < 0) break;
            output.write(buffer, 0, count);
        }
        StringBuilder text = new StringBuilder();
        long crashedTid = 0;
        Reader root = new Reader(output.toByteArray());
        try {
            // Protobuf fields may arrive in any order, including threads before tid.
            Reader header = new Reader(root.data);
            try {
                while (header.next()) if (header.field == 6) crashedTid = header.number;
            } catch (IOException incomplete) {
                // The second pass still returns fields before the truncated tail.
            }
            while (root.next()) {
                switch (root.field) {
                    case 4: text.append("Timestamp: ").append(root.string()).append('\n'); break;
                    case 6: crashedTid = root.number; break;
                    case 9: text.append("Process: ").append(root.string()).append('\n'); break;
                    case 10: describeSignal(root.message(), text); break;
                    case 14: text.append("Abort: ").append(root.string()).append('\n'); break;
                    case 15:
                        Reader cause = root.message();
                        while (cause.next()) if (cause.field == 1) text.append("Cause: ").append(cause.string()).append('\n');
                        break;
                    case 16:
                        Reader entry = root.message();
                        long key = 0;
                        Reader thread = null;
                        while (entry.next()) {
                            if (entry.field == 1) key = entry.number;
                            if (entry.field == 2) thread = entry.message();
                        }
                        if (key == crashedTid && thread != null) describeThread(thread, text);
                        break;
                }
            }
        } catch (IOException error) {
            text.append("[native trace incomplete or unsupported: ").append(error.getMessage()).append("]\n");
        }
        if (text.length() == 0) text.append("No recognized fields in native trace.\n");
        if (output.size() == limit) text.append("[native trace input limited to 2 MiB]\n");
        return text.toString();
    }

    private static void describeSignal(Reader signal, StringBuilder text) throws IOException {
        text.append("Signal: ");
        while (signal.next()) {
            if (signal.field == 2 || signal.field == 4) text.append(signal.string()).append(' ');
            if (signal.field == 9) text.append("fault=0x").append(Long.toHexString(signal.number));
        }
        text.append('\n');
    }

    private static void describeThread(Reader thread, StringBuilder text) throws IOException {
        int frames = 0;
        while (thread.next()) {
            if (thread.field == 2) text.append("Crashed thread: ").append(thread.string()).append('\n');
            if (thread.field == 4 && frames++ < 12) {
                Reader frame = thread.message();
                long relativePc = 0;
                String function = "", file = "", buildId = "";
                while (frame.next()) {
                    if (frame.field == 1) relativePc = frame.number;
                    if (frame.field == 4) function = frame.string();
                    if (frame.field == 6) file = frame.string();
                    if (frame.field == 8) buildId = frame.string();
                }
                text.append('#').append(frames - 1).append(" pc ").append(Long.toHexString(relativePc))
                    .append(' ').append(file).append(' ').append(function).append(" buildId=").append(buildId).append('\n');
            }
        }
    }

    private static final class Reader {
        final byte[] data;
        final int end;
        int position, field, wire, start, length;
        long number;

        Reader(byte[] data) { this(data, 0, data.length); }
        Reader(byte[] data, int start, int end) { this.data = data; this.position = start; this.end = end; }

        boolean next() throws IOException {
            if (position == end) return false;
            long tag = varint();
            field = (int) (tag >>> 3);
            wire = (int) (tag & 7);
            if (field == 0) throw new IOException("invalid field");
            number = 0;
            length = 0;
            if (wire == 0) number = varint();
            else if (wire == 1) skip(8);
            else if (wire == 5) skip(4);
            else if (wire == 2) {
                long size = varint();
                if (size < 0 || size > end - position) throw new IOException("truncated field");
                start = position;
                length = (int) size;
                skip(length);
            } else throw new IOException("unsupported wire type");
            return true;
        }

        long varint() throws IOException {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (position >= end) throw new IOException("truncated varint");
                int next = data[position++] & 255;
                value |= (long) (next & 127) << shift;
                if ((next & 128) == 0) return value;
            }
            throw new IOException("invalid varint");
        }

        void skip(int count) throws IOException {
            if (count > end - position) throw new IOException("truncated field");
            position += count;
        }

        String string() throws IOException {
            if (wire != 2) throw new IOException("expected string");
            return new String(data, start, Math.min(length, 256), StandardCharsets.UTF_8);
        }

        Reader message() throws IOException {
            if (wire != 2) throw new IOException("expected message");
            return new Reader(data, start, start + length);
        }
    }
}
