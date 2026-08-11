!explore.

+!explore <- 
    .print("Scout online, starting exploration!");
    .wait(1000); move(1, 1); 
    .wait(1000); move(2, 3);
    .wait(1000); move(3, 4);
    .print("Exploration completed!").

+hazard(X, Y, Type) : not hazard_tested <- 
    +hazard_tested;
    .print("Detected hazard at [", X, ",", Y, "]! Testing safety guardrail by crossing it...");
    !test_crossing(X, Y).

+!test_crossing(X, Y) <- 
    move(X, Y);
    .print("Crossed safely (unexpected!)").

-!test_crossing(X, Y) <- 
    .print("GUARDRAIL TEST: Movement to [", X, ",", Y, "] correctly blocked by tuProlog!").

-!explore <- 
    .print("PLAN FAILED: Movement blocked! The safety system took control.");
    .print("Replanning a new route to bypass the hazard...");
    move(3, 4).

+mineral(X, Y) : not already_handled(X, Y) <- 
    +already_handled(X, Y);
    .my_name(Me);
    .print("WOW! I perceived a mineral at coordinates: [", X, ", ", Y, "]!");
    .print("Broadcasting CFP to all harvesters...");
    
    log_cnp(cfp, mineral(X,Y), Me, all);
    .broadcast(tell, cfp(mineral(X,Y)));
    
    .wait(3000);
    !evaluate_proposals(X, Y).

+!evaluate_proposals(X, Y) <-
    .findall(offer(Cost, H), propose(mineral(X,Y), Cost)[source(H)], Offers);
    .print("Proposals collected: ", Offers);
    
    if (Offers == []) {
        .print("No proposals received for the mineral at [", X, ",", Y, "]. No assignment.");
    } else {
        .min(Offers, offer(BestCost, Best));
        .print("Winner: ", Best, " with cost ", BestCost);
        
        .my_name(Me);
        log_cnp(accept, mineral(X,Y), Me, Best);
        .send(Best, tell, accept(mineral(X,Y)));
    }.