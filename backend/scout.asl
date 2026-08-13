!explore.

+!explore <- 
    .my_name(Me);
    .print("Scout online, starting exploration!");
    // Lo scout memorizza la sua posizione iniziale
    +at(0,0);
    !do_patrol(Me).

// --- SISTEMA DI MOVIMENTO E SCOPERTA LOCALE ---
+!go(X,Y) <-
    .wait(1000);
    move(X,Y);     // Se tuProlog blocca questa mossa, il piano si interrompe qui!
    -+at(X,Y).     // Se la mossa ha successo, lo scout aggiorna la sua posizione mentale

// La vera Esplorazione: si attiva SOLO quando lo scout calpesta un minerale!
@mineral_found[atomic]
+at(X,Y) : mineral(X,Y) & not already_handled(X,Y) <- 
    .print("🔍 BINGO! I physically found a mineral during my patrol at [", X, ",", Y, "]!");
    +already_handled(X,Y);
    !try_claim(X, Y).

// --- ROTTE DI PATTUGLIAMENTO (Realistiche) ---
+!do_patrol(scout1) <- 
    // Rotta naturale e ottimizzata verso ovest
    !go(1, 1); 
    !go(3, 4); // Trova il minerale senza deviazioni assurde!
    !go(2, 7); 
    .print("Exploration completed (Route A)!").

+!do_patrol(scout2) <- 
    // Rotta verso est: incrocerà la tempesta dinamica in 6,5 o 6,6!
    !go(8, 1); 
    !go(7, 4); 
    !go(6, 5); 
    !go(6, 6);
    .print("Exploration completed (Route B)!").

// Se un '!go' fallisce (es. bloccato dal Guardrail Prolog), si attiva il piano di emergenza
-!do_patrol(Me) <- 
    .print("🚨 Patrol route blocked by safety guardrail! Diverting to alternative target...");
    // Lo scout devia verso una zona sicura
    !go(8, 8). 

// --- GESTIONE ASTA (CNP) ---
+!try_claim(X, Y) <- 
    claim_mineral(X, Y);
    .my_name(Me);
    .print("Claim successful for [", X, ",", Y, "]! Launching CFP...");
    log_cnp(cfp, mineral(X,Y), Me, all);
    .broadcast(tell, cfp(mineral(X,Y)));
    .wait(3000);
    !evaluate_proposals(X, Y).

-!try_claim(X, Y) <- 
    .print("Mineral at [", X, ",", Y, "] already claimed by another scout. Standing down.").

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