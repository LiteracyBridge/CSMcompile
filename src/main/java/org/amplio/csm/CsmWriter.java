package org.amplio.csm;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CsmWriter{
    private final CsmData csmData;
    private final boolean verbose;

    private final CsmWriter.CsmOutputStream cos;
    private Map<String, Integer> predictedStateOffsets;
    private Map<String, Integer> predictedGroupOffsets;

    public CsmWriter(CsmData csmData,
        OutputStream cos,
        boolean verbose)
    {
        if (!csmData.isValid()) {
            throw new IllegalStateException("CsmData must be valid.");
        }
        this.csmData = csmData;
        this.cos = new CsmOutputStream(cos);
        this.verbose = verbose;
    }

    public void emit() throws IOException {
        // Layout of the csm data:
        // header
        // states
        // groups
        // stringpool
        emitLabel();
        emitHeader();
        emitCStates();
        emitCGroups();
        if (verbose) {
            System.out.printf("Actual offset for stringpool: %04x\n", cos.size());
        }
        cos.close();
    }

    private int sizeInBytes(CsmData.CGroup cGroup) {
        // cgroup 0:
        //   offset of name
        //   num_event/new_state pairs
        //   event 0/new state 0
        //   ...
        return 2 + 2 + 2 * cGroup.eventMapping.size();
    }
    private int sizeInBytes(CsmData.CState cState) {
        // cstate 0:
        //   offset of name
        //   num_event/new_state pairs | num_actions << 8
        //   action 0
        //   offset of arg 0
        //   ...
        //   action n
        //   offset of arg n
        //   event 0/new_state 0
        //   ...
        //   event j/new_state j
        //   ff / cgroup 0    last cgroup specified
        //   ...
        //   ff / cgroup n    first cgroup specified
        // ...
        return 2 + 2 + 4 * cState.actions.size() + 2 * cState.groups.size() + 2 * cState.eventMapping.size();

    }

    private void emitVersion(int major, int minor) throws IOException {
        cos.writeU8(major);
        cos.writeU8(minor);
    }

    private void emitLabel() {
        final DateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS'Z'",
            Locale.US); // Quoted "Z" to indicate UTC, no timezone offset
        final TimeZone UTC = TimeZone.getTimeZone("UTC");
        ISO8601.setTimeZone(UTC);
        String label = "Built @ " + ISO8601.format(new Date());
        if (csmData.propsIn.containsKey("Label")) {
            label += ": " + csmData.propsIn.get("Label");
        }
        cos.addString(label);
    }

    private void emitHeader() throws IOException {
        // signature (two bytes, to reveal any endian issues)
        // num_cstates | num_cgroups << 8
        // offset of cstate 0
        // ...
        // offset of cstate n
        // offset of cgroup 0
        // ...
        // offset of cgroup n
        // offset of string pool
        // cstate 0:
        // ...
        int offset = 0;

        if (verbose) {
            System.out.printf("version (%d) written at offset %04x\n",
                CsmMain.sVersion,
                cos.size());
        }
        cos.writeU16(CsmMain.sVersion);
        offset += 2;
        if (verbose) {
            System.out.printf("Start of %d CStates offsets will be written at offset %04x\n",
                csmData.CStates().size(),
                cos.size());
        }
        cos.writeU8(csmData.CStates().size());
        offset += 1 + 2*csmData.CStates().size();
        if (verbose) {
            System.out.printf("Start of %d CGroups offsets will be written at offset %04x\n",
                csmData.CGroups().size(),
                cos.size());
        }
        cos.writeU8(csmData.CGroups().size());
        offset += 1 + 2*csmData.CGroups().size();
        if (verbose) {
            System.out.printf("The string pool offset will be written at offset %04x\n", offset);
        }
        // The string pool offset will be written after the offsets, but before the CStates, so account for it.
        offset += 2;
        predictedStateOffsets = new HashMap<>();
        for (CsmData.CState state : csmData.CStates().values()) {
            predictedStateOffsets.put(state.name, offset);
            if (verbose) System.out.printf(
                "Predicted offset for state %s: %04x written at offset %04x\n",
                state.name,
                offset,
                cos.size());
            cos.writeU16(offset);
            offset += sizeInBytes(state);
        }
        predictedGroupOffsets = new HashMap<>();
        for (CsmData.CGroup group : csmData.CGroups().values()) {
            predictedGroupOffsets.put(group.name, offset);
            if (verbose) System.out.printf(
                "Predicted offset for group %s: %04x written at offset %04x\n",
                group.name,
                offset,
                cos.size());
            cos.writeU16(offset);
            offset += sizeInBytes(group);
        }
        // String pool offset.
        if (verbose) System.out.printf(
            "Predicted offset for string pool: %04x written at offset %04x\n",
            offset,
            cos.size());
        cos.writeU16(offset);
    }

    void emitCStates() throws IOException {
        // cstate 0:
        //   offset of name
        //   num_event/new_state pairs | num_actions << 8
        //   action id 0
        //   offset of arg 0
        //   ...
        //   action id n
        //   offset of arg n
        //   event id 0/new_state id 0
        //   ...
        //   event id j/new_state id j
        //   ff / cgroup 0    last cgroup specified
        //   ...
        //   ff / cgroup n    first cgroup specified
        // ...
        for (CsmData.CState state : csmData.CStates().values()) {
            if (predictedStateOffsets.get(state.name) != cos.size()) {
                System.out.printf(
                    "Internal error: CState '%s' predicted offset (%04x) != actual offset (%04x)\n",
                    state.name,
                    predictedStateOffsets.get(state.name),
                    cos.size());
            }
            if (verbose)
                System.out.printf("Actual offset for state %s: %04x\n", state.name, cos.size());
            // Offset of name, counts of transitions, count of actions
            cos.writeU16(cos.addString(state.name));
            cos.writeU8(state.groups.size() + state.eventMapping.size());
            cos.writeU8(state.actions.size());
            // Actions
            for (CsmData.CAction action : state.actions) {
                cos.writeU16(action.index());
                cos.writeU16(cos.addString(action.args));
            }
            // State transitions
            for (CsmData.CEvent event : state.eventMapping) {
                cos.writeU8(event.eventIndex());
                cos.writeU8(event.stateIndex());
            }
            // CGroups, in reverse order
            List<String> reversed = new ArrayList<>(state.groups);
            Collections.reverse(reversed);
            for (String groupName : reversed) {
                cos.writeU8(0xff);
                cos.writeU8(state.groupIndex(groupName));
            }
        }
    }

    void emitCGroups() throws IOException {
        // cgroup 0:
        //   offset of name
        //   num_event/new_state pairs
        //   event id 0/new state id 0
        //   ...
        for (CsmData.CGroup group : csmData.CGroups().values()) {
            if (predictedGroupOffsets.get(group.name) != cos.size()) {
                System.out.printf(
                    "Internal error: CGroup '%s' predicted offset (%04x) != actual offset (%04x)\n",
                    group.name,
                    predictedStateOffsets.get(group.name),
                    cos.size());
            }
            if (verbose)
                System.out.printf("Actual offset for group %s: %04x\n", group.name, cos.size());
            // Offset of name, counts of transitions, count of actions
            cos.writeU16(cos.addString(group.name));
            cos.writeU16(group.eventMapping.size());
            // State transitions
            for (CsmData.CEvent event : group.eventMapping) {
                cos.writeU8(event.eventIndex());
                cos.writeU8(event.stateIndex());
            }
        }
    }
//    }

    public static class CsmOutputStream implements Closeable{
        private boolean open = true;
        private final DataOutputStream dos;
        private final List<String> stringPool = new ArrayList<>();
        private short nextOffset = 0;
        private final Map<String, Short> internedStrings = new HashMap<>();

        public int size() {return dos.size();}

        /**
         * Creates a new data output stream to write data to the specified
         * underlying output stream. The counter <code>written</code> is
         * set to zero.
         *
         * @param out the underlying output stream, to be saved for later
         *            use.
         * @see FilterOutputStream
         */
        public CsmOutputStream(OutputStream out) {
            dos = new DataOutputStream(out);
        }

        public synchronized void writeU16(int v) throws IOException {
            // little end first.
            dos.write((v) & 0xFF);
            dos.write((v >>> 8) & 0xFF);
        }

        public synchronized void writeU8(int v) throws IOException {
            dos.write(v & 0xFF);
        }

        public synchronized short addString(String s) {
            if (internedStrings.containsKey(s)) {
                return internedStrings.get(s);
            }
            stringPool.add(s);
            short offset = nextOffset;
            internedStrings.put(s, offset);
            nextOffset += (short) (s.length() + 1);
            return offset;
        }

        @Override
        public void close() throws IOException {
            if (!open) return;
            open = false;
            for (String s : stringPool) {
                dos.writeBytes(s);
                dos.writeByte(0); // nul-terminate string
            }
            dos.close();
        }
    }
}
