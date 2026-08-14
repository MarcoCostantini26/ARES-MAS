!standby.

+!standby <- 
    .print("Harvester online, awaiting auctions...").

+cfp(mineral(X,Y))[source(S)] <- 
    .my_name(Me);
    .print("CFP Receipt from ", S, " for the mineral to [", X, ", ", Y, "]");
    
    Distance = math.abs(X - 0) + math.abs(Y - 0);
    
    EstimatedCost = Distance * 2;
    
    if (Me == harv1) {
        Cost = EstimatedCost - 5; 
    } else {
        Cost = EstimatedCost + 10;
    };
    
    .print("Calculated Manhattan distance is ", Distance, ". Bidding cost: ", Cost, "...");
    log_cnp(propose, offer(mineral(X,Y), Cost), Me, S);
    .send(S, tell, propose(mineral(X,Y), Cost)).

+accept(mineral(X,Y))[source(S)] <- 
    .my_name(Me);
    .print("YAY! I won the contract for [", X, ", ", Y, "]! I'm going to extract...");
    .wait(1500);
    move(X, Y);
    !do_extract(X, Y).

+!do_extract(X, Y) <-
    .print("Attempting to extract at [", X, ", ", Y, "]...");
    extract(X, Y);
    .print("Extraction successful! Delegating return to STRIPS...");
    !plan_return(X, Y).

-!do_extract(X, Y) <-
    .print("Extraction BLOCKED by tuProlog guardrail! Triggering emergency return.");
    !plan_return(X, Y).

+!plan_return(X, Y) <-
    .my_name(Me);
    strips.plan_return(Me, X, Y, 0, 0, Plan);
    .print("STRIPS returned the following plan: ", Plan);
    !execute_plan(Plan).

+!execute_plan([]) <- 
    .print("Safely returned to base!");
    mission_complete.

+!execute_plan([move(NX, NY) | Rest]) <- 
    move(NX, NY);
    .wait(1000);
    !execute_plan(Rest).