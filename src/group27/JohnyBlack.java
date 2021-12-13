package group27;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import genius.core.Bid;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;

public class JohnyBlack extends OpponentEstimator {

	private HashMap<String, Integer>[] optionFrequency;
	
	public JohnyBlack(AdditiveUtilitySpace space) {
		super(space);

		List<Issue> issues = opUtilSpace.getDomain().getIssues();
		optionFrequency = (HashMap<String, Integer>[]) new HashMap[issues.size()];
		for (int i = 0; i < issues.size(); i++) {
			optionFrequency[i] = new HashMap<String, Integer>();
			IssueDiscrete issueDiscrete = (IssueDiscrete) issues.get(i);
			for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
				optionFrequency[i].put(valueDiscrete.getValue(), 0);
			}
		}
		updateModel();
	}

	@Override
	public void newBid(Bid bid) {
		List<Value> values = new ArrayList<Value>(bid.getValues().values());
		for (int i = 0; i < values.size(); i++) {
			optionFrequency[i].put(values.get(i).toString(), (Integer) (optionFrequency[i].get(values.get(i).toString()) + 1));
		}
	}

	@Override
	public void updateModel() {
		HashMap<String, Double>[] optionOrder = (HashMap<String, Double>[]) new HashMap[optionFrequency.length];
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
		
		double[] nIssueWeighting = new double[issueWeighting.length];
		for (int i = 0; i < nIssueWeighting.length; i++) {
			nIssueWeighting[i] = issueWeighting[i] / weightSum;
		}
		
		List<Issue> issues = opUtilSpace.getDomain().getIssues();
		for (Issue issue : issues) {
		    int issueNumber = issue.getNumber() - 1;
		    
		    EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) opUtilSpace.getEvaluator(issueNumber + 1);
		    
		    evaluatorDiscrete.setWeight(nIssueWeighting[issueNumber]);
		    for (Entry<String, Double> entry : optionOrder[issueNumber].entrySet()) {
		    	evaluatorDiscrete.setEvaluationDouble(new ValueDiscrete(entry.getKey()), entry.getValue());
			}
		}
		
	}

}
