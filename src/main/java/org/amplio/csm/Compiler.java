package org.amplio.csm;

import org.amplio.csm.CsmData.CGroup;
import org.amplio.csm.CsmData.CState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.amplio.csm.CsmData.eventNames;

/**
 * Compiles parsed YAML into a CsmData object.  The general form of the source is like this:
 * <pre>
 * {
 *   CGroups: {
 *     whenSleeping:  { House:      stWakeup },
 *     whenPlayPaused:{ Bowl:       stPlayResume },
 *   },
 *   CStates: {
 *     stOnPrevMsg:    { Actions: [ msgAdj(-1),  playSubject(msg) ],
 *                       CGroups:[ whenAwake, whenPlaying, whenNav, whenNavMsgs ],
 *                       AudioStart: stPlaying },
 *     stOnNextMsg:    { Actions: [ msgAdj(+1),  playSubject(msg) ],
 *                       CGroups:[ whenAwake, whenPlaying, whenNav, whenNavMsgs ],
 *                       AudioStart: stPlaying },
 *     . . .
 *   }
 * }
 * </pre>
 * CGroups consist of event:newState pairs.
 * CStates are an optional sequence of action(args), an optional list of CGroups,
 * and zero or more event:newState messages. Actions are performed when the state
 * is entered. Groups describe common event:newState sets. Later CGroups override
 * earlier CGroups, and the state-specific pairs override CGroups.
 * <p>
 * Actions and Events are defined by the TBv2 firmware, and are imported into this
 * application by pre-processing the CsmEnums.h file into CsmEnums.java.
 */
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class Compiler {
    // Match "name" or "name(arg)"
    static final Pattern ACTION_ARG = Pattern.compile("(\\w*)(?:\\((.*)\\))?");
    private final CsmData csmData;

    final Map<String, Map<String, String>> cGroupsIn;
    final Map<String, Map<String, Object>> cStatesIn;
    final Map<String, Object> propsIn = new HashMap<>();

    final List<String> errors = new ArrayList<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Compiler(Map rawData) {
        csmData = new CsmData();
        cGroupsIn = (Map<String, Map<String, String>>) rawData.get("CGroups");
        cStatesIn = (Map<String, Map<String, Object>>) rawData.get("CStates");
        propsIn.putAll((Map<String,Object>) rawData.getOrDefault("Props", new HashMap<String,Object>()));
        csmData.addProps(propsIn);
    }

    /**
     * Compile the data.
     * @return the parsed CsmData.
     */
    public CsmData go() {
        boolean ok = compileCGroups();
        ok = compileCStates() && ok;
        csmData.ok = ok;
        return ok ? csmData : null;
    }

    /**
     * So that the caller can get the csmData even if there are compile errors.
     * @return the csmData.
     */
    public CsmData getCsmData() {
        return csmData;
    }

    /**
     * Retrieve compile errors, if any.
     * @param list to be filled with the errors.
     */
    public void fillErrors(List<String> list) {
        list.addAll(errors);
    }

    /**
     * Iterates the states defined in the 'CStates' input object, and compiles each one.
     */
    boolean compileCStates() {
        boolean stateOk = true;
        for (Map.Entry<String, Map<String, Object>> stateEntry : cStatesIn.entrySet()) {
            String stateName = stateEntry.getKey();
            Map<String, Object> state = stateEntry.getValue();
            CState newState = compileCState(stateName, state);
            stateOk = stateOk && newState != null;
            if (stateOk) {
                csmData.putCState(newState);
            }
        }
        return stateOk;
    }

    /**
     * Compile one CState.
     * @param stateName name of the state.
     * @param state parsed yaml object describing the state.
     * @return the CState if it is parsed successfully, null otherwise.
     */
    CState compileCState(String stateName, Map<String, Object> state) {
        CState result = csmData.new CState(stateName);
        List<String> stateErrors = new ArrayList<>();
        // There are (or may be) 'Actions', 'CGroups', and Event:newState pairs. Query the Actions and CGroups.
        //noinspection unchecked
        List<String> actions = (List<String>) state.getOrDefault("Actions", new ArrayList<>());
        //noinspection unchecked
        List<String> cGroups = (List<String>) state.getOrDefault("CGroups", new ArrayList<>());
        // Everything else is an Event:newState
        List<String> events = state.keySet()
            .stream()
            .filter(n -> !CsmData.cStateNonEventKeys.contains(n))
            .collect(Collectors.toList());

        // Validate and accumulate actions.
        for (String action : actions) {
            // Validate well-formed action, get optional args.
            Matcher m = ACTION_ARG.matcher(action);
            if (m.matches()) {
                String actionName = m.group(1);
                String actionArg = m.groupCount() > 1 ? m.group(2) : "";
                // Known action?
                if (CsmData.actionNames.contains(actionName)) {
                    if (actionName.equals("enterState")) {
                        if (!cStatesIn.containsKey(actionArg)) {
                            stateErrors.add("Not a valid state for 'enterState': " + actionArg);
                            continue;
                        }
                    } else if (actionName.equals("exitScript")) {
                        if (!CsmData.eventNames.contains(actionArg)) {
                            stateErrors.add("Not a valid event name for 'exitScript': " + actionArg);
                            continue;
                        }
                    }
                    result.actions.add(csmData.new CAction(actionName, actionArg));
                } else {
                    stateErrors.add("Unknown action '" + action + "'");
                }
            } else {
                stateErrors.add("Misformed action '" + action + "'");
            }
        }
        // Validate and accumulate CGroup (names).
        for (String groupName : cGroups) {
            if (cGroupsIn.containsKey(groupName)) {
                result.groups.add(groupName);
            } else {
                stateErrors.add("Unknown group '" + groupName + "'");
            }
        }
        // Validate and accumulate event:state pairs.
        for (String event : events) {
            String newStateName = (String) state.get(event);
            if (event == null || newStateName == null | !eventNames.contains(event)) {
                stateErrors.add("Unrecognizable event or state: '" + event + ":" + newStateName + "'");
            } else {
                if (!events.contains(event)) stateErrors.add("Unknown event '" + event + "'");
                if (!cStatesIn.containsKey(newStateName)) stateErrors.add("Unknown state '" + newStateName + "'");
                result.eventMapping.add(csmData.new CEvent(event, newStateName));
            }
        }
        if (!stateErrors.isEmpty()) {
            errors.add(String.format("Error(s) parsing CState %s: \n    %s\n", stateName, String.join("\n    ", stateErrors)));
            return null;
        }
        return result;
    }

    boolean compileCGroups() {
        boolean groupOk = true;
        for (Map.Entry<String, Map<String, String>> groupEntry : cGroupsIn.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, String> group = groupEntry.getValue();
            CGroup newGroup = compileCGroup(groupName, group);
            groupOk = groupOk && newGroup != null;
            if (groupOk) {
                csmData.putCGroup(newGroup);
            }
        }
        return groupOk;
    }

    /**
     * Compile one CGroup.
     * @param groupName name of the CGroup.
     * @param group parsed yaml object describing the group.
     * @return the CGroup if it is parsed successfully, null otherwise.
     */
    CGroup compileCGroup(String groupName, Map<String, String> group) {
        CGroup result = csmData.new CGroup(groupName);
        List<String> groupErrors = new ArrayList<>();
        // Validate all the event:newState pairs.
        for (Map.Entry<String, String> eventMapping : group.entrySet()) {
            String eventName = eventMapping.getKey();
            String newStateName = eventMapping.getValue();
            if (eventName == null || newStateName == null) {
                groupErrors.add("Unrecognizable event or state: '" + eventName + ":" + newStateName + "'");
            } else {
                if (!eventNames.contains(eventName)) groupErrors.add("Unknown event '" + eventName + "'");
                if (!cStatesIn.containsKey(newStateName)) groupErrors.add("Unknown state '" + newStateName + "'");
            }
            if (groupErrors.isEmpty()) {
                result.eventMapping.add(csmData.new CEvent(eventName, newStateName));
            }
        }
        if (!groupErrors.isEmpty()) {
            errors.add(String.format("Error(s) parsing CGroup %s: \n    %s\n", groupName, String.join("\n    ", groupErrors)));
            return null;
        }
        return result;
    }


}
