package group27;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

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
	private HashMap<String, Integer>[] optionFrequency;
	private HashMap<String, Double>[] optionOrder;
	private double[] nIssueWeighting;

	@SuppressWarnings("unchecked")
	@Override
	public void init(NegotiationInfo info) {
		super.init(info);
		AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
		AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

		List< Issue > issues = additiveUtilitySpace.getDomain().getIssues();

		for (Issue issue : issues) {
		    int issueNumber = issue.getNumber();
		    System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

		    IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
		    EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

		    for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
		        System.out.println(valueDiscrete.getValue());
		        System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
		        try {
		            System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		    }
		}
		
		maxUtil = utilitySpace.getUtility(getMaxUtilityBid());
		minUtil = utilitySpace.getUtility(getMinUtilityBid());
		System.out.println("Max: " + maxUtil + ", Min: " + minUtil);
		threshold = 0.5;
		
		optionFrequency = (HashMap<String, Integer>[]) new HashMap[issues.size()];
		for (int i = 0; i < issues.size(); i++) {
			optionFrequency[i] = new HashMap<String, Integer>();
			IssueDiscrete issueDiscrete = (IssueDiscrete) issues.get(i);
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				optionFrequency[i].put(valueDiscrete.getValue(), 0);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		optionOrder = (HashMap<String, Double>[]) new HashMap[optionFrequency.length];
		double[] issueWeighting = new double[optionFrequency.length];
		double weightSum = 0.0;
		
		for (int i = 0; i < optionFrequency.length; i++) {
			optionOrder[i] = new HashMap<String, Double>();
			ArrayList<Entry<String, Integer>> entrySet = new ArrayList<Entry<String, Integer>>(optionFrequency[i].entrySet());
			double rank = 1.0;
			double totalRanks = 0.0;
			double totalBids = 0.0;
			while (entrySet.size() > 0) {
				int maxValuesValue = entrySet.get(0).getValue();
				ArrayList<Entry<String, Integer>> maxValues = new ArrayList<Entry<String, Integer>>();
				for (Entry<String, Integer> entry : entrySet) {
					if (entry.getValue() > maxValuesValue) {
						maxValuesValue = entry.getValue();
						maxValues = new ArrayList<Entry<String, Integer>>();
						maxValues.add(entry);
					} else if (entry.getValue() == maxValuesValue) {
						maxValues.add(entry);
					}
				}
				for (Entry<String, Integer> entry : maxValues) {
					optionOrder[i].put(entry.getKey(), rank);
					totalBids += (entry.getValue() + 0.0);
					entrySet.remove(entry);
				}
				rank++;
				totalRanks++;
			}
			for (Entry<String, Double> entry : optionOrder[i].entrySet()) {
				optionOrder[i].put(entry.getKey(), (totalRanks - entry.getValue() + 1.0) / totalRanks);
			}
			entrySet = new ArrayList<Entry<String, Integer>>(optionFrequency[i].entrySet());
			for (Entry<String, Integer> entry : entrySet) {
				issueWeighting[i] += Math.pow(((double) entry.getValue()) / totalBids, 2.0);
			}
			weightSum += issueWeighting[i];
		}
				
		nIssueWeighting = new double[issueWeighting.length];
		System.out.print("Issue Weighting: ");
		for (int i = 0; i < nIssueWeighting.length; i++) {
			nIssueWeighting[i] = issueWeighting[i] / weightSum;
			System.out.print(nIssueWeighting[i] + ", ");
		}
		System.out.println("");
		
		System.out.println("Option Ordering: ");
		displayHashMapArray(optionOrder);
		
		
		double time = getTimeLine().getTime();
		double currentConsession = ((1.0 - threshold) * (1.0 - time)) + threshold;
		double targetUtil = ((maxUtil - minUtil) * currentConsession) + minUtil;
		
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
		
		return randomBid;
	}
	
	private Comparator<Bid> bidSort = new Comparator<Bid>() {
		public int compare(Bid arg0, Bid arg1) {
			double diff = predictUtil(arg1) - predictUtil(arg0);
			return diff < 0 ? -1 : diff > 0 ? 1 : 0;
		}
	};
	
	private double predictUtil(Bid bid) {
		double util = 0.0;
		List<Value> values = new ArrayList<Value>(bid.getValues().values());
		for (int i = 0; i < nIssueWeighting.length; i++) {
			util += nIssueWeighting[i] * optionOrder[i].get(values.get(i).toString());
		}		
		return util;
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
	
	private <V> void displayHashMapArray(HashMap<String, V>[] arr) {
		for (int i = 0; i < arr.length; i++) {
			System.out.print("Issue " + i + " - ");
			for (Entry<String, V> entry : arr[i].entrySet()) {
				System.out.print(entry.getKey() + ": " + entry.getValue() + ", ");
			}
			System.out.println("");
		}
		System.out.println("=========");
	}

	@Override
	public void receiveMessage(AgentID sender, Action action) {
		if (action instanceof Offer) 
		{
			lastOffer = ((Offer) action).getBid();
			List<Value> values = new ArrayList<Value>(lastOffer.getValues().values());
			for (int i = 0; i < values.size(); i++) {
				optionFrequency[i].put(values.get(i).toString(), (Integer) (optionFrequency[i].get(values.get(i).toString()) + 1));
			}
			System.out.println("Option Frequency: ");
			displayHashMapArray(optionFrequency);
		}
	}

	@Override
	public String getDescription() {
		return "Places random bids >= " + MINIMUM_TARGET;
	}
	
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() {
		return super.estimateUtilitySpace();
	}

}
