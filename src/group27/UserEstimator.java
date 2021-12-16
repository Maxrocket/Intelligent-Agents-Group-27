package group27;

import genius.core.Bid;
import genius.core.issue.Issue;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import gurobi.GRB.IntParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UserEstimator {

    public static void estimateUsingLP(AdditiveUtilitySpace us, BidRanking r) {
        HashMap<Issue, ArrayList<Value>> values = getAllValuesUsedInBids(r.getBidOrder());

        try {
            // Setup Enviornment
            GRBEnv env = new GRBEnv(true);
            //env.set("logFile", "solver.log"); //might be needed idk
            env.set(IntParam.OutputFlag, 0);
            env.start();

            // Create empty model
            GRBModel model = new GRBModel(env);

            // Add variables (utilities)
            HashMap<Issue, HashMap<Value, GRBVar>> vars = new HashMap<>();
            for (Issue issue : values.keySet()) {
                vars.put(issue, new HashMap<>());
                for (Value value : values.get(issue)) {
                    if (!vars.get(issue).containsKey(value)) {
                        GRBVar var = model.addVar(0, 1, 0, GRB.CONTINUOUS, "v" + Math.random());//<<<<<<<<<<<<<<<<<<<<<<<<<check name thing
                        vars.get(issue).put(value, var);
                    }
                }
            }
            // Add variables (epsilons)
            GRBVar[] epsilons = new GRBVar[r.getBidOrder().size() - 1];
            for (int i = 0; i < r.getBidOrder().size() - 1; i++) {
                epsilons[i] = model.addVar(0, 1, 0, GRB.CONTINUOUS, "e" + i);
            }

            // Set objective
            GRBLinExpr objective = new GRBLinExpr();
            for (GRBVar epsilon : epsilons) {
                objective.addTerm(1, epsilon);
            }
            model.setObjective(objective, GRB.MAXIMIZE);

            // Add constraints
            for (int i = r.getBidOrder().size() - 1; i > 0; i--) {
                GRBLinExpr constraint = buildConstraint(r.getBidOrder().get(i), r.getBidOrder().get(i - 1), vars, epsilons[i - 1]);
                model.addConstr(constraint, GRB.GREATER_EQUAL, 0, "c" + i);
            }

            // Solve
            model.optimize();

            // Get Utilities
            for (Issue issue : values.keySet()) {
                EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) us.getEvaluator(issue.getNumber());
                for (Value value : values.get(issue)) {
                    double utility = vars.get(issue).get(value).get(GRB.DoubleAttr.X);
                    if(utility<0)
                    	utility=0;
                    evaluatorDiscrete.setEvaluationDouble((ValueDiscrete) value, utility);
                }
                evaluatorDiscrete.normalizeAll();
                evaluatorDiscrete.scaleAllValuesFrom0To1();
            }
            us.normalizeWeights();

            // Clean up
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }

    private static GRBLinExpr buildConstraint(Bid b0, Bid b1, HashMap<Issue, HashMap<Value, GRBVar>> vars, GRBVar epsilon) {
        GRBLinExpr constraint = new GRBLinExpr();
        // b0 >= b1 + e  ->   b0 - b1 - e >= 0

        //b0
        for (Issue issue : b0.getIssues()) {
            constraint.addTerm(1, vars.get(issue).get(b0.getValue(issue)));
        }
        //-b1
        for (Issue issue : b1.getIssues()) {
            constraint.addTerm(-1, vars.get(issue).get(b1.getValue(issue)));
        }
        //-e0
        constraint.addTerm(-1, epsilon);

        return constraint;
    }

    private static HashMap<Issue, ArrayList<Value>> getAllValuesUsedInBids(List<Bid> bids) {
        HashMap<Issue, ArrayList<Value>> values = new HashMap<>();
        for (Bid b : bids) {
            List<Issue> issues = b.getIssues();
            for (Issue issue : issues) {
                // Add new issue
                if (!values.containsKey(issue)) {
                    values.put(issue, new ArrayList<Value>());
                }
                // Add new value
                if (!values.get(issue).contains(b.getValue(issue))) {
                    values.get(issue).add(b.getValue(issue));
                }
            }
        }
        return values;
    }

}
