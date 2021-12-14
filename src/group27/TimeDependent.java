package group27;

public class TimeDependent {

	private double beta;
	
	public TimeDependent(double beta) {
		this.beta = beta;
	}
	
	public double getTargetUtil(double maxUtil, double minUtil, double time) {
		return ((maxUtil - minUtil) * (1.0 - f(time))) + minUtil;
	}
	
	public double f(double t) {
		return Math.pow(t, (1.0 / beta));
	}

}
