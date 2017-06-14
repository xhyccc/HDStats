package xiong.hdstats.da.evaluator;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.uva.libopt.numeric.*;
import smile.math.matrix.Matrix;
import smile.projection.PCA;
import smile.stat.distribution.GLassoMultivariateGaussianDistribution;
import smile.stat.distribution.MultivariateGaussianDistribution;
import xiong.hdstats.Estimator;
import xiong.hdstats.da.Classifier;
import xiong.hdstats.da.LDA;
import xiong.hdstats.da.PseudoInverseLDA;
import xiong.hdstats.da.OnlineLDA;
import xiong.hdstats.da.OptimalLDA;
import xiong.hdstats.da.CovLDA;
import xiong.hdstats.da.comb.RayleighFlowLDA;
import xiong.hdstats.da.comb.StochasticTruncatedRayleighFlowDBSDA;
import xiong.hdstats.da.comb.TruncatedRayleighFlowLDA;
import xiong.hdstats.da.mcmc.BayesLDA;
import xiong.hdstats.da.mcmc.LiklihoodBayesLDA;
import xiong.hdstats.da.mcmc.MCBayesLDA;
import xiong.hdstats.da.mcmc.MCRegularizedBayesLDA;
import xiong.hdstats.da.mcmc.RegularizedBayesLDA;
import xiong.hdstats.da.mcmc.RegularizedLikelihoodBayesLDA;
import xiong.hdstats.da.ml.AdaBoostTreeClassifier;
import xiong.hdstats.da.ml.AdaboostLRClassifier;
import xiong.hdstats.da.ml.DTreeClassifier;
import xiong.hdstats.da.ml.LRClassifier;
import xiong.hdstats.da.ml.NonlinearSVMClassifier;
import xiong.hdstats.da.ml.RandomForestClassifier;
import xiong.hdstats.da.ml.SVMClassifier;
import xiong.hdstats.da.shruken.DBSDA;
import xiong.hdstats.da.shruken.ODaehrLDA;
import xiong.hdstats.da.shruken.SDA;
import xiong.hdstats.da.shruken.ShLDA;
import xiong.hdstats.da.shruken.ShrinkageLDA;
import xiong.hdstats.da.shruken.mDaehrLDA;

public class PsuedoRandomLDACompare {

	public static PrintStream ps = null;

	public static void main(String[] args) throws FileNotFoundException {
		for (int i = 2; i <= 10; i += 2)
			_main(200, 10, 500, 500, 5);
	}

	public static void _main(int p, int nz, int initTrainSize, int testSize, int rate) throws FileNotFoundException {

		ps = new PrintStream("C:/Users/xiongha/Desktop/OnlineLDA/trf-" + p + "-" + nz + "-" + initTrainSize + "-"
				+ ((double) rate / 1.0) + ".txt");
		double[][] cov = new double[p][p];
		double[][] groupMean = new double[2][p];
		double[] mud = new double[p];

		for (int i = 0; i < cov.length; i++) {
			for (int j = 0; j < cov.length; j++) {
				cov[i][j] = Math.pow(0.8, Math.abs(i - j));
			}
		}
		double[] meanPositive = new double[p];
		double[] meanNegative = new double[p];
		for (int i = 0; i < meanPositive.length; i++) {
			if (i < nz) {
				meanPositive[i] = 1.0;
				mud[i] = 1.0;
				groupMean[0][i] = 1.0;
			} else {
				meanPositive[i] = 0.0;
				mud[i] = 0.0;
				groupMean[0][i] = 0.0;
			}
			meanNegative[i] = 0.0;
			groupMean[1][i] = 0.0;
		}

		double[][] theta_s = new Matrix(cov).inverse();
		double[] beta_s = new double[p];
		new Matrix(theta_s).ax(mud, beta_s);

		GLassoMultivariateGaussianDistribution posD = new GLassoMultivariateGaussianDistribution(meanPositive, cov);
		GLassoMultivariateGaussianDistribution negD = new GLassoMultivariateGaussianDistribution(meanNegative, cov);

		for (int r = 0; r < 20; r++) {
			double[][] testData = new double[testSize][p];
			int[] testLabel = new int[testSize];
			for (int i = 0; i < testSize; i++) {
				double[] tdat;
				if (i % 10 < rate) {
					tdat = posD.rand();
					testLabel[i] = 1;
				} else {
					tdat = negD.rand();
					testLabel[i] = -1;
				}
				for (int j = 0; j < cov.length; j++)
					testData[i][j] = tdat[j];
			}

			double[][] trainData = new double[initTrainSize][p];
			int[] trainLabel = new int[initTrainSize];
			for (int i = 0; i < initTrainSize; i++) {
				double[] tdat;
				{
					if (i % 10 < rate) {
						tdat = posD.rand();
						trainLabel[i] = 1;
					} else {
						tdat = negD.rand();
						trainLabel[i] = -1;
					}
					for (int j = 0; j < cov.length; j++)
						trainData[i][j] = tdat[j];
				}
			}
			long start = 0;
			long current = 0;

			start = System.currentTimeMillis();
			OptimalLDA opLDA = new OptimalLDA(beta_s, groupMean, -1.0 * Math.log(rate / (10.0 - rate)));
			current = System.currentTimeMillis();
			accuracy("optimal", testData, testLabel, opLDA, start, current);

			Estimator.lambda = 12;

			start = System.currentTimeMillis();
			DBSDA dbsda = new DBSDA(trainData, trainLabel, false);
			current = System.currentTimeMillis();
			accuracy("DBSDA", testData, testLabel, dbsda, start, current);

			start = System.currentTimeMillis();
			SDA sda = new SDA(trainData, trainLabel, false);
			current = System.currentTimeMillis();
			accuracy("SDA", testData, testLabel, sda, start, current);

			start = System.currentTimeMillis();
			PseudoInverseLDA LDA = new PseudoInverseLDA(trainData, trainLabel, false);
			current = System.currentTimeMillis();
			accuracy("LDA", testData, testLabel, LDA, start, current);

			for (int i = 0; i < 10; i++) {
				start = System.currentTimeMillis();
				TruncatedRayleighFlowLDA olda = new TruncatedRayleighFlowLDA(trainData, trainLabel, false, i * 2 + 2);
				current = System.currentTimeMillis();
				accuracy("TruncatedRayleighFlowLDA-" + (i * 2 + 2), testData, testLabel, olda, start, current);
			}
			
			for (int c = 50; c < 100; c += 10) {
				Estimator.lambda = c * Math.sqrt(Math.log(p) / (double) trainData.length);

				for (int k = 0; k < 10; k++) {
					// for (double i = 0.0001; i < 0.1; i *= 10) {
					start = System.currentTimeMillis();
					StochasticTruncatedRayleighFlowDBSDA olda = new StochasticTruncatedRayleighFlowDBSDA(trainData,
							trainLabel, false, k * 2 + 2, 0);
					current = System.currentTimeMillis();
					accuracy("StochasticTruncatedRayleighFlowDBSDA-" + c + "-" + (k * 2 + 2), testData, testLabel, olda,
							start, current);
					// }
				}
			}

			// for (int i = 0; i < 5; i++) {
			// start = System.currentTimeMillis();
			// TruncatedRayleighFlowDBSDA olda = new
			// TruncatedRayleighFlowDBSDA(trainData, trainLabel, false,
			// i * 2 + 6);
			// current = System.currentTimeMillis();
			// accuracy("TruncatedRayleighFlowDBSDA-" + (i * 2 + 6), testData,
			// testLabel, olda, start, current);
			// }



			start = System.currentTimeMillis();
			RayleighFlowLDA olda = new RayleighFlowLDA(trainData, trainLabel, false);
			current = System.currentTimeMillis();
			accuracy("RayleighFlowLDA", testData, testLabel, olda, start, current);

			// start = System.currentTimeMillis();
			// DBSDA dblda = new DBSDA(trainData, trainLabel, false);
			// current = System.currentTimeMillis();
			// accuracy("DBSDA", testData, testLabel, dblda, start, current);

			// Estimator.lambda = 32.0;

			// System.out.println(Utils.getErrorInf(olda.means[0], new
			// double[1][p]));

		}
	}

	private static void accuracy(String name, double[][] data, int[] labels, Classifier<double[]> classifier, long t1,
			long t2) {
		// int[] plabels=new int[labels.length];
		// System.out.println("accuracy statistics");
		int tp = 0, fp = 0, tn = 0, fn = 0;
		for (int i = 0; i < labels.length; i++) {
			int pl = classifier.predict(data[i]);
			// System.out.println(pl + "\t vs\t" + labels[i]);
			if (pl == 1 && labels[i] == 1) {
				tp++;
			} else if (pl == -1 && labels[i] == -1) {
				tn++;
			} else if (pl == 1 && labels[i] == -1) {
				fp++;
			} else {
				fn++;
			}

		}
		long train_time = t2 - t1;
		double test_time = ((double) (System.currentTimeMillis() - t2)) / ((double) labels.length);
		ps.println(name + "\t" + tp + "\t" + tn + "\t" + fp + "\t" + fn + "\t" + train_time + "\t" + test_time);

	}

	private static void plotAccuracy(double[][] fm, double[][] fm2, HashMap<Integer, Set<Integer>> missingcodes,
			int sum_miss_codes, double[][] fm3, int sum_recover_codes, double threshold) {
		HashMap<Integer, Set<Integer>> recoverycodes = new HashMap<Integer, Set<Integer>>();

		for (int i = 0; i < fm.length; i++) {
			for (int j = 0; j < fm[i].length; j++) {
				if (fm2[i][j] == 0 && fm3[i][j] > threshold) {
					sum_recover_codes++;
					if (!recoverycodes.containsKey(i))
						recoverycodes.put(i, new HashSet<Integer>());
					recoverycodes.get(i).add(j);

				}
			}
		}

		int intersection = intersection(missingcodes, recoverycodes);
		double precision = (double) intersection / (double) sum_recover_codes;
		double recall = (double) intersection / (double) sum_miss_codes;
		double f1 = 2 * precision * recall / (precision + recall);
		double err_l1 = Utils.normalizedErrorL1Recovery(fm, fm2, fm3, threshold);
		double err_l2 = Utils.normalizedErrorL2Recovery(fm, fm2, fm3, threshold);

		ps.print(threshold);
		ps.print("," + sum_miss_codes);
		ps.print("," + sum_recover_codes);
		ps.print("," + intersection);
		ps.print("," + f1);
		ps.print("," + precision);
		ps.print("," + recall);
		ps.print("," + err_l1);
		ps.println("," + err_l2);

	}

	public static double F1(Set<String> miss, Set<String> recover) {
		int intersect = 0;
		for (String i : miss)
			if (recover.contains(i))
				intersect++;
		return 2.0 * (double) intersect / (double) (miss.size() + recover.size());
	}

	public static int intersection(HashMap<Integer, Set<Integer>> miss, HashMap<Integer, Set<Integer>> recover) {
		int intersect = 0;
		for (int p : miss.keySet()) {
			if (recover.containsKey(p)) {
				for (int code : miss.get(p)) {
					if (recover.get(p).contains(code)) {
						intersect++;
					}
				}
			}
		}

		return intersect;
	}

}