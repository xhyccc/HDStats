package xiong.hdstats.opt.sensing;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Jama.Matrix;
import xiong.hdstats.opt.AveragedChainedRiskFunction;
import xiong.hdstats.opt.ChainedFunction;
import xiong.hdstats.opt.GradientDescent;
import xiong.hdstats.opt.estimator.MF.LpMF;
import xiong.hdstats.opt.estimator.MF.MFUtil;
import xiong.hdstats.opt.randgraph.RandomGraph;
import xiong.hdstats.opt.randgraph.RandomGraphChainedRiskFunction;
import xiong.hdstats.opt.var.ChainedMVariables;

public class RandomGraphCrowdSensor {
	public static PrintStream ps;
	public static int cycle = 0;
	public static List<double[]> allData;
	public static HashMap<Integer, RandomGraphCrowdSensor> cmap = new HashMap<Integer, RandomGraphCrowdSensor>();
	public static double[][] estimated;
	public static double[][] truth;

	public int id;
	public HashMap<Integer, Set<Integer>> collected = new HashMap<Integer, Set<Integer>>();
	public double[][] collectedData;
	public double[][] nz;

	public RandomGraphCrowdSensor() {
		this.id = cmap.size();
		cmap.put(id, this);
	}

	public static void creatWorldWithCrowds(String fname, int N) {
		cycle = 0;
		cmap = new HashMap<Integer, RandomGraphCrowdSensor>();
		allData = DataLoader.allSensorData(fname);
		for (int i = 0; i < N; i++)
			new RandomGraphCrowdSensor();
	}

	public static void pseudoLocations(double r, int maxLocations) {
		Set<RandomGraphCrowdSensor> selected = new HashSet<RandomGraphCrowdSensor>();
		while (selected.size() < cmap.size() * r) {
			int index = (int) (Math.random() * cmap.size());
			index = index == cmap.size() ? index - 1 : index;
			selected.add(cmap.get(index));
		}
		for (RandomGraphCrowdSensor cs : selected) {
			int num = (int) Math.random() * maxLocations;
			num = num == 0 ? 1 : num;
			// num=50;
			Set<Integer> ca = new HashSet<Integer>();
			cs.collected.put(cycle, ca);
			while (ca.size() < num) {
				ca.add((int) (Math.random() * allData.size()));
			}
		}
	}

	public static void autoMasking(double maxNoise) {
		double[][] _addData = DataLoader.getAllDataBeforeTime(allData, cycle + 1);
		truth = _addData;
		for (RandomGraphCrowdSensor cs : cmap.values()) {
			cs.collectedData = new double[_addData.length][_addData[0].length];
			cs.nz = new double[_addData.length][_addData[0].length];
			for (int t : cs.collected.keySet()) {
				for (int a : cs.collected.get(t)) {
					cs.collectedData[t][a] = _addData[t][a] * (1 + maxNoise * Math.random());
					cs.nz[t][a] = 1;
				}
			}
		}
	}

	public static void main(String[] args) throws FileNotFoundException {
		ps = new PrintStream("C:\\Users\\xiongha\\Desktop\\pm25Output.txt");
		for (int maxLoc = 1; maxLoc <= 10; maxLoc++)
			for (int crowdSize = 10; crowdSize < 100; crowdSize += 10) {
				for (int par = 2; par < 10; par++) {
					for (int wind = 5; wind <= 50; wind += 5) {
						for (int latent = 2; latent <= 30; latent += 2) {
							creatWorldWithCrowds("C:\\Users\\xiongha\\Desktop\\pm25Leye.csv", crowdSize);
							List<Double> MMSE = new ArrayList<Double>();
							List<Double> TMSE = new ArrayList<Double>();
							for (; cycle < 100; cycle++) {
								pseudoLocations(((double) par) / 10.0, maxLoc);
								autoMasking(0.01);
								if (cycle > wind) {
									estimatingFully(0.001, 0.001, wind, latent);
									recordPerformance("full-connected", MMSE, TMSE);
								}
							}
							double aMSE = 0;
							for (double m : MMSE)
								aMSE += m;
							aMSE /= MMSE.size();
							ps.println("average MSE\t" + latent + "\t" + wind + "\t" + crowdSize + "\t" + par + "\t"
									+ maxLoc + "\t" + aMSE);

							double aTemp = 0;
							for (double t : TMSE)
								aTemp += t / TMSE.size();
							ps.println("average T-err\t" + latent + "\t" + wind + "\t" + crowdSize + "\t" + par + "\t"
									+ maxLoc + "\t" + aTemp);
						}
					}
				}
			}
	}

	private static void recordPerformance(String name, List<Double> MMSE, List<Double> TMSE) {
		double MSE = 0;
		for (int i = 0; i < estimated[estimated.length - 1].length; i++) {
			double err = Math
					.abs(estimated[estimated.length - 1][i] - truth[truth.length - 1][i]);
			// err=err*err;
			MSE += err / estimated[estimated.length - 1].length;
			// System.out.print(estimated[cycle][i]);
		}
		// System.out.println("cycle\t" + cycle +
		// "\t" +
		// MSE);
		MMSE.add(MSE);
		double aTemp = 0;
		for (double t : truth[truth.length - 1])
			aTemp += t / truth[truth.length - 1].length;
		double eTemp = 0;
		for (double t : truth[truth.length - 1])
			eTemp += Math.abs(t - aTemp) / truth[truth.length - 1].length;
		TMSE.add(eTemp);
	}

	public static void estimatingFully(double _lp, double _lq, int wind, int latent) {
		List<ChainedFunction> lcf = new ArrayList<ChainedFunction>();
		for (RandomGraphCrowdSensor cs : cmap.values()) {
			lcf.add(cs.getRiskFunction(_lp, _lq, wind));
		}
		RandomGraphChainedRiskFunction arf = new RandomGraphChainedRiskFunction(lcf,
				RandomGraph.uniformFullyConnected(lcf.size()));

		ChainedMVariables cmv = LpMF.initiNMFPQ(new Matrix(getLatestWindow(cmap.get(0).collectedData, wind)),
				latent);
		ChainedMVariables res = GradientDescent.getMinimum(arf, cmv, 10e-4, 10e-2, 2000, GradientDescent.SGD);
		estimated = LpMF.getP(res).times(LpMF.getQ(res)).getArray();
	}

	public static void estimatingUniformSingleComp(double _lp, double _lq, int wind, int latent, int numEdges) {
		List<ChainedFunction> lcf = new ArrayList<ChainedFunction>();
		for (RandomGraphCrowdSensor cs : cmap.values()) {
			lcf.add(cs.getRiskFunction(_lp, _lq, wind));
		}
		RandomGraphChainedRiskFunction arf = new RandomGraphChainedRiskFunction(lcf,
				RandomGraph.uniformGraph(lcf.size(), numEdges, true));

		ChainedMVariables cmv = LpMF.initiNMFPQ(new Matrix(getLatestWindow(cmap.get(0).collectedData, wind)),
				latent);
		ChainedMVariables res = GradientDescent.getMinimum(arf, cmv, 10e-4, 10e-2, 2000, GradientDescent.SGD);
		estimated = LpMF.getP(res).times(LpMF.getQ(res)).getArray();
	}

	public static void estimatingMaxDegreeSingleComp(double _lp, double _lq, int wind, int latent, int numEdges, int maxDegree) {
		List<ChainedFunction> lcf = new ArrayList<ChainedFunction>();
		for (RandomGraphCrowdSensor cs : cmap.values()) {
			lcf.add(cs.getRiskFunction(_lp, _lq, wind));
		}
		RandomGraphChainedRiskFunction arf = new RandomGraphChainedRiskFunction(lcf,
				RandomGraph.uniformGraphMaxDegree(lcf.size(), numEdges, maxDegree, true));

		ChainedMVariables cmv = LpMF.initiNMFPQ(new Matrix(getLatestWindow(cmap.get(0).collectedData, wind)),
				latent);
		ChainedMVariables res = GradientDescent.getMinimum(arf, cmv, 10e-4, 10e-2, 2000, GradientDescent.SGD);
		estimated = LpMF.getP(res).times(LpMF.getQ(res)).getArray();
	}

	public static void estimatingUniform(double _lp, double _lq, int wind, int latent, int numEdges) {
		List<ChainedFunction> lcf = new ArrayList<ChainedFunction>();
		for (RandomGraphCrowdSensor cs : cmap.values()) {
			lcf.add(cs.getRiskFunction(_lp, _lq, wind));
		}
		RandomGraphChainedRiskFunction arf = new RandomGraphChainedRiskFunction(lcf,
				RandomGraph.uniformGraph(lcf.size(), numEdges, false));

		ChainedMVariables cmv = LpMF.initiNMFPQ(new Matrix(getLatestWindow(cmap.get(0).collectedData, wind)),
				latent);
		ChainedMVariables res = GradientDescent.getMinimum(arf, cmv, 10e-4, 10e-2, 2000, GradientDescent.SGD);
		estimated = LpMF.getP(res).times(LpMF.getQ(res)).getArray();
	}

	public static void estimatingMaxDegree(double _lp, double _lq, int wind, int latent, int numEdges, int maxDegree) {
		List<ChainedFunction> lcf = new ArrayList<ChainedFunction>();
		for (RandomGraphCrowdSensor cs : cmap.values()) {
			lcf.add(cs.getRiskFunction(_lp, _lq, wind));
		}
		RandomGraphChainedRiskFunction arf = new RandomGraphChainedRiskFunction(lcf,
				RandomGraph.uniformGraphMaxDegree(lcf.size(), numEdges, maxDegree, false));

		ChainedMVariables cmv = LpMF.initiNMFPQ(new Matrix(getLatestWindow(cmap.get(0).collectedData, wind)),
				latent);
		ChainedMVariables res = GradientDescent.getMinimum(arf, cmv, 10e-4, 10e-2, 2000, GradientDescent.SGD);
		estimated = LpMF.getP(res).times(LpMF.getQ(res)).getArray();
	}
	
	public static double[][] getLatestWindow(double[][] collectedData, int wind) {
		double[][] data = new double[wind][collectedData[0].length];
		int index = 0;
		for (int i = collectedData.length - wind; i < collectedData.length; i++) {
			for (int j = 0; j < collectedData[i].length; j++) {
				data[index][j] = collectedData[i][j];
			}
			i++;
		}
		return data;
	}

	public ChainedFunction getRiskFunction(double _lp, double _lq, int wind) {
		return LpMF.getNMFRiskFunction(new Matrix(getLatestWindow(this.collectedData, wind)),
				MFUtil.nmf, MFUtil.L2, null, _lp, _lq);
	}

}
