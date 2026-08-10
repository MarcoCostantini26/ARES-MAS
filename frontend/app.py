import streamlit as st
import pandas as pd
import json
import matplotlib.pyplot as plt
import os

st.set_page_config(page_title="ARES-MAS Dashboard", layout="wide")

col_title, col_btn = st.columns([4, 1])
with col_title:
    st.title("🪐 ARES-MAS: Explainability Dashboard")
with col_btn:
    st.write("") # Spaziatura
    if st.button("Live (Polling)"):
        st.rerun()

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
    st.warning("No event found...")
    st.stop()

max_tick = max([e.get("tick", 0) for e in events])
selected_tick = st.slider("Time machine", 0, max_tick, max_tick)

current_events = [e for e in events if e.get("tick", 0) <= selected_tick]

rover_positions = {}
for e in current_events:
    if e["type"] == "MOVE":
        rover_positions[e["rover"]] = e["to"]

col_map, col_explain = st.columns([1, 1.2])

with col_map:
    st.subheader("Sensorial map")
    
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
        x, y = ent["position"]
        if ent["type"] == "MINERAL":
            ax.plot(x, y, "bD", markersize=12, label="Minerale")
        elif ent["type"] == "HAZARD":
            ax.plot(x, y, "rX", markersize=14, label="Tempesta (Hazard)")

    for rover, pos in rover_positions.items():
        color = "gold" if "scout" in rover else "orange"
        marker = "o" if "scout" in rover else "s"
        ax.plot(pos[0], pos[1], marker=marker, color=color, markersize=15, markeredgecolor="black")
        ax.text(pos[0], pos[1]-0.3, rover, ha='center', fontsize=9, fontweight='bold')

    ax.plot(0, 0, "gP", markersize=16, label="Base")
    
    plt.legend(loc='upper right', bbox_to_anchor=(1.3, 1))
    st.pyplot(fig)


with col_explain:
    st.subheader("Logic of system (Explainability)")
    
    st.markdown("### Negotiation state (Contract Net)")
    cnp_events = [e for e in current_events if e["type"] == "NEGOTIATION"]
    if cnp_events:
        df_cnp = pd.DataFrame(cnp_events)[["tick", "sender", "msg_type", "content", "receiver"]]
        st.dataframe(df_cnp.tail(6), use_container_width=True)
    else:
        st.info("No negotiation.")

    st.markdown("### tuProlog (Security)")
    violation_events = [e for e in current_events if e["type"] == "VIOLATION"]
    if violation_events:
        for v in violation_events:
            st.error(f"**Tick {v['tick']}**: Agent `{v['rover']}` try to move in {v['attempted_to']}. **Action blocked by tuProlog** (Cause: {v['reason']}).")
    else:
        st.success("All moves are safe.")

    st.markdown("### Automated Planning (STRIPS)")
    plan_events = [e for e in current_events if e["type"] == "PLANNING"]
    if plan_events:
        for p in plan_events:
            st.info(f"**Tick {p['tick']}**: `{p['rover']}` he delegated the return. Plan calculated: `{p['plan']}`")
    else:
        st.write("No STRIPS plan generated")