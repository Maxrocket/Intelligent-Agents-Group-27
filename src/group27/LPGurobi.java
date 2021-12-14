package group27;

import java.util.Collections;
import java.util.List;

import genius.core.Bid;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;

public class LPGurobi extends OpponentEstimator {

	public LPGurobi(AdditiveUtilitySpace space) {
		super(space);
	}

	@Override
	protected void newBid(Bid bid) {
		
	}

	@Override
	protected void updateModel() {
		List<Bid> bidList = (List<Bid>) opponentOffers.clone();
		Collections.reverse(bidList);
		UserEstimator.estimateUsingLP(opUtilSpace, new BidRanking(bidList, 0, 1));
	}

}
