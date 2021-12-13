package group27;

import java.util.ArrayList;
import genius.core.Bid;
import genius.core.utility.AdditiveUtilitySpace;

public abstract class OpponentEstimator {

	protected ArrayList<Bid> opponentOffers;
	protected AdditiveUtilitySpace opUtilSpace;
	
	public OpponentEstimator(AdditiveUtilitySpace space) {
		opponentOffers = new ArrayList<Bid>();
		opUtilSpace = new AdditiveUtilitySpace(space);
	}
	
	public void addNewBid(Bid bid) {
		opponentOffers.add(bid);
		newBid(bid);
		updateModel();
	}

	protected abstract void newBid(Bid bid);
	
	protected abstract void updateModel();
	
	public AdditiveUtilitySpace getModel() {
		return opUtilSpace;
	}
	
}
