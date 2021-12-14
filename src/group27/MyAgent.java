package group27;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import agents.org.apache.commons.lang.StringUtils;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.analysis.BidPoint;
import genius.core.analysis.MultilateralAnalysis;
import genius.core.analysis.ParetoFrontier;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.BidRanking;
import genius.core.parties.PartyWithUtility;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import genius.core.utility.UtilitySpace;

public class MyAgent extends AbstractNegotiationParty {
	
	private Bid lastOffer;
	private double maxUtil;
	
	private OpponentEstimator opEstimator;

	@SuppressWarnings("unchecked")
	@Override
	public void init(NegotiationInfo info) {
		super.init(info);
		if (hasPreferenceUncertainty()) {
			System.out.println("Preference uncertainty is enabled.");
			System.out.println("Agent ID: " + info.getAgentID());
			BidRanking bidRanking = userModel.getBidRanking();
			System.out.println("Total number of possible bids: " + userModel.getDomain().getNumberOfPossibleBids());
			System.out.println("The number of bids in the ranking: " + bidRanking.getSize());
			System.out.println("The elicitation costs area: " + user.getElicitationCost());
			List<Bid> bidList = bidRanking.getBidOrder();
			for (int i = 0; i < bidList.size(); i++) {
				System.out.println("Bid " + (bidList.size() - i) + ": " + bidList.get(i));
			}
			
			estimateUsingLP((AdditiveUtilitySpace) utilitySpace, bidRanking);
		}
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		displayUtilitySpace(additiveUtilitySpace);
		
		opEstimator = new JohnyBlack(additiveUtilitySpace);
	}
	
	//Displays a utility space to stdOut.
	private void displayUtilitySpace(AdditiveUtilitySpace additiveUtilitySpace) {
		System.out.println("#===== " + utilitySpace.getName() + " =====#");
		//Loops through all issues in domain.
		List< Issue > issues = additiveUtilitySpace.getDomain().getIssues();
		for (Issue issue : issues) {
		    int issueNumber = issue.getNumber();
		    System.out.println("- " + issue.getName() + " - Weight: " + additiveUtilitySpace.getWeight(issueNumber));

		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

		    //Loops through all options in isssue.
		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		        System.out.println("  - " + valueDiscrete.getValue());
		        System.out.println("      Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
		        try {
		            System.out.println("      Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    }
		}
		System.out.println("#=====" + CharBuffer.allocate(utilitySpace.getName().length()).toString().replace('\0', '=') + "=====#");
	}
	
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		//displayUtilitySpace(opEstimator.getModel());
		MultilateralAnalysis analyser = generateAnalyser(utilitySpace, opEstimator.getModel());
		ArrayList<BidPoint> paretoFrontier = buildParetoFrontier(generateAllBids());

		maxUtil = utilitySpace.getUtility(getMaxUtilityBid());
		double minUtil = utilitySpace.getUtility(getMinUtilityBid());
		double nashUtil = 0;
		BidPoint nashBid = calcNash(analyser);
		
		if (nashBid != null) {
			nashUtil = nashBid.getUtilityA();
		}
				
		double cNashUtil = ((nashUtil - minUtil) * 0.50) + minUtil;
		
		System.out.println("Nash Util: " + nashUtil);
		
		double time = getTimeLine().getTime();
		TimeDependent td = new TimeDependent(0.2);
		double targetUtil = td.getTargetUtil(maxUtil, cNashUtil, time);

		System.out.println("Target Util: " + targetUtil);
		
		if (lastOffer != null)
			if (getUtility(lastOffer) >= targetUtil) 
				return new Accept(getPartyId(), lastOffer);
		
		Bid selectedBid = null;
		if(lastOffer == null || paretoFrontier.size()==0)
			selectedBid = generateRandomBidAboveTarget(targetUtil, 1000,10000);
		else
			selectedBid = generateSubsetBidAboveTarget(targetUtil, paretoFrontier);
		System.out.printf("Target Util: %f\nRandom Bid Util: %f\n",targetUtil, utilitySpace.getUtility(selectedBid));
		return new Offer(getPartyId(), selectedBid);
	}
	
	private ArrayList<BidPoint> generateAllBids()
	{
		//TODO method stub, needs implementing
		return new ArrayList<BidPoint>();
	}
	
	/**
	 * Builds a pareto frontier from a set of all bids
	 */
	private ArrayList<BidPoint> buildParetoFrontier(ArrayList<BidPoint> allBids)
	{
		ParetoFrontier frontier = new ParetoFrontier();
		for (BidPoint b : allBids)
			frontier.mergeIntoFrontier(b);
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
		BidPoint bestOppBid=bidSet.get(0);
		BidPoint bestUsBid=bidSet.get(0);
		
		for(BidPoint b : bidSet)
			if(b!=null)
			{
				if(b.getUtilityA() >= target)
					if(b.getUtilityB()>bestOppBid.getUtilityB())
						bestOppBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
				if(b.getUtilityA()>bestUsBid.getUtilityA())
					bestUsBid = new BidPoint(b.getBid(), b.getUtilityA(), b.getUtilityB());
			}
		
		if(bestOppBid == null && bestUsBid==null)
		{
			System.out.println("Could not find a non-null bid in the best. Generating random bid");
			return generateRandomBidAboveTarget(target, 1000,10000);
		}
		else if(bestOppBid ==null)
		{
			System.out.printf("Could not find a bid above target\n Our Best Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
			return bestUsBid.getBid();
		}
		else
		{
			System.out.printf("Best bid above target\n Our Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
			return bestOppBid.getBid();
		}
	}
	
	/**
	 * Generates a random bid above the target utility
	 * @param target the target utility to exceed
	 * @param minBids the minimum number of random bids to generate, will only be exceeded if no bid above the target utility is generated
	 * @param maxBids the maximum number of random bids to generate, will not be exceeded
	 * @return The random bid above the target utility with the highest bid for the modelled oponent; or the bid with the highest utility for us if no such bid was generated.
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
			System.out.printf("Could not find a bid above target (%f)\n Our Best Util: %f\n Opp Util: %f\n", target, bestUtil, predictUtil(bestUsBid));
			return bestUsBid;
		}
		else
		{
			System.out.printf("Best bid above target (%f)\n Our Util: %f\n Opp Util: %f\n", target, utilitySpace.getUtility(bestOppBid), bestOpp);
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

	// - 9.0 - Recieves opponent offer and tracks the frequency of which each option has been offered.
	@Override
	public void receiveMessage(AgentID sender, Action action) {
		if (action instanceof Offer) {
			lastOffer = ((Offer) action).getBid();
			opEstimator.addNewBid(lastOffer);
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
