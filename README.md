# 🪐 ARES-MAS: Autonomous Rover Exploration System

A Multi-Agent System (MAS) simulating an autonomous planetary exploration mission. Built with the **BDI (Belief-Desire-Intention)** architecture, the system features autonomous rovers that explore an unknown environment and coordinate task execution dynamically using the **FIPA Contract Net Protocol (CNP)**.

## Tech Stack

- **MAS Engine:** Jason (AgentSpeak)
- **Backend Environment:** Kotlin & Gradle
- **Data & Logging:** JSON / JSONL
- **Dashboard UI:** Python & Streamlit *(In development)*

## Agent Roles

- **Scout:** Explores the map, detects minerals, and acts as the auctioneer. It broadcasts tasks (Call For Proposal) and selects the best candidate based on cost.
- **Harvesters:** Act as workers. They receive CFPs, compute dynamic extraction costs based on their status, and perform the physical extraction if they win the auction.
