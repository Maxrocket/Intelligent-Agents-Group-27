package group27;

public class Linear extends ConcessionFunction {

	public double getTargetUtil(double maxUtil, double minUtil, double time) {
		return ((maxUtil - minUtil) * (1.0 - time)) + minUtil;
	}

}
