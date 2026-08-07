!explore.

+!explore <- 
    .print("Scout online, starting exploration!");
    .wait(1000); move(1, 1); 
    
    .print("Attempting to cross zone [5, 5]...");
    .wait(1000); move(5, 5); 
    
    .wait(1000); move(3, 4);
    .print("Reconnaissance completed!").

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