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
		HashMap<String, Double>[] optionOrder = (HashMap<String, Double>[]) new HashMap[optionFrequency.length];
		double[] issueWeighting = new double[optionFrequency.length];
		double weightSum = 0.0;

        Map.Entry<String, Integer> maxEntry = null;
        Integer sum = 0;
        Integer Z = 0;

        // Get a max frequency entry from the map (Note that there can be multiple entries with the same max frequency.)
        for (Map.Entry<String, Integer> entry : optionFrequency.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
                sum += entry.getValue();
            } else {
                // Z only includes the values of non-maximal options.
                Z += entry.getValue();
            }
        }

        // Start new hashmap that stores the proportional frequencies of each entry
        HashMap<String, Double> propFreq= (HashMap<String, Double>[]) new HashMap[optionFrequency.length];

        for (Map.Entry<String, Integer> entry : optionFrequency.entrySet()) {
            if (entry.getValue() = maxEntry.getValue()) {
                propFreq.put(entry.getKey(),1);
            } else {
                if (Z == 0) {
                    propFreq.put(entry.getKey(),Z);
                } else {
                    propFreq.put(entry.getKey(),entry.getValue()/Z);
                }
            }
        }

        Double importance = 0d;
        for (Map.Entry<String, Integer> entry : optionFrequency.entrySet()) {
            importance += propFreq.get(entry.getKey()).getValue() * entry.getValue();
        }
        importance = importance / sum;
		
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
