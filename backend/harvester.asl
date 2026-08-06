!standby.

+!standby <- 
    .print("Harvester online, awaiting auctions...").

+cfp(mineral(X,Y))[source(S)] <- 
    .my_name(Me);
    .print("CFP Receipt from ", S, " for the mineral to [", X, ", ", Y, "]");
    
    .random(R);
    Cost = 10 + math.round(R * 20); 
    
    .print("My calculated cost is ", Cost, ". Send proposal to ", S, "...");
    
    log_cnp(propose, mineral(X,Y), Me, S);
    .send(S, tell, propose(mineral(X,Y), Cost)).

+accept(mineral(X,Y))[source(S)] <- 
    .my_name(Me);
    .print("YAY! I won the contract for [", X, ", ", Y, "]! I'm going to extract...");
    .wait(1500);
    move(X, Y);
    .print("Extraction completed at [", X, ", ", Y, "]! Return to standby...").