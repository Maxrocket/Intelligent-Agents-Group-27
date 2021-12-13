package group27;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import agents.org.apache.commons.lang.StringUtils;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.analysis.BidPoint;
import genius.core.analysis.MultilateralAnalysis;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Objective;
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
	
	private static double MINIMUM_TARGET = 0.8;
	private Bid lastOffer;
	private double maxUtil;
	private double minUtil;
	private double threshold;
	
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
		}
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		displayUtilitySpace(additiveUtilitySpace);
		
		List< Issue > issues = additiveUtilitySpace.getDomain().getIssues();
		
		// - 2.0 - Sets parameters for consession. Max concession will be changed to Nash bargaining solution.
		maxUtil = utilitySpace.getUtility(getMaxUtilityBid());
		minUtil = utilitySpace.getUtility(getMinUtilityBid());
		System.out.println("Max: " + maxUtil + ", Min: " + minUtil);
		threshold = 0.5;
		// - END 2.0
		
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
	
	@SuppressWarnings("unchecked")
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		displayUtilitySpace(opEstimator.getModel());
		
		// - 4.0 - Concession calculation based on max concession. Currently linear. Needs abtracting to try different solutions.
		double time = getTimeLine().getTime();
		double currentConsession = ((1.0 - threshold) * (1.0 - time)) + threshold;
		double targetUtil = ((maxUtil - minUtil) * currentConsession) + minUtil;
		// - END 4.0
		
		if (lastOffer != null)
			if (getUtility(lastOffer) >= targetUtil) 
				return new Accept(getPartyId(), lastOffer);
		
		return new Offer(getPartyId(), generateRandomBidAboveTarget(targetUtil));
	}

	private BidPoint calcNash(UtilitySpace oppPrefs, UtilitySpace ourPrefs)
	{
		PartyWithUtility oppParty = new PartyWithUtility() {
			@Override
			public AgentID getID()
			{
				return new AgentID("oppParty");
			}

			@Override
			public UtilitySpace getUtilitySpace()
			{
				return oppPrefs;
			}			
		};
		
		PartyWithUtility ourParty = new PartyWithUtility() {

			@Override
			public AgentID getID()
			{
				return new AgentID("ourParty");
			}

			@Override
			public UtilitySpace getUtilitySpace()
			{
				return ourPrefs;
			}
		};
		
		ArrayList<PartyWithUtility> parties = new ArrayList();
		parties.add(oppParty);
		parties.add(ourParty);
		
		MultilateralAnalysis analyser = new MultilateralAnalysis(parties, null, 200d);
		
		return analyser.getNashPoint();
	}
	
	private Bid generateRandomBidAboveTarget(double target) {
		// - 5.0 - Generates counter bid by sampling 100 random bids that are over target util and offering the best for the opponent.
		//         Can be rewritten into a smarter way of generating counter offers.
		ArrayList<Bid> samples = new ArrayList<Bid>();
		Bid randomBid = generateRandomBid();
		double util;
		for (int i = 0; i < 100; i++) {
			randomBid = generateRandomBid();
			util = utilitySpace.getUtility(randomBid);
			if (util >= target) {
				samples.add(randomBid);
			}
		}
		if (samples.size() > 1) {
			Collections.sort(samples, bidSort);
			System.out.println("Best Offer: " + samples.get(0) + ", " + predictUtil(samples.get(0)));
			System.out.println("Worst Offer: " + samples.get(samples.size() - 1) + ", " + predictUtil(samples.get(samples.size() - 1)));
			randomBid = samples.get(0);
		} else if (samples.size() > 1) {
			randomBid = samples.get(0);
		}
		// - END 5.0
		
		return randomBid;
	}
	
	// - 6.0 - Comparator to sort lists by which is best for the opponent.
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
