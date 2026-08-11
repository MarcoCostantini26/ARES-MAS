!standby.

+!standby <- 
    .print("Harvester online, awaiting auctions...").

+cfp(mineral(X,Y))[source(S)] <- 
    .my_name(Me);
    .print("CFP Receipt from ", S, " for the mineral to [", X, ", ", Y, "]");
    
    .random(R);
    Cost = 10 + math.round(R * 20); 
    
    .print("My calculated cost is ", Cost, ". Send proposal to ", S, "...");
    
    log_cnp(propose, offer(mineral(X,Y), Cost), Me, S);
    .send(S, tell, propose(mineral(X,Y), Cost)).

+accept(mineral(X,Y))[source(S)] <- 
    .my_name(Me);
    .print("YAY! I won the contract for [", X, ", ", Y, "]! I'm going to extract...");
    .wait(1500);
    move(X, Y);
    .print("Extraction completed at [", X, ", ", Y, "]!");
    
    .print("CRITICAL BATTERY DETECTED! Requesting route from STRIPS Engine...");
    
    strips.plan_return(Me, X, Y, 0, 0, Plan);
    
    .print("STRIPS returned the following plan: ", Plan);
    .print("Initiating physical return sequence...");
    
    !execute_plan(Plan).


+!execute_plan([]) <- 
    .print("Safely returned to base!");
    mission_complete.

+!execute_plan([move(NX, NY) | Rest]) <- 
    move(NX, NY);
    .wait(1000);
    !execute_plan(Rest).