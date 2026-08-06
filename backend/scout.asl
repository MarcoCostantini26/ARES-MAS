!explore.

+!explore <- 
    .print("Scouts online, let's explore!");
    .wait(1000); move(1, 1); 
    .wait(1000); move(2, 3);
    .wait(1000); move(3, 4);
    .print("Reconnaissance completed!").

+mineral(X, Y) : not already_handled(X, Y) <- 
    +already_handled(X, Y);
    .my_name(Me);
    .print("I sensed a mineral at the coordinates: [", X, ", ", Y, "]!");
    .print("CFP launch to all collectors...");
    
    log_cnp(cfp, mineral(X,Y), Me, all);
    .broadcast(tell, cfp(mineral(X,Y)));
    
    .wait(3000);
    !evaluate_proposals(X, Y).

+!evaluate_proposals(X, Y) <-
    .findall(offer(Cost, H), propose(mineral(X,Y), Cost)[source(H)], Offers);
    .print("Proposals collected: ", Offers);
    
    if (Offers == []) {
        .print("No proposal received for the mineral at [", X, ",", Y, "]. No assignment.");
    } else {
        .min(Offers, offer(BestCost, Best));
        .print("Winner: ", Best, " with cost ", BestCost);
        
        .my_name(Me);
        log_cnp(accept, mineral(X,Y), Me, Best);
        .send(Best, tell, accept(mineral(X,Y)));
    }.