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
import java.util.PriorityQueue;
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
	MultilateralAnalysis analyser = null;
	ArrayList<BidPoint> paretoFrontier = null;
	private Bid lastOffer;
	private double maxUtil;
	private ArrayList<Bid> allPossibleBids;
	private double deltaModel;
	private OpponentEstimator opEstimator;
	private ArrayList<Bid> consideredElicits;
	
	//functionality options
	private boolean prefElicit = true;
	private String opponentModel = "JohnyBlack";
	
	//output options
	private boolean verboseElicit = true;
	private boolean verboseStartup = true;
	private boolean displayUtilSpace = true;
	private boolean showUtilCalcs = true;
	private boolean verboseBidGeneration = true;
	private boolean verboseFrontier = true;
	
	@SuppressWarnings("unchecked")
	@Override
	public void init(NegotiationInfo info) {
		super.init(info);

		
		if (hasPreferenceUncertainty()) {
			if(verboseStartup)
			{
				 System.out.println("Preference uncertainty is enabled.");
				 System.out.println("Agent ID: " + info.getAgentID());
			}
			BidRanking bidRanking = userModel.getBidRanking();
			if(verboseStartup)
			{
				System.out.println("Total number of possible bids: " + userModel.getDomain().getNumberOfPossibleBids());
				System.out.println("The number of bids in the ranking: " + bidRanking.getSize());
				System.out.println("The elicitation costs area: " + user.getElicitationCost());
			}
			List<Bid> bidList = bidRanking.getBidOrder();
			if(verboseStartup)
				for (int i = 0; i < bidList.size(); i++)
					System.out.println("Bid " + (bidList.size() - i) + ": " + bidList.get(i));
			
			UserEstimator.estimateUsingLP((AdditiveUtilitySpace) utilitySpace, bidRanking);
		}

		allPossibleBids = generateAllBids(info.getUtilitySpace().getDomain());
		
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
		displayUtilitySpace(additiveUtilitySpace);
		deltaModel = 1d;
		
		if(opponentModel.equals("JohnyBlack"))
			opEstimator = new JohnyBlack(additiveUtilitySpace);
		else if(opponentModel.equals("Gurobi"))
			opEstimator = new LPGurobi(additiveUtilitySpace);
	}
	
	//Displays a utility space to stdOut.
	private void displayUtilitySpace(AdditiveUtilitySpace additiveUtilitySpace) {
		if(!displayUtilSpace)
			return;
		System.out.println("#===== " + utilitySpace.getName() + " =====#");
		//Loops through all issues in domain.
		List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();
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
		
		if(showUtilCalcs)
			System.out.println("Nash Util: " + nashUtil);

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
            if(showUtilCalcs)
            	System.out.println("Target Util: " + targetUtil);
			
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
	
	
	private double EEU(ArrayList<Bid> bidPlan, UtilitySpace us)
	{
		int cnt=0;
		double sum=0;
		for(Bid b : bidPlan)
		{
			cnt++;
			sum+=us.getUtility(b);
		}
		return sum/cnt;
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
			UserEstimator.estimateUsingLP(newUtilitySpace, updatedModel.getBidRanking());
			ret.add(newUtilitySpace);
		}

		return ret;
	}
	
	private double EVOI(Bid bid)
	{
		if(userModel.getBidRanking().getBidOrder().contains(bid))
			return 0;
		
		ArrayList<AdditiveUtilitySpace> uss = generateUtilitySpaces(userModel, bid);
		PriorityQueue<Double> eeusQueue = new PriorityQueue<Double>();
		double sum = 0;
		int cnt = 0;
		for(AdditiveUtilitySpace us : uss)
		{
			ArrayList<BidPoint> paretoFrontier = buildParetoFrontier(generateAllBidPoints(us));
			MultilateralAnalysis analyser = generateAnalyser(us, opEstimator.getModel());
			double eeu = (EEU(generateBidPlan(getTimeLine().getTime(), paretoFrontier, analyser), us));	
			sum+=eeu;
			cnt++;
			eeusQueue.add(eeu);
		}
		
		ArrayList<Double> eeus = new ArrayList<Double>(eeusQueue);
		ArrayList<Double> ds = new ArrayList<Double>();
		double mean = sum/cnt;
		while(eeusQueue.peek()!=null)
			sum = (Math.pow(eeusQueue.remove()-mean,2));
		double stdev = Math.pow(sum, 0.5);
		
		sum=0;
		cnt=0;
		for(Double d : eeus)
			if(d>(mean-stdev) && d < (mean+stdev))
			{
				sum+=d;
				cnt++;
			}
		
		return sum/cnt;
	}
	
	private boolean elicitPredicate(Bid bid)
	{
		if(getTimeLine().getTime() > 0.995)
			return false;
		if(analyser == null)
		{
			System.out.println("No analyser, returning false");
			return false;
		}
		double evoi = EVOI(bid);
		ArrayList<Bid> bidPlan = generateBidPlan(getTimeLine().getTime(), paretoFrontier, analyser);
		double eeu = EEU(bidPlan, utilitySpace);
		if(eeu != 0d && evoi != 0d && verboseElicit)
			System.out.printf("Comparing %f > (%f + %f)\n", evoi, eeu, user.getElicitationCost());
		return evoi > (eeu + user.getElicitationCost());
	}
	
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		//System.out.println("-");
		//displayUtilitySpace(opEstimator.getModel());
		analyser = generateAnalyser(utilitySpace, opEstimator.getModel());
		paretoFrontier = buildParetoFrontier(generateAllBidPoints());

		maxUtil = utilitySpace.getUtility(getMaxUtilityBid());
		double minUtil = utilitySpace.getUtility(getMinUtilityBid());
		double nashUtil = 0;
		BidPoint nashBid = calcNash(analyser);
		
		if (nashBid != null) {
			nashUtil = nashBid.getUtilityA();
		}
				
		double cNashUtil = ((nashUtil - minUtil) * 0.75) + minUtil;
		
		if(showUtilCalcs)
			System.out.println("Nash Util: " + nashUtil);
		
		double time = getTimeLine().getTime();
		TimeDependent td = new TimeDependent(0.4);
		double targetUtil = td.getTargetUtil(maxUtil, cNashUtil, time);
		if (time >= 0.95) {
			targetUtil = td.getTargetUtil(targetUtil, minUtil, (time - 0.95) * 20.0);
		}
                
	    if (time >= 0.995) {
	            targetUtil = 0;
	    }
	
	    if(showUtilCalcs)
			System.out.println("Target Util: " + targetUtil);
		
		if (lastOffer != null)
			if (getUtility(lastOffer) >= targetUtil) 
				return new Accept(getPartyId(), lastOffer);
		
		Bid selectedBid = null;
		if(lastOffer == null || paretoFrontier.size()==0)
			selectedBid = generateRandomBidAboveTarget(targetUtil, 1000,10000);
		else
			selectedBid = generateSubsetBidAboveTarget(targetUtil, paretoFrontier);
		elicitBid(lastOffer);
				
		
		if(showUtilCalcs)
			System.out.printf("Target Util: %f\nSelected Bid Util: %f\n",targetUtil, utilitySpace.getUtility(selectedBid));
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
	private ArrayList<BidPoint> generateAllBidPoints(UtilitySpace us) {
		ArrayList<BidPoint> bidPoints = new ArrayList<BidPoint>();
		for (Bid bid : allPossibleBids) {
			bidPoints.add(new BidPoint(bid, us.getUtility(bid), opEstimator.getModel().getUtility(bid)));
		}
		return bidPoints;
	}
	
	/**
	 * Builds a pareto frontier from a set of all bids
	 */
	private ArrayList<BidPoint> buildParetoFrontier(ArrayList<BidPoint> allBids)
	{
		ParetoFrontier frontier = new ParetoFrontier();
		for (BidPoint b : allBids)
			frontier.mergeIntoFrontier(b);


		if(verboseFrontier)
			System.out.println("Pareto Frontier Size: " + frontier.getFrontier().size());
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
			if(verboseBidGeneration)
				System.out.println("Could not find a non-null bid in the best. Generating random bid");
			return generateRandomBidAboveTarget(target, 1000,10000);
		}
		else if(!found)
		{
			if(verboseBidGeneration)
				System.out.printf("Could not find a bid above target\n Our Best Util: %f\n Opp Util: %f\n", bestUsBid.getUtilityA(), bestUsBid.getUtilityB());
			return bestUsBid.getBid();
		}
		else
		{
			if(verboseBidGeneration)
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
			if(verboseBidGeneration)
				System.out.printf("Could not find a bid above target (%f)\n Our Best Util: %f\n Opp Util: %f\n", target, bestUtil, predictUtil(bestUsBid));
			return bestUsBid;
		}
		else
		{
			if(verboseBidGeneration)
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
		if(consideredElicits.contains(bid))
		{
			if(verboseElicit)
				System.out.println("Bid elicit skipped. Already Checked");
			return 0;
		}
		if(!prefElicit || !hasPreferenceUncertainty())
			return 0;
		//condition to elicit on
		if(elicitPredicate(bid))
		{
			if(!userModel.getBidRanking().getBidOrder().contains(bid))
			{
				AdditiveUtilitySpace oldUS = (AdditiveUtilitySpace)utilitySpace.copy();
					
				userModel = user.elicitRank(bid, userModel);
				UserEstimator.estimateUsingLP((AdditiveUtilitySpace) utilitySpace, userModel.getBidRanking());
				consideredElicits.clear();
				return measureChange(oldUS, (AdditiveUtilitySpace)utilitySpace);
			}
		}
		else
			consideredElicits.add(bid);
				
		if(verboseElicit)
			System.out.println("Change In Model: " + deltaModel);
		return deltaModel;
	}

	// - 9.0 - Recieves opponent offer and tracks the frequency of which each option has been offered.
	@Override
	public void receiveMessage(AgentID sender, Action action) {
		if (action instanceof Offer) {
			lastOffer = ((Offer) action).getBid();
			opEstimator.addNewBid(lastOffer);
			if(hasPreferenceUncertainty())
				if(elicitPredicate(lastOffer))
					deltaModel = elicitBid(lastOffer);
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
