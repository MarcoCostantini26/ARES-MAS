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
col_title.title("🪐 ARES-MAS: Explainability Dashboard")
col_play.write("")
col_play.button("⏯️ Play / Pause", on_click=toggle_play)

selected_tick = st.slider("⏱️ Time Machine", 0, max_tick, key="tick_slider")

current_events = [e for e in events if e.get("tick", 0) <= selected_tick]
current_tick_events = [e for e in events if e.get("tick", 0) == selected_tick]

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
    st.subheader("🗺️ Sensor Map")

    width = scenario["grid"]["width"]
    height = scenario["grid"]["height"]
    fig, ax = plt.subplots(figsize=(6, 6))
    ax.set_xlim(-0.5, width - 0.5)
    ax.set_ylim(-0.5, height - 0.5)
    ax.set_xticks(range(width))
    ax.set_yticks(range(height))
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.invert_yaxis()

    for ent in scenario["entities"]:
        if ent["type"] == "MINERAL":
            x, y = ent["position"]
            ax.plot(x, y, "bD", markersize=12)

    if hazard_position:
        ax.plot(hazard_position[0], hazard_position[1], "rX", markersize=14)

    for rover, path in rover_paths.items():
        if path["x"]:
            ax.plot(path["x"], path["y"], color="gray", linestyle=":", linewidth=2, alpha=0.6)

    for rover, pos in rover_positions.items():
        color = "gold" if "scout" in rover else "orange"
        marker = "o" if "scout" in rover else "s"
        ax.plot(pos[0], pos[1], marker=marker, color=color, markersize=15, markeredgecolor="black")
        ax.text(pos[0], pos[1] - 0.3, rover, ha='center', fontsize=9, fontweight='bold')

    for e in current_tick_events:
        if e["type"] == "VIOLATION":
            hx, hy = e["attempted_to"]
            ax.plot(hx, hy, "ro", markersize=30, alpha=0.3)
            ax.text(hx, hy + 0.4, "BLOCKED!", color='red', ha='center', fontweight='bold')

    ax.plot(0, 0, "gP", markersize=16, label="Base")
    st.pyplot(fig)


with col_explain:

    st.subheader("📜 Mission Log")
    narrative = []
    for e in current_events:
        t = e['tick']
        if e["type"] == "NEGOTIATION":
            if e["msg_type"] == "cfp":
                narrative.append(f"**Tick {t}:** 📡 `{e['sender']}` launched a CNP auction.")
            elif e["msg_type"] == "accept":
                narrative.append(f"**Tick {t}:** 🏆 `{e['receiver']}` won the extraction contract.")
        elif e["type"] == "VIOLATION":
            narrative.append(f"**Tick {t}:** 🚨 `{e['rover']}` attempted a dangerous move to {e['attempted_to']}. Prolog guardrail intervened.")
        elif e["type"] == "PLANNING":
            narrative.append(f"**Tick {t}:** 🧠 `{e['rover']}` delegated the return route to STRIPS.")
        elif e["type"] == "HAZARD_MOVE":
            narrative.append(f"**Tick {t}:** 🌪️ The sandstorm moved to {e['to']}.")
        elif e["type"] == "MISSION_COMPLETE":
            narrative.append(f"**Tick {t}:** 🏁 `{e['rover']}` completed the mission. Sandstorm patrol halted.")

    if narrative:
        with st.container(height=150):
            for line in narrative[::-1]:
                st.write(line)
    else:
        st.write("No relevant events recorded yet.")

    if hazard_position:
        st.caption(f"🌪️ Current sandstorm position: {hazard_position}")

    st.divider()

    st.markdown("### 🤝 Contract Net Protocol Auction")
    cnp_proposals = [e for e in current_events if e["type"] == "NEGOTIATION" and e["msg_type"] == "propose"]
    if cnp_proposals:
        costs = {}
        for p in cnp_proposals:
            match = re.findall(r'\d+', p["content"])
            if match:
                costs[p["sender"]] = int(match[-1])

        if costs:
            st.write("Offer comparison (estimated cost):")
            st.bar_chart(pd.DataFrame.from_dict(costs, orient='index', columns=['Cost']), height=150)
    else:
        st.info("No offers recorded at the current tick.")

    st.markdown("### 🛡️ Ethical Guardrail (tuProlog)")
    violation_events = [e for e in current_events if e["type"] == "VIOLATION"]
    if violation_events:
        v = violation_events[-1]
        st.error(f"**Violation blocked at Tick {v['tick']}** for agent `{v['rover']}`")
        st.markdown("**Partial Proof-Tree (failed logical derivation):**")
        st.code(f"""?- safe_to_move({v['attempted_to'][0]}, {v['attempted_to'][1]}).
-> Rule: {v.get('rule_evaluated', 'N/A')}
-> Blocking fact: {v.get('hazard_matched', 'N/A')}
-> Result: FALSE (action denied)""", language="prolog")
    else:
        st.success("All movements are ethically safe.")

if st.session_state.playing:
    time.sleep(0.3)
    st.rerun()
    
