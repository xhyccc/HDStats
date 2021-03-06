package xiong.hdstats.graph.ens;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import gov.sandia.cognition.math.RingAccumulator;
import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.MatrixFactory;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;
import gov.sandia.cognition.math.matrix.mtj.DenseMatrixFactoryMTJ;
import gov.sandia.cognition.math.matrix.mtj.decomposition.CholeskyDecompositionMTJ;
import gov.sandia.cognition.statistics.distribution.MultivariateGaussian;
import xiong.hdstats.gaussian.NearPD;
import xiong.hdstats.gaussian.SampleCovarianceEstimator;
import xiong.hdstats.graph.SampleGraph;

public class SampleWishartGraph extends SampleCovarianceEstimator {

	public double[][] wishartMeanPrecision;
	public double[] mean;
	public List<SampleGraph> sampledGraphs = new ArrayList<SampleGraph>();
	public int size;
	// private NonSparseEstimator ne = new NonSparseEstimator();

	public SampleWishartGraph(double[][] data, int size) {
		this.size = size;
		this.covariance(data);
		this.mean = this.getMean(data);

		for (int i = 0; i < this.wishartMeanPrecision.length; i++)
			this.wishartMeanPrecision[i][i] += 1;

		VectorFactory vf = VectorFactory.getDenseDefault();
		MatrixFactory mf = MatrixFactory.getDenseDefault();
		Vector meanV = vf.copyArray(mean);
		Matrix covarianceSqrt = null;
		try {
			covarianceSqrt = CholeskyDecompositionMTJ
					.create(DenseMatrixFactoryMTJ.INSTANCE.copyMatrix(mf.copyArray(wishartMeanPrecision).inverse()))
					.getR();
		} catch (Exception exp) {
			NearPD npd = new NearPD();
			npd.calcNearPD(new Jama.Matrix(this.wishartMeanPrecision));
			this.wishartMeanPrecision = npd.getX().getArray();
			covarianceSqrt = CholeskyDecompositionMTJ
					.create(DenseMatrixFactoryMTJ.INSTANCE.copyMatrix(mf.copyArray(wishartMeanPrecision).inverse()))
					.getR();
		}
		int fDOF = Math.min(Math.max(this.wishartMeanPrecision.length, data.length) * 10, 2000);
		Random r = new Random(System.currentTimeMillis());
		while (sampledGraphs.size() < this.size) {
			Matrix mtx = sample(r, meanV, covarianceSqrt, fDOF);
			sampledGraphs.add(new SampleGraph(mtx.inverse().toArray(), false));
		}
	}

	@Override
	public double[][] covariance(double[][] samples) {
		double[][] covar = super.covariance(samples);
		this.wishartMeanPrecision = inverse(covar);

		// this.wishartMeanPrecision=new NearPD().;
		return covar;
		// this.wishartMeanPrecision =
		// ne._deSparsifiedGlassoPrecisionMatrix(super.covariance(samples));
		// return inverse(this.wishartMeanPrecision);
	}

	public int[][] thresholding(double t, int e, double lbd) {
		List<int[][]> tGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			tGraphs.add(sampledGraphs.get(i).thresholding(t));
		return this.submodularSearch(tGraphs, e, lbd);
	}

	public int[][] adaptiveThresholding(double t, int e, double lbd) {
		List<int[][]> tGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			tGraphs.add(sampledGraphs.get(i).adaptiveThresholding(t));
		return this.submodularSearch(tGraphs, e, lbd);
	}

	public int[][] thresholdingDiff(double t, double e, SampleWishartGraph wg) {
		List<int[][]> tGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			tGraphs.add(sampledGraphs.get(i).thresholding(t));
		List<int[][]> wGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			wGraphs.add(wg.sampledGraphs.get(i).thresholding(t));

		return this.submodularSubmodularSearch(tGraphs, wGraphs, e);
	}

	public int[][] adaptiveThresholdingDiff(double t, double overlap, SampleWishartGraph wg) {
		List<int[][]> tGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			tGraphs.add(sampledGraphs.get(i).adaptiveThresholding(t));
		List<int[][]> wGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			wGraphs.add(wg.sampledGraphs.get(i).adaptiveThresholding(t));

		return this.submodularSubmodularSearch(tGraphs, wGraphs, overlap);
	}

	public static boolean graphCompare(int[][] graph1, int[][] graph2) {
		for (int i = 0; i < graph1.length; i++) {
			for (int j = 0; j < graph2.length; j++) {
				if (graph1[i][j] != graph2[i][j])
					return false;
			}
		}
		return true;
	}

	public int[][] submodularSearch(List<int[][]> graphs, int e, double lbd) {
		int[][] sGraph = new int[this.wishartMeanPrecision.length][this.wishartMeanPrecision.length];
		Set<Integer> sNodes = new HashSet<Integer>();
		int selected = 0;
		while (selected < e) {
			int i_index = -1, j_index = -1;
			double maxV = -1;
			for (int i = 0; i < this.wishartMeanPrecision.length; i++) {
				for (int j = 0; j < this.wishartMeanPrecision.length; j++) {
					if (sGraph[i][j] == 0) {
						double v = 0;
						if (sNodes.contains(i))
							v += lbd;
						if (sNodes.contains(j))
							v += lbd;
						for (int[][] graph : graphs) {
							if (graph[i][j] != 0)
								v++;
						}
						if (v > maxV) {
							maxV = v;
							i_index = i;
							j_index = j;
						}
					}
				}
			}
			if (maxV <= 0)
				return sGraph;
			sGraph[i_index][j_index] = 1;
			sNodes.add(i_index);
			sNodes.add(j_index);
			selected++;
		}
		return sGraph;
	}

	public int[][] adaptiveThresholdingDiff(double t, double overlap) {
		List<int[][]> tGraphs = new ArrayList<int[][]>();
		for (int i = 0; i < this.size; i++)
			tGraphs.add(sampledGraphs.get(i).adaptiveThresholding(t));

		return this.$_submodularSubmodularSearch(tGraphs, overlap);
	}

	public int[][] $_submodularSubmodularSearch(List<int[][]> graphs, double overlap) {
		int[][] sGraph = new int[this.wishartMeanPrecision.length][this.wishartMeanPrecision.length];
		double error = 0;
		while (error < overlap) {
			int i_index = -1, j_index = -1;
			double maxV = -1;
			double ecost = 0;
			for (int i = 0; i < this.wishartMeanPrecision.length; i++) {
				for (int j = 0; j < this.wishartMeanPrecision.length; j++) {
					if (sGraph[i][j] == 0) {
						double v = 0;
						double u = 0;

						for (int[][] graph : graphs) {
							if (graph[i][j] != 0)
								v++;
							else
								u++;
						}

						if (u != 0 && v / u > maxV) {
							maxV = v / u;
							i_index = i;
							j_index = j;
							ecost = u / graphs.size();
						} else if (u == 0 && v != 0) {
							maxV = Double.POSITIVE_INFINITY;
							i_index = i;
							j_index = j;
							ecost = u / graphs.size();
						}
					}
				}
			}
			if (maxV <= 0)
				return sGraph;
			sGraph[i_index][j_index] = 1;
			error += ecost;
			System.out.println(ecost);
		}
		return sGraph;
	}

	public int[][] submodularSubmodularSearch(List<int[][]> graphs, List<int[][]> wgraphs, double overlap) {
		int[][] sGraph = new int[this.wishartMeanPrecision.length][this.wishartMeanPrecision.length];
		double error = 0;
		while (error < overlap * size) {
			int i_index = -1, j_index = -1;
			double maxV = -1;
			double ecost = 0;
			for (int i = 0; i < this.wishartMeanPrecision.length; i++) {
				for (int j = 0; j < this.wishartMeanPrecision.length; j++) {
					if (sGraph[i][j] == 0) {
						double v = 0;
						for (int[][] graph : graphs) {
							if (graph[i][j] != 0)
								v++;
						}
						double u = 0;
						for (int[][] graph : wgraphs) {
							if (graph[i][j] != 0)
								u++;
						}

						if (v / u > maxV) {
							maxV = v / u;
							i_index = i;
							j_index = j;
							ecost = u;
						}
					}
				}
			}
			if (maxV <= 0)
				return sGraph;
			sGraph[i_index][j_index] = 1;
			error += ecost;
		}
		return sGraph;
	}

	public static Matrix sample(final Random random, final Vector mean, final Matrix covarianceSqrt,
			final int degreesOfFreedom) {
		ArrayList<Vector> xs = MultivariateGaussian.sample(mean, covarianceSqrt, random, degreesOfFreedom);
		RingAccumulator<Matrix> sum = new RingAccumulator<Matrix>();
		for (Vector x : xs) {
			sum.accumulate(x.outerProduct(x));
		}
		return sum.getSum();
	}

}
