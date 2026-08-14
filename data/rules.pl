safe_to_move(X, Y) :- \+ hazard(X, Y).

safe_to_extract(Rover, X, Y) :- 
    is_harvester(Rover), 
    \+ hazard(X, Y), 
    battery_level(Rover, Level), 
    Level > 20.