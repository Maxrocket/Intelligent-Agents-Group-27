package group27;

public class OptimalBiddingConcession {
    
	public static double getTargetUtil(double minUtil, double roundsLeft) {
		if (roundsLeft <= 0) {
			return minUtil;
		}
                double nextTargetUtil = getTargetUtil(minUtil, roundsLeft-1);
                nextTargetUtil = 1/2.0 + 1/2.0 * Math.pow(nextTargetUtil, 2);
		return nextTargetUtil;
	}
        
}