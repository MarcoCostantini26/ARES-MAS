import streamlit as st
import pandas as pd
import json
import matplotlib.pyplot as plt
import os
import re
import time

st.set_page_config(page_title="ARES-MAS Dashboard", layout="wide")

DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
SCENARIO_FILE = os.path.join(DATA_DIR, "scenario.json")
EVENTS_FILE = os.path.join(DATA_DIR, "events.jsonl")

@st.cache_data
def load_scenario():
    with open(SCENARIO_FILE, "r") as f:
        return json.load(f)

def load_events():
    events = []
    if os.path.exists(EVENTS_FILE):
        with open(EVENTS_FILE, "r") as f:
            for line in f:
                if line.strip():
                    events.append(json.loads(line))
    events.sort(key=lambda x: x.get("tick", 0))
    return events


scenario = load_scenario()
events = load_events()

if not events:
    st.warning("No events found. Run the Kotlin backend to generate the logs!")
    st.stop()

max_tick = max([e.get("tick", 0) for e in events])

if "tick_slider" not in st.session_state:
    st.session_state.tick_slider = 0
if "playing" not in st.session_state:
    st.session_state.playing = False

def toggle_play():
    st.session_state.playing = not st.session_state.playing

if st.session_state.playing:
    if st.session_state.tick_slider < max_tick:
        st.session_state.tick_slider += 1
    else:
        st.session_state.playing = False

col_title, col_play = st.columns([4, 1])
col_title.title("🪐 ARES-MAS")
col_play.write("")
col_play.button("Play / Pause", on_click=toggle_play)

selected_tick = st.slider("Time Machine", 0, max_tick, key="tick_slider")

current_events = [e for e in events if e.get("tick", 0) <= selected_tick]

rover_paths = {}
rover_positions = {}
for e in current_events:
    if e["type"] == "MOVE":
        rover = e["rover"]
        if rover not in rover_paths:
            rover_paths[rover] = {"x": [], "y": []}
        rover_paths[rover]["x"].append(e["to"][0])
        rover_paths[rover]["y"].append(e["to"][1])
        rover_positions[rover] = e["to"]

hazard_position = None
for e in current_events:
    if e["type"] == "HAZARD_MOVE":
        hazard_position = e["to"]

if hazard_position is None:
    for ent in scenario["entities"]:
        if ent["type"] == "HAZARD":
            hazard_position = ent["position"]
            break

col_map, col_explain = st.columns([1, 1.2])

with col_map:
    st.subheader("🗺️ Map")

    width = scenario["grid"]["width"]
    height = scenario["grid"]["height"]
    fig, ax = plt.subplots(figsize=(6, 6))
    ax.set_xlim(-0.5, width - 0.5)
    ax.set_ylim(-0.5, height - 0.5)
    ax.set_xticks(range(width))
    ax.set_yticks(range(height))
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.invert_yaxis()

    rover_batteries = {}
    for e in current_events:
        if e["type"] == "MOVE" and "battery" in e:
            rover_batteries[e["rover"]] = e["battery"]

    extracted_minerals = [tuple(e["at"]) for e in current_events if e["type"] == "EXTRACT"]

    for ent in scenario["entities"]:
        if ent["type"] == "MINERAL":
            x, y = ent["position"]
            if (x, y) not in extracted_minerals:
                ax.plot(x, y, "bD", markersize=12)

    if hazard_position:
        ax.plot(hazard_position[0], hazard_position[1], "rX", markersize=18)

    for rover, path in rover_paths.items():
        if path["x"]:
            ax.plot(path["x"], path["y"], color="gray", linestyle=":", linewidth=2, alpha=0.6)

    for rover, pos in rover_positions.items():
        if "scout" in rover:
            ax.plot(pos[0], pos[1], "mo", markersize=14)
            ax.text(pos[0], pos[1] - 0.35, rover, ha='center', fontsize=9, fontweight='bold', color='purple')
        else:
            # Harvester: Quadrato arancione
            ax.plot(pos[0], pos[1], marker="s", color="orange", markersize=14)
            bat_level = rover_batteries.get(rover, 100)
            
            label = f"{rover}\n(Bat: {bat_level}%)"
            color = "red" if bat_level < 20 else "black"
            ax.text(pos[0], pos[1] - 0.45, label, ha='center', fontsize=8, fontweight='bold', color=color)

    GRACE_WINDOW = 3 
    recent_violations = [e for e in current_events if e["type"] == "VIOLATION" and selected_tick - e["tick"] <= GRACE_WINDOW]
    
    for v in recent_violations:
        hx, hy = v["attempted_to"]
        action_type = v.get("action_type", "MOVE")
        
        if action_type == "EXTRACT":
            ax.plot(hx, hy, "yo", markersize=40, alpha=0.4)
            ax.text(hx, hy + 0.45, "LOW BATTERY!", color='darkorange', ha='center', fontweight='bold')
        else:
            ax.plot(hx, hy, "ro", markersize=40, alpha=0.3)
            ax.text(hx, hy + 0.45, "HAZARD BLOCK!", color='red', ha='center', fontweight='bold')

    # Base Station (Croce verde)
    ax.plot(0, 0, "gP", markersize=16, label="Base")
    
    st.pyplot(fig)
    plt.close(fig) 

with col_explain:
    st.subheader("📜 Mission Log")
    narrative = []
    for e in current_events:
        t = e['tick']
        if e["type"] == "CLAIM":
            if e.get("success"):
                narrative.append(f"**Tick {t}:** 🔍 BINGO! `{e['rover']}` physically discovered and locked the mineral at {e['at']}.")
            else:
                narrative.append(f"**Tick {t}:** 🚫 `{e['rover']}` found a mineral at {e['at']}, but it was already claimed.")
        elif e["type"] == "EXTRACT":
            narrative.append(f"**Tick {t}:** ⛏️ `{e['rover']}` successfully extracted the mineral at {e['at']}. Ready to return.")
        elif e["type"] == "NEGOTIATION":
            if e["msg_type"] == "cfp":
                narrative.append(f"**Tick {t}:** 📡 `{e['sender']}` broadcasted a Call For Proposal (CFP) to all harvesters.")
            elif e["msg_type"] == "accept":
                narrative.append(f"**Tick {t}:** 🏆 `{e['receiver']}` won the extraction contract and is moving to the target.")
        elif e["type"] == "VIOLATION":
            action_type = e.get("action_type", "MOVE")
            if action_type == "EXTRACT":
                narrative.append(f"**Tick {t}:** 🚨 GUARDRAIL: `{e['rover']}` extraction blocked at {e['attempted_to']}. Heavy drilling halted to prevent total power failure. Remaining battery reserved for return trip.")
            else:
                narrative.append(f"**Tick {t}:** 🚨 EMERGENCY: `{e['rover']}` almost entered a hazard zone at {e['attempted_to']}. tuProlog intervened, blocking the move.")
        elif e["type"] == "PLANNING":
            narrative.append(f"**Tick {t}:** 🧠 `{e['rover']}` delegated the emergency return route to STRIPS.")
        elif e["type"] == "HAZARD_MOVE":
            narrative.append(f"**Tick {t}:** 🌪️ Environmental Update: The sandstorm shifted to {e['to']}.")
        elif e["type"] == "MISSION_COMPLETE":
            has_extracted = any(ev["type"] == "EXTRACT" and ev["rover"] == e["rover"] for ev in current_events)
            
            if has_extracted:
                narrative.append(f"**Tick {t}:** 🏁 `{e['rover']}` safely returned to base with the payload. Mission accomplished!")
            else:
                narrative.append(f"**Tick {t}:** ⚠️ `{e['rover']}` safely returned to base EMPTY-HANDED. Mission aborted for safety!")
    if narrative:
        with st.container(height=300): 
            for line in narrative[::-1]:
                st.write(line)
    else:
        st.write("No relevant events recorded yet.")

    st.divider()

    st.markdown("### Contract Net Protocol")
    cnp_proposals = [e for e in current_events if e["type"] == "NEGOTIATION" and e["msg_type"] == "propose"]
    if cnp_proposals:
        costs = {}
        for p in cnp_proposals:
            match = re.findall(r'\d+', p["content"])
            if match:
                costs[p["sender"]] = int(match[-1])
        if costs:
            st.write("Offer comparison (estimated cost with Manhattan Distance):")
            st.bar_chart(pd.DataFrame.from_dict(costs, orient='index', columns=['Cost']), height=150)
    else:
        st.info("No offers recorded at the current tick.")

    st.markdown("### AI Safety Reasoning")
    violation_events = [e for e in current_events if e["type"] == "VIOLATION"]
    
    if violation_events:
        for v in reversed(violation_events):
            action = v.get("action_type", "MOVE")
            rover_name = v['rover']
            target = v['attempted_to']
            
            st.error(f"**Intervention at Tick {v['tick']}** | Agent: `{rover_name}` | Blocked Action: `{action}` at {target}")
            
            if action == "EXTRACT":
                reason = v.get('reason', 'Battery critical')
                st.markdown(f"""
                **Query: `safe_to_extract/3` ➔ DENIED**
                - `is_harvester` ➔ ✔️
                - `\+ hazard` ➔ ✔️
                - `battery > 20` ➔ ❌ (*{reason}*)
                
                **💡 Rationale:** Drilling aborted to prevent complete power failure. Remaining battery locked for STRIPS emergency return (✅ *Return guaranteed*).
                """)
            else:
                st.markdown(f"""
                **Query: `safe_to_move/2` ➔ DENIED**
                - `\+ hazard` ➔ ❌ (*Sandstorm at {target}*)
                
                **💡 Rationale:** Motors disabled to avoid fatal collision. Route recalculation required.
                """)
    else:
        st.success("All actions are within safe parameters. No interventions required.")

if st.session_state.playing:
    time.sleep(0.3)
    st.rerun()