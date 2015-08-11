package edu.uva.hdstats.test;

import edu.uva.hdstats.*;
import edu.uva.libopt.numeric.Utils;

public class HDStatsTest {
	
	public static void main(String[] args){
		double[][] samples=Utils.getSparseRandomMatrix(4000, 100,1);
		System.out.println("************Samples Generated*************");
		Estimator est=new PDLassoEstimator(0.015);
		double[][] hdcovar=est.covariance(samples);
	///	double[][] lacovar=new LassoEstimator(0.001).covariance(samples);
		double[][] ldcovar=new LDEstimator().covariance(samples);
		double error1=0;
		double error2=0;

		double basis=0;
		for(int i=0;i<hdcovar.length;i++){
			for(int j=0;j<hdcovar[i].length;j++){
				error1+=Math.abs(hdcovar[i][j]-ldcovar[i][j]);
		//		error2+=Math.abs(lacovar[i][j]-ldcovar[i][j]);
				basis+=Math.abs(ldcovar[i][j]);
			}
		}
		System.out.println("error:\t"+(error1/basis)+"\t"+(error2/basis));

	}

}
