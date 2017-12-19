package xiong.hdstats.gaussian;

import xiong.hdstats.mat.RandSVD;

public class SpikedEstimator extends SampleCovarianceEstimator{

	private int _comp;
	
	public SpikedEstimator(int comp){
		this._comp=comp;
	}
	
	@Override
	public double[][] covariance(double[][] samples) {
		// TODO Auto-generated method stub
		return RandSVD.spikedCovarianceMatrix(samples, _comp, _comp);
	}
	

	@Override
	public double[][] inverseCovariance(double[][] samples) {
		// TODO Auto-generated method stub
		return RandSVD.spikedInverseCovarianceMatrix(samples, _comp, _comp);

	}

}
