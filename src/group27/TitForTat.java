package group27;

import genius.core.Bid;
import genius.core.utility.AdditiveUtilitySpace;

public class TitForTat {
    
    private AdditiveUtilitySpace userUtilitySpace, opponentUtilitySpace;
    private Bid userLastBid, opponentLastBid;

    public TitForTat(AdditiveUtilitySpace userUtilitySpace, AdditiveUtilitySpace opponentUtilitySpace) {
        this.userUtilitySpace = userUtilitySpace;
        this.opponentUtilitySpace = opponentUtilitySpace;
    }
    
    public double getTargetOpponentUtil(Bid opponentBid) {
        if (opponentLastBid == null || userLastBid == null) {
            opponentLastBid = opponentBid;
            return 0;
        }
        
        // Calculate opponent's consession
        double opponentConsession = userUtilitySpace.getUtility(opponentLastBid) - userUtilitySpace.getUtility(opponentBid);
        // Use as user consession
        double targetOpponentUtil = opponentUtilitySpace.getUtility(userLastBid) - opponentConsession;
        
        // Update last bid
        opponentLastBid = opponentBid;
        
        return targetOpponentUtil;
    }
    
    public void updateUserBid(Bid userBid) {
        userLastBid = userBid;
    }
    
}
