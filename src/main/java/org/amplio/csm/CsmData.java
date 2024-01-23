package org.amplio.csm;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.repeat;

@SuppressWarnings("unused")
public class CsmData {
    // Members of a CState that are lists of actions, lists of groups. all other members should
    // be event:newState.
    static final List<String> stateKeys = Arrays.asList("Actions", "CGroups");

    // Names of all of the events. The ordinal in the array is also the event id.
    public static List<String> eventNames = Arrays.stream(CsmEnums.tknEvent.values())
        .map(Enum::name)
        .collect(Collectors.toList());
    public static int eventNameMaxLen = eventNames.stream().map(String::length).max(Integer::compareTo).orElse(12);

    // Names of all of the actions. The ordinal in the array is also the action id.
    public static List<String> actionNames = Arrays.stream(CsmEnums.tknAction.values())
        .map(Enum::name)
        .collect(Collectors.toList());
    public static int actionNameMaxLen = actionNames.stream().map(String::length).max(Integer::compareTo).orElse(12);
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    final Map<String, Map<String, String>> CGroupsIn;
    final Map<String, Map<String, Object>> CStatesIn;
    final Map<String, Object> propsIn;

    List<String> groupNames;
    private final int groupNameMaxLen;
    List<String> stateNames;
    private final int stateNameMaxLen;

    // The compiled CGroups and CStates.
    final LinkedHashMap<String, CGroup> CGroups;
    final LinkedHashMap<String, CState> CStates;


    boolean ok = false;

    int actionsStringLen = 60; // semi-reasonable default value.

    public CsmData(Map csmdef) {
        this.CGroupsIn = (Map<String, Map<String, String>>) csmdef.get("CGroups");
        this.CStatesIn = (Map<String, Map<String, Object>>) csmdef.get("CStates");
        this.propsIn = (Map<String, Object>) csmdef.getOrDefault("Props", new HashMap<String,Object>());

        groupNames = new ArrayList<>(this.CGroupsIn.keySet());
        this.groupNameMaxLen = groupNames.stream().map(String::length).max(Integer::compareTo).orElse(12);
        stateNames = new ArrayList<>(this.CStatesIn.keySet());
        this.stateNameMaxLen = stateNames.stream().map(String::length).max(Integer::compareTo).orElse(12);

        this.CGroups = new LinkedHashMap<>();
        this.CStates = new LinkedHashMap<>();
    }

    public String toString() {
        this.actionsStringLen = CStates.values()
                                       .stream()
                                       .map(CState::actionsString)
                                       .map(String::length)
                                       .max(Integer::compareTo)
                                       .orElse(60);
        if (!ok) {
            return "{'Not a valid CSM'}";
        }
        StringBuilder result = new StringBuilder("{\n");
        if (propsIn.size() > 0) {
            result.append("  Props: ");
            result.append(propsIn.toString());
            result.append(",\n");
        }
        result.append("  CGroups: {\n");
        for (Map.Entry<String, CGroup> groupEntry : CGroups.entrySet()) {
            result.append(groupEntry.getValue().toString()).append(",\n");
        }
        result.append("  },\n  CStates: {\n");
        for (CState state : CStates.values()) {
            result.append(state.toString()).append(",\n");
        }
        return result.append("  }\n}").toString();
    }

    /**
     * Encapsulate an action and it's argument.
     */
    public static class CAction {
        public final String action;
        public final String args;

        public CAction(String action, String args) {
            this.action = action;
            this.args = StringUtils.isNotBlank(args) ? args : "";
        }

        public int index() {
            return actionNames.indexOf(action);
        }

        public String toString() {
            StringBuilder result = new StringBuilder(action);
            if (StringUtils.isNotEmpty(args)) {
                result.append('(')
                    .append(args)
                    .append(')');
            }
            return result.toString();
        }
    }

    /**
     * Encapsulate an event and the new state to which it transitions.
     */
    public class CEvent {
        public final String event;
        public final String newState;

        public CEvent(String event, String newState) {
            this.event = event;
            this.newState = newState;
        }

        public int eventIndex() {
            return eventNames.indexOf(event);
        }
        public int stateIndex() {
            return stateNames.indexOf(newState);
        }

        public String toString() {
            return event + StringUtils.repeat(' ', eventNameMaxLen - event.length()) + ": " + newState;
        }

        public String toString(boolean pad) {
            int p = pad ? eventNameMaxLen - event.length() : 0;
            return event + repeat(' ', p) + ": " + newState;
        }
    }

    /**
     * Encapsulate a CGroup's transitions.
     */
    public class CGroup {
        public String name;
        public List<CsmData.CEvent> eventMapping;

        public CGroup(String name) {
            this.name = name;
            this.eventMapping = new ArrayList<>();
        }

        public int emitSize() {
            // cgroup 0:
            //   offset of name
            //   num_event/new_state pairs
            //   event 0/new state 0
            //   ...
            return 2 + 2 + 2 * eventMapping.size();
        }

        public String toString() {
            int groupNamePad = groupNameMaxLen - name.length();
            StringBuilder result = new StringBuilder("   ").append(name).append(':')
                .append(repeat(' ', groupNamePad)).append('{');
            boolean first = true;
            for (CsmData.CEvent mapping : eventMapping) {
                result.append(first ? " " : "  ").append(mapping.toString()).append(",\n    ")
                    .append(repeat(' ', groupNameMaxLen));
                first = false;
            }
            result.append("}");
            return result.toString();
        }
    }

    /**
     * Encapsulate a state, with entry actions and transitions out.
     */
    public class CState {
        public String name;
        public List<CsmData.CAction> actions;
        public List<String> cGroups;
        public List<CsmData.CEvent> eventMapping;

        public CState(String name) {
            this.name = name;
            this.actions = new ArrayList<>();
            this.cGroups = new ArrayList<>();
            this.eventMapping = new ArrayList<>();
        }

        public int groupIndex(String groupName) {
            return groupNames.indexOf(groupName);
        }

        public int emitSize() {
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
            return 2 + 2 + 4 * actions.size() + 2 * cGroups.size() + 2 * eventMapping.size();
        }

        String actionsString() {
            if (!actions.isEmpty()) {
                StringBuilder result = new StringBuilder("Actions: [ ");
                result.append(actions.stream().map(CsmData.CAction::toString).collect(Collectors.joining(", ")));
                result.append(" ]");
                return result.toString();
            }
            return "";
        }

        public String toString() {
            StringBuilder result = new StringBuilder("    ").append(name).append(':')
                .append(repeat(' ', stateNameMaxLen - name.length())).append("{");
            if (!actions.isEmpty()) {
                String actionsString = actionsString();
                result.append(' ').append(actionsString).append(',')
                    .append(repeat(' ', actionsStringLen - actionsString.length()));
            } else {
                result.append(repeat(' ', actionsStringLen + 2));
            }
            if (!cGroups.isEmpty()) {
                result.append("CGroups: [ ").append(String.join(", ", cGroups)).append(" ], ");
            }
            if (!eventMapping.isEmpty()) {
                for (CsmData.CEvent mapping : eventMapping) {
                    result.append(mapping.toString(false)).append(", ");
                }
            }
            result.append("}");
            return result.toString();
        }
    }
}
