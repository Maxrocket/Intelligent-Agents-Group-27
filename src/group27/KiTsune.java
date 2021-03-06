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

public class KiTsune extends OpponentEstimator {

	private HashMap<String, Integer>[] optionFrequency;
	
	public KiTsune(AdditiveUtilitySpace space) {
		super(space);

        //Create new frequency map based on current counter-offers recieved
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
		HashMap<String, Double>[] optionMap = (HashMap<String, Double>[]) new HashMap[optionFrequency.length];
		double[] issueWeighting = new double[optionFrequency.length];
		double weightSum = 0.0;

        for (int i = 0; i < optionFrequency.length; i++) {
        	
            Entry<String, Integer> maxEntry = null;
            Integer sum = 0;
            Integer Z = 0;
            Integer maxsum = 0;

            // Get a max frequency entry from the map (Note that there can be multiple entries with the same max frequency.)
            for (Entry<String, Integer> entry : optionFrequency[i].entrySet()) {
                if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                    maxEntry = entry;
                    sum += entry.getValue();
                    Z += maxsum;
                    maxsum = entry.getValue();
                } else if (entry.getValue().compareTo(maxEntry.getValue()) == 0) {
                    sum += entry.getValue();
                	maxsum += maxEntry.getValue();
                } else {
                    // Z only includes the values of non-maximal options.
                    Z += entry.getValue();
                }
            }

            // Start new hashmap that stores the proportional frequencies of each entry
            HashMap<String, Double> propFreq = new HashMap<String, Double>();

            for (Entry<String, Integer> entry : optionFrequency[i].entrySet()) {
                if (entry.getValue() == maxEntry.getValue()) {
                    propFreq.put(entry.getKey(),1.0);
                } else {
                    if (Z == 0) {
                        propFreq.put(entry.getKey(),Z+0.0);
                    } else {
                        propFreq.put(entry.getKey(),entry.getValue()/(Z+0.0));
                    }
                }
            }

            Double importance = 0d;
            for (Entry<String, Integer> entry : optionFrequency[i].entrySet()) {
                importance += propFreq.get(entry.getKey()) * entry.getValue();
            }
            optionMap[i] = propFreq;
            weightSum += importance / sum;
            issueWeighting[i] = importance / sum;
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
		    for (Entry<String, Double> entry : optionMap[issueNumber].entrySet()) {
		    	evaluatorDiscrete.setEvaluationDouble(new ValueDiscrete(entry.getKey()), entry.getValue());
			}
		}
		
	}

}
