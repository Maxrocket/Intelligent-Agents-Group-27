package group27;

import genius.core.Bid;
import genius.core.utility.AdditiveUtilitySpace;

public class TitForTat {
    
    private AdditiveUtilitySpace userUtilitySpace, opponentUtilitySpace;
    private Bid userLastBid, opponentBid, opponentLastBid;

    public TitForTat(AdditiveUtilitySpace userUtilitySpace, AdditiveUtilitySpace opponentUtilitySpace) {
        this.userUtilitySpace = userUtilitySpace;
        this.opponentUtilitySpace = opponentUtilitySpace;
    }
    
    public double getMaxTargetOpponentUtil() {
        if (opponentLastBid == null || userLastBid == null) {
            return 1;
        }
        
        // Calculate opponent's consession
        double opponentConsession = userUtilitySpace.getUtility(opponentBid) - userUtilitySpace.getUtility(opponentLastBid);
        // Use as user consession
        double targetOpponentUtil = opponentUtilitySpace.getUtility(userLastBid) - opponentConsession;
        
        return targetOpponentUtil;
    }
    
    public double getMinTargetOpponentUtil() {
        if (userLastBid == null) {
            return 0;
        }
        return opponentUtilitySpace.getUtility(userLastBid);
    }
    
    public void updateOpponenetBid(Bid opponentBid) {
        opponentLastBid = this.opponentBid;
        this.opponentBid = opponentBid;
    }
    
    public void updateUserBid(Bid userBid) {
        userLastBid = userBid;
    }
    
}
