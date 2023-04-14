package Server.Model.MatchStatus;

public class WaitingForPlayers extends MatchStatus {
    private int numberMissingPlayers;

    @Override
    public MatchStatus evolve() throws UnsupportedOperationException{
        numberMissingPlayers--;
        if(numberMissingPlayers == 0){
            return new Running();
        }else{
            throw new UnsupportedOperationException("Still "+numberMissingPlayers+
                    "players missing");
        }
    }
}
