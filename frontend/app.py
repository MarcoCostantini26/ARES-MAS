import streamlit as st
import json
import os

st.set_page_config(page_title="ARES-MAS Dashboard", layout="wide")
st.title("🪐 ARES-MAS: Mission Control")

current_dir = os.path.dirname(os.path.abspath(__file__))
log_file_path = os.path.abspath(os.path.join(current_dir, "..", "data", "events.jsonl"))

col1, col2 = st.columns([2, 1])

with col1:
    st.subheader("Grid Map (Mock)")
    grid_placeholder = st.empty() 

with col2:
    st.subheader("Live Event Log")
    log_placeholder = st.empty() 

@st.fragment(run_every=1)
def update_dashboard():
    if os.path.exists(log_file_path):
        with open(log_file_path, "r") as f:
            lines = f.readlines()
            
        if lines:
            all_events = [json.loads(line) for line in lines if line.strip()]
            if not all_events: return
            
            latest_tick = all_events[-1].get("tick", 0)
            recent_events = [ev for ev in all_events if ev.get("tick") == latest_tick]
            
            with grid_placeholder.container():
                st.info(f"**Tick corrente:** {latest_tick}")
                st.write("*(Rendering grafico 2D in arrivo nelle prossime fasi)*")
                for ev in recent_events:
                    entity = ev.get('rover') or ev.get('hazard')
                    icon = "🌪️" if ev.get('type') == "HAZARD_MOVE" else "🤖"
                    st.write(f"{icon} **{entity}** si trova in {ev.get('to')}")
                
            with log_placeholder.container():
                for ev in reversed(all_events[-6:]):
                    st.json(ev)
    else:
        grid_placeholder.warning(f"In attesa dell'avvio della simulazione... (File non trovato: {log_file_path})")

update_dashboard()