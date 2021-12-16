package group27;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import agents.org.apache.commons.lang.StringUtils;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.analysis.BidPoint;
import genius.core.analysis.MultilateralAnalysis;
import genius.core.analysis.ParetoFrontier;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Objective;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.UserModel;
import genius.core.parties.PartyWithUtility;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.UtilitySpace;

public class Agent27 extends AbstractNegotiationParty {
	
	private Bid lastOffer;
	private double maxUtil;
	private ArrayList<Bid> allPossibleBids;
	private double deltaModel;
	private OpponentEstimator opEstimator;
        private TitForTat tft;

	@SuppressWarnings("unchecked")
	@Override
	public void init(NegotiationInfo info) {
		super.init(info);

		
		if (hasPreferenceUncertainty()) {
			//System.out.println("Preference uncertainty is enabled.");
			//System.out.println("Agent ID: " + info.getAgentID());
			BidRanking bidRanking = userModel.getBidRanking();
			//System.out.println("Total number of possible bids: " + userModel.getDomain().getNumberOfPossibleBids());
			//System.out.println("The number of bids in the ranking: " + bidRanking.getSize());
			//System.out.println("The elicitation costs area: " + user.getElicitationCost());
			List<Bid> bidList = bidRanking.getBidOrder();
			for (int i = 0; i < bidList.size(); i++) {
				//System.out.println("Bid " + (bidList.size() - i) + ": " + bidList.get(i));
			}
			
			UserEstimator.estimateUsingLP((AdditiveUtilitySpace) utilitySpace, bidRanking);
		}

		allPossibleBids = generateAllBids(info.getUtilitySpace().getDomain());
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		displayUtilitySpace(additiveUtilitySpace);
		deltaModel = 1d;
		
		opEstimator = new JohnyBlack(additiveUtilitySpace);
		//opEstimator = new LPGurobi(additiveUtilitySpace);
                
                tft = new TitForTat(additiveUtilitySpace, (AdditiveUtilitySpace) opEstimator.opUtilSpace);
	}
	
	//Displays a utility space to stdOut.
	private void displayUtilitySpace(AdditiveUtilitySpace additiveUtilitySpace) {
		//System.out.println("#===== " + utilitySpace.getName() + " =====#");
		//Loops through all issues in domain.
		List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
		for (Issue issue : issues) {
		    int issueNumber = issue.getNumber();
		    //System.out.println("- " + issue.getName() + " - Weight: " + additiveUtilitySpace.getWeight(issueNumber));

		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

		    //Loops through all options in isssue.
		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		        //System.out.println("  - " + valueDiscrete.getValue());
		        //System.out.println("      Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
		        try {
		            //System.out.println("      Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    }
		}
		//System.out.println("#=====" + CharBuffer.allocate(utilitySpace.getName().length()).toString().replace('\0', '=') + "=====#");
	}
	
	//Pretty sure this is useless
	private ArrayList<Bid> generateBidPlan(double time, ArrayList<BidPoint> paretoFrontier, MultilateralAnalysis analyser)
	{
		maxUtil = utilitySpace.getUtility(getMaxUtilityBid());
		double minUtil = utilitySpace.getUtility(getMinUtilityBid());
		double nashUtil = 0;
		BidPoint nashBid = calcNash(analyser);
		ArrayList<Bid> ret = new ArrayList<Bid>();
		if (nashBid != null) {
			nashUtil = nashBid.getUtilityA();
		}
				
		double cNashUtil = ((nashUtil - minUtil) * 0.75) + minUtil;
		
		//System.out.println("Nash Util: " + nashUtil);

		while(time<1.0)
		{
			TimeDependent td = new TimeDependent(0.4);
			double targetUtil = td.getTargetUtil(maxUtil, cNashUtil, time);
			if (time >= 0.95) {
				targetUtil = td.getTargetUtil(targetUtil, minUtil, (time - 0.95) * 20.0);
			}
	                
            if (time >= 0.995) {
                    targetUtil = 0;
            }
	
			//System.out.println("Target Util: " + targetUtil);
			
			Bid selectedBid = null;
			if(lastOffer == null || paretoFrontier.size()==0)
				selectedBid = generateRandomBidAboveTarget(targetUtil, 1000,10000);
			else
				selectedBid = generateSubsetBidAboveTarget(targetUtil, paretoFrontier);
			ret.add(selectedBid);
			time += 0.005;
		}
		return ret;
	}
	
	private double EEU(ArrayList<BidPoint> paretoFrontier, MultilateralAnalysis analyser, UtilitySpace us)
	{
		Bid selectedBid = null;
		if(lastOffer == null || paretoFrontier.size()==0)
			selectedBid = generateRandomBidAboveTarget(0, 1000,10000);
		else
			selectedBid = generateSubsetBidAboveTarget(0, paretoFrontier);
		
		return us.getUtility(selectedBid);
	}
	
	/**
	 * Generates all possibly utility spaces for a bid's placement in the user model's bid ranking
	 * @param model the user model whose ranking is being placed into
	 * @param bid the bid being placed
	 */
	private ArrayList<AdditiveUtilitySpace> generateUtilitySpaces(UserModel model, Bid bid)
	{
		
		ArrayList<Bid> baseBidOrder = new ArrayList<Bid>(model.getBidRanking().getBidOrder());
		ArrayList<AdditiveUtilitySpace> ret = new ArrayList<AdditiveUtilitySpace>();
		for(int i=0;i<model.getBidRanking().getSize()+1;i++)
		{
			AdditiveUtilitySpace newUtilitySpace = (AdditiveUtilitySpace)utilitySpace.copy();
			ArrayList<Bid> newBidOrder = new ArrayList<Bid>(baseBidOrder);
			newBidOrder.add(i, bid);
			UserModel updatedModel = new UserModel(new BidRanking(newBidOrder, model.getBidRanking().getLowUtility(), model.getBidRanking().getHighUtility()));
			UserEstimator.estimateUsingLP(newUtilitySpace, userModel.getBidRanking());
			ret.add(newUtilitySpace);
		}

		return ret;
	}
	
	private double EVOI(ArrayList<BidPoint> paretoFrontier, MultilateralAnalysis analyser, Bid bid)
	{
		if(userModel.getBidRanking().getBidOrder().contains(bid))
			return 0;
		int cnt=0;
		double sum=0d;
		ArrayList<AdditiveUtilitySpace> uss = generateUtilitySpaces(userModel, bid);
		for(AdditiveUtilitySpace us : uss)
		{
			cnt++;
			sum+=EEU(paretoFrontier, analyser, us);
		}
		
		return sum/cnt;
	}
	
	private boolean elicitPredicate(ArrayList<BidPoint> paretoFrontier, MultilateralAnalysis analyser, Bid bid, double elicitCost)
	{
		double evoi = EVOI(paretoFrontier, analyser, bid);
		System.out.printf("Comparing %f > %f", evoi, elicitCost);
		return evoi > elicitCost;
	}
	
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		//System.out.println("-");
		//displayUtilitySpace(opEstimator.getModel());
		MultilateralAnalysis analyser = generateAnalyser(utilitySpace, opEstimator.getModel());
		ArrayList<BidPoint> paretoFrontier = buildParetoFrontier(generateAllBidPoints());

		maxUtil = utilitySpace.getUtility(getMaxUtilityBid());
		double minUtil = utilitySpace.getUtility(getMinUtilityBid());
		double nashUtil = 0;
		BidPoint nashBid = calcNash(analyser);
		
		if (nashBid != null) {
			nashUtil = nashBid.getUtilityA();
		}
				
		double cNashUtil = ((nashUtil - minUtil) * 0.75) + minUtil;
		
		//System.out.println("Nash Util: " + nashUtil);
		
		double time = getTimeLine().getTime();
		TimeDependent td = new TimeDependent(0.4);
		double targetUtil = td.getTargetUtil(maxUtil, cNashUtil, time);
		if (time >= 0.95) {
			targetUtil = td.getTargetUtil(targetUtil, minUtil, (time - 0.95) * 20.0);
		}
                
                if (time >= 0.995) {
                        targetUtil = 0;
                }

		//System.out.println("Target Util: " + targetUtil);
		
		if (lastOffer != null)
			if (getUtility(lastOffer) >= targetUtil) 
				return new Accept(getPartyId(), lastOffer);
		
		Bid selectedBid = null;
		if(lastOffer == null || paretoFrontier.size()==0)
			selectedBid = generateRandomBidAboveTarget(targetUtil, 1000,10000);
		else
			//selectedBid = generateSubsetBidWithinOppTarget(tft.getMinTargetOpponentUtil(), tft.getMaxTargetOpponentUtil(), generateAllBidPoints());
			selectedBid = generateSubsetBidAboveTarget(targetUtil, paretoFrontier);
		if(hasPreferenceUncertainty())
			if(elicitPredicate(paretoFrontier, analyser, selectedBid, user.getElicitationCost()))
				elicitBid(selectedBid);
		
		//System.out.printf("Target Util: %f\nRandom Bid Util: %f\n",targetUtil, utilitySpace.getUtility(selectedBid));
                tft.updateUserBid(selectedBid);
		return new Offer(getPartyId(), selectedBid);
	}
	
	private ArrayList<Bid> generateAllBids(Domain domain) {
		List<Issue> issues = domain.getIssues();
		ArrayList<Bid> bids = generateAllBidsR(issues, domain.getRandomBid(new Random()), new ArrayList<Bid>());
		for (Bid bid : bids) {
			//System.out.println(bid);
		}
		return bids;
	}
	
	private ArrayList<Bid> generateAllBidsR(List<Issue> issues, Bid bid, ArrayList<Bid> bids) {
		if (issues.size() > 1) {
			IssueDiscrete issueDiscrete = (IssueDiscrete) issues.get(0);
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				bid = bid.putValue(issueDiscrete.getNumber(), valueDiscrete);
				Bid newBid = new Bid(bid);
				bids = generateAllBidsR(issues.subList(1, issues.size()), newBid, bids);
			}
		} else {
			IssueDiscrete issueDiscrete = (IssueDiscrete) issues.get(0);
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				bid = bid.putValue(issueDiscrete.getNumber(), valueDiscrete);
				Bid newBid = new Bid(bid);
				bids.add(newBid);
			}
		}
		return bids;
	}
	
	private ArrayList<BidPoint> generateAllBidPoints() {
		ArrayList<BidPoint> bidPoints = new ArrayList<BidPoint>();
		for (Bid bid : allPossibleBids) {
			bidPoints.add(new BidPoint(bid, utilitySpace.getUtility(bid), opEstimator.getModel().getUtility(bid)));
		}
		return bidPoints;
	}
	
	/**
	 * Builds a pareto frontier from a set of all bids
	 */
	private ArrayList<BidPoint> buildParetoFrontier(ArrayList<BidPoint> allBids)
	{
		//System.out.println(allBids);
		ParetoFrontier frontier = new ParetoFrontier();
		for (BidPoint b : allBids)
			frontier.mergeIntoFrontier(b);
		//System.out.println(frontier.getFrontier());
		return new ArrayList<BidPoint>(frontier.getFrontier());
	}
	
	/**
	 * Generates a multilateral analyser, excluding bids, using our and our opponent's preferences
	 */
	private MultilateralAnalysis generateAnalyser(UtilitySpace ourPrefs, UtilitySpace oppPrefs)
	{
		PartyWithUtility oppParty = new PartyWithUtility() {
			@Override
			public AgentID getID() { return new AgentID("oppParty"); }

			@Override
			public UtilitySpace getUtilitySpace() { return oppPrefs; }			
		};
		
		PartyWithUtility ourParty = new PartyWithUtility() {

			@Override
			public AgentID getID() { return new AgentID("ourParty"); }

			@Override
			public UtilitySpace getUtilitySpace() { return ourPrefs; }
		};
		
		ArrayList<PartyWithUtility> parties = new ArrayList();
		parties.add(ourParty);
		parties.add(oppParty);
		
		MultilateralAnalysis analyser = new MultilateralAnalysis(parties, null, getTimeLine().getTime());
		return analyser;
	}
	
	/**
	 * Calculates a nash bargaining solution 
	 * @param analyser the analyser to pull the solution from
	 */
	private BidPoint calcNash(MultilateralAnalysis analyser) {
		return analyser.getNashPoint();
	}
	
	//TODO ensure bidSet.get(x).getUtilityA() is our utility, not opponent utility
	private Bid generateSubsetBidAboveTarget(double target, ArrayList<BidPoint> bidSet)
	{
		BidPoint StartBid = new BidPoint(null,0d,0d);
		BidPoint bestOppBid=new BidPoint(null,0d,0d);
		BidPoint bestUsBid=StartBid;
		boolean found = false;
		
		for(BidPoint b : bidSet)
			if(b!=null)
			{
				if(b.getUtilityA() >= target && b.getUtilityB() > bestOppBid.getUtilityB())
				{
					found = true;
					bestOppBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
				}
				if(b.getUtilityA() > bestUsBid.getUtilityA())
					bestUsBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
			}
		
		if(!found && bestUsBid == StartBid)
		{
			//System.out.println("Could not find a non-null bid in the best. Generating random bid");
			return generateRandomBidAboveTarget(target, 1000,10000);
		}
		else if(!found)
		{
			//System.out.printf("Could not find a bid above target\n Our Best Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
			return bestUsBid.getBid();
		}
		else
		{
			//System.out.printf("Best bid above target\n Our Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
			return bestOppBid.getBid();
		}
	}
        
        private Bid generateSubsetBidWithinOppTarget(double targetMin, double targetMax, ArrayList<BidPoint> bidSet)
        {
            BidPoint StartBid = new BidPoint(null,0d,0d);
            BidPoint bestOppBid = StartBid;
            BidPoint worstOppBid = StartBid;
            BidPoint bestUsBid = StartBid;
            boolean found = false;

            for(BidPoint b : bidSet)
                if(b!=null)
                {
                    //opp's util in target range, better than best bid for us.
                    if (b.getUtilityB() <= targetMax && b.getUtilityB() > targetMin && (b.getUtilityA() > bestUsBid.getUtilityA() || bestOppBid.getBid() == null)) {
                        found = true;
                        bestUsBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
                    }
                    //opp's bid better than best bid so far, below max.
                    if((b.getUtilityB() > bestOppBid.getUtilityB() || bestOppBid.getBid() == null) && b.getUtilityB() < targetMax)
                        bestOppBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
                    
                    //opp's bid worse than worst bid so far, above min.
                    if((b.getUtilityB() < worstOppBid.getUtilityB() || worstOppBid.getBid() == null) && b.getUtilityB() > targetMin)
                        worstOppBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
                }

            if(!found && bestOppBid.getBid() == null && worstOppBid.getBid() == null) {
                System.out.println("Could not find a non-null bid. Generating random bid");
                return generateRandomBid();
            } else if(!found && worstOppBid.getBid() == null) {
                System.out.printf("Could not find a bid within target\n Our Best Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
                return bestOppBid.getBid();
            } else if(!found) {
                System.out.printf("Best bid above target\n Our Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
                return worstOppBid.getBid();
            } else {
                return bestUsBid.getBid();
            }
        }
	
	/**
	 * Generates a random bid above the target utility
	 * @param target the target utility to exceed
	 * @param minBids the minimum number of random bids to generate, will only be exceeded if no bid above the target utility is generated
	 * @param maxBids the maximum number of random bids to generate, will not be exceeded
	 * @return The random bid above the target utility with the highest bid for the modelled opponent; or the bid with the highest utility for us if no such bid was generated.
	 */
	private Bid generateRandomBidAboveTarget(double target, long minBids, long maxBids) {
		// - 5.0 - Generates counter bid by sampling 100 random bids that are over target util and offering the best for the opponent.
		//         Can be rewritten into a smarter way of generating counter offers.
		if(maxBids <= minBids)
			maxBids = minBids+1;
		
		Bid randomBid = generateRandomBid();
		Bid bestOppBid=null;
		Bid bestUsBid=null;
		double bestUtil=0;
		double oppUtil;
		double bestOpp = 0;
		double util = utilitySpace.getUtility(randomBid);
		boolean targetMet=false;
		
		for (int i=0;(i<maxBids && !targetMet) || i < minBids;i++)
		{
			if(util >= target)
			{
				targetMet=true;
				oppUtil = predictUtil(randomBid);
				if(oppUtil>bestOpp)
				{
					bestOppBid = new Bid(randomBid);
					bestOpp=oppUtil;
				}
			}
			if(util>bestUtil)
			{
				bestUsBid = new Bid(randomBid);
				bestUtil = util;
			}
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
		}
		
		if(bestOppBid == null)
		{
			//System.out.printf("Could not find a bid above target (%f)\n Our Best Util: %f\n Opp Util: %f\n", target, bestUtil, predictUtil(bestUsBid));
			return bestUsBid;
		}
		else
		{
			//System.out.printf("Best bid above target (%f)\n Our Util: %f\n Opp Util: %f\n", target, utilitySpace.getUtility(bestOppBid), bestOpp);
			return bestOppBid;
		}
		
		
		// - END 5.0
		
	}
	
	// - 6.0 - Comparator to sort lists by which is best for the opponent. DEPRICATED
	private Comparator<Bid> bidSort = new Comparator<Bid>() {
		public int compare(Bid arg0, Bid arg1) {
			double diff = predictUtil(arg1) - predictUtil(arg0);
			return diff < 0 ? -1 : diff > 0 ? 1 : 0;
		}
	};
	// - END 6.0

	private double predictUtil(Bid bid) {	
		return opEstimator.getModel().getUtility(bid);
	}
	
	private Bid getMaxUtilityBid() {
	    try {
	        return utilitySpace.getMaxUtilityBid();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	private Bid getMinUtilityBid() {
	    try {
	        return utilitySpace.getMinUtilityBid();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return null;
	}
	
	private double measureChange(AdditiveUtilitySpace a, AdditiveUtilitySpace b)
	{
		Set<Map.Entry<Objective, Evaluator>> aEvals = a.getEvaluators();
		Set<Map.Entry<Objective, Evaluator>> bEvals = b.getEvaluators();
		Map<Objective, Evaluator> aMap = new HashMap<Objective,Evaluator>();
		Map<Objective, Evaluator> bMap = new HashMap<Objective,Evaluator>();
		double sum = 0;
		double cnt = 0;
		for (Map.Entry<Objective, Evaluator> e : aEvals)
			aMap.put(e.getKey(), e.getValue());
		for (Map.Entry<Objective, Evaluator> e : bEvals)
			bMap.put(e.getKey(), e.getValue());
		
		for(Objective o : aMap.keySet())
			if(bMap.containsKey(o))
			{
				//System.out.printf("Our Weight: %f\nTheir Weight: %f\n", aMap.get(o).getWeight(),bMap.get(o).getWeight());
				sum += (Math.abs(aMap.get(o).getWeight() - bMap.get(o).getWeight())); 
				cnt ++;
			}
		
		return sum/cnt;
	}
	
	/**
	 * DOWNGRADE
	 * @param bid
	 * @param deltaModel
	 * @return
	 */
	private double elicitBid(Bid bid)
	{
		//condition to elicit on
		if(!userModel.getBidRanking().getBidOrder().contains(bid))
		{
			AdditiveUtilitySpace oldUS = (AdditiveUtilitySpace)utilitySpace.copy();
				
			userModel = user.elicitRank(bid, userModel);
			UserEstimator.estimateUsingLP((AdditiveUtilitySpace) utilitySpace, userModel.getBidRanking());
			return measureChange(oldUS, (AdditiveUtilitySpace)utilitySpace);
			
		}
		return deltaModel;
	}

	// - 9.0 - Recieves opponent offer and tracks the frequency of which each option has been offered.
	@Override
	public void receiveMessage(AgentID sender, Action action) {
		if (action instanceof Offer) {
			lastOffer = ((Offer) action).getBid();
			opEstimator.addNewBid(lastOffer);
                        tft.updateOpponenetBid(lastOffer);
			if(hasPreferenceUncertainty())
			{
				//deltaModel = elicitBid(lastOffer);
				System.out.printf("change in model: %f\n", deltaModel);
			}
		}
	}
	// - END 9.0

	@Override
	public String getDescription() {
		return "Agent Description";
	}
	
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() {
		return super.estimateUtilitySpace();
	}

}
